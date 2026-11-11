package tw.hoonsor.weather.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.Broadcast
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.OfficeCalendar
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.PlacePicker
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.domain.TyphoonImpact
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 會被唸出來的錯誤：code 對應 Agent API 錯誤碼（PERMISSION／NOT_FOUND／INTERNAL） */
class AppError(val code: String, message: String) : Exception(message)

data class TyphoonState(val list: List<Typhoon>, val updatedAt: LocalDateTime?, val available: Boolean)

/** 一段報告：summary 直接唸，data 給 AI 回答追問 */
data class Report(val summary: String, val data: JSONObject)

/**
 * 唯一的資料入口（SOP：Repository 唯一寫入入口）。小工具、主畫面、Agent API 都經過這裡。
 * 快取放 filesDir/cache（不進備份）；連不上時回最後一次資料，並在 summary 註明時間。
 */
class Repository(
    private val context: Context,
    val settings: Settings,
    val vault: Vault,
    private val sources: Sources = Sources(),
    val locator: Locator = Locator(context),
) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val calDir = File(context.filesDir, "calendar").apply { mkdirs() }
    private val lock = Mutex()

    // ---------------- 地點 ----------------

    suspend fun resolveTown(kind: PlaceKind): Town = when (kind) {
        PlaceKind.HOME -> settings.place(PlaceKind.HOME) ?: throw AppError("NOT_FOUND", "還沒設定家的位置，到天氣 APP 的設定填好，或說「把家設成現在這裡」。")
        PlaceKind.SCHOOL -> settings.place(PlaceKind.SCHOOL) ?: throw AppError("NOT_FOUND", "還沒設定學校的位置，要不要把現在這裡設成學校？")
        PlaceKind.CURRENT -> currentTown()
    }

    private suspend fun currentTown(): Town {
        if (locator.hasPermission()) {
            val loc = locator.location()
            if (loc != null) {
                val t = locator.townOf(loc.latitude, loc.longitude)
                    ?: Town("", "目前位置", Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0)
                settings.setPlace(PlaceKind.CURRENT, t)
                return t
            }
        }
        settings.place(PlaceKind.CURRENT)?.let { return it }
        settings.place(PlaceKind.HOME)?.let { return it }
        if (!locator.hasPermission()) throw AppError("PERMISSION", "天氣 APP 沒有定位權限，打開 APP 允許定位後再試一次。")
        throw AppError("INTERNAL", "現在抓不到位置，等一下再試，或先在設定填家和學校。")
    }

    /** auto：明天上班 → 學校；否則 → 家；都沒設定 → 目前位置（F07） */
    suspend fun autoKind(date: LocalDate = LocalDate.now().plusDays(1)): PlaceKind =
        PlacePicker.auto(calendar().isWorkday(date), settings.place(PlaceKind.HOME) != null, settings.place(PlaceKind.SCHOOL) != null)

    /** 設定地點時比對鄉鎮：有授權碼用氣象署的鄉鎮表，沒有就用 Geocoder */
    suspend fun searchTown(query: String): List<Town> {
        val (county, town) = Names.split(query)
        val key = vault.get(Vault.CWA)
        if (key != null) {
            val counties = if (county != null) listOf(county) else Names.counties
            val found = ArrayList<Town>()
            for (c in counties) {
                val towns = cachedText("towns_$c", 60L * 24 * 30) { sources.cwaCountyTowns(key, c) }?.let { runCatching { CwaParse.towns(it) }.getOrNull() } ?: continue
                found += towns.filter { it.name == town || (town.length >= 2 && it.name.startsWith(town)) }
                if (county == null && found.size > 3) break
            }
            if (found.isNotEmpty()) return found.distinctBy { it.full }
        }
        if (county == null) return emptyList()
        return listOfNotNull(locator.geocode(county, town))
    }

    // ---------------- 天氣快照 ----------------

    private fun file(kind: PlaceKind) = File(dir, "snap_${kind.key}.json")

    fun cached(kind: PlaceKind): Snapshot? = runCatching { SnapshotJson.read(JSONObject(file(kind).readText())) }.getOrNull()

    suspend fun snapshot(kind: PlaceKind, maxAgeMin: Long = 30, force: Boolean = false): Snapshot = lock.withLock {
        val old = cached(kind)
        if (!force && old != null && old.ageMinutes() < maxAgeMin && sameTown(kind, old)) return old
        try {
            val s = fetch(kind)
            file(kind).writeText(SnapshotJson.write(s).toString())
            settings.lastError = null
            settings.lastRefresh = LocalDateTime.now().toString()
            s
        } catch (e: AppError) {
            settings.lastError = e.message
            if (old != null && e.code == "INTERNAL") old else throw e
        } catch (e: Exception) {
            settings.lastError = e.message
            old ?: throw AppError("INTERNAL", "氣象資料現在抓不到，可能是網路不穩，等一下再問我。")
        }
    }

    /** 家或學校改過地點，舊快取就不能用 */
    private fun sameTown(kind: PlaceKind, s: Snapshot) = kind == PlaceKind.CURRENT || settings.place(kind)?.full == s.town.full

    private suspend fun fetch(kind: PlaceKind): Snapshot {
        var town = resolveTown(kind)
        val key = vault.get(Vault.CWA)
        var source = "open-meteo"
        var hours = emptyList<tw.hoonsor.weather.domain.HourPoint>()
        var days = emptyList<tw.hoonsor.weather.domain.DayForecast>()
        if (key != null && town.county in CwaParse.datasets) {
            runCatching {
                val r = CwaParse.parse(sources.cwaForecast(key, town.county, town.name), town.name)
                if (r.hours.isNotEmpty() && r.days.isNotEmpty()) {
                    hours = r.hours; days = r.days; source = "cwa"
                    r.town?.let { t -> if (kind != PlaceKind.CURRENT) town = t }
                }
            }.onFailure { settings.lastError = it.message }
        }
        if (source != "cwa") {
            val (h, d) = OpenMeteoParse.parse(sources.openMeteo(town.lat, town.lon))
            hours = h; days = d
        }
        if (kind != PlaceKind.CURRENT && settings.place(kind)?.let { it.lat != town.lat || it.lon != town.lon } == true) settings.setPlace(kind, town)
        val (airNow, airTomorrow) = air(town)
        return Snapshot(kind, town, LocalDateTime.now(), source, hours, days, airNow, airTomorrow)
    }

    private suspend fun air(town: Town): Pair<AirReading?, AirReading?> {
        val key = vault.get(Vault.MOENV) ?: return null to null
        val now = runCatching { cachedText("aqi_now", 30) { sources.moenvStations(key) }?.let { MoenvParse.nearest(MoenvParse.stations(it), town.lat, town.lon) } }.getOrNull()
        val county = town.county.ifBlank { "臺北市" }
        val tom = runCatching { cachedText("aqi_fc", 180) { sources.moenvForecast(key) }?.let { MoenvParse.forecast(it, county, LocalDate.now().plusDays(1)) } }.getOrNull()
        return now to tom
    }

    /** 文字快取：過期才連網；連不上回舊檔 */
    private suspend fun cachedText(name: String, maxAgeMin: Long, fetch: suspend () -> String): String? {
        val f = File(dir, "$name.txt")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMin * 60_000
        if (fresh) return f.readText()
        return runCatching { fetch().also { f.writeText(it) } }.getOrElse { if (f.exists()) f.readText() else throw it }
    }

    // ---------------- 辦公日曆（F08） ----------------

    private var calendarCache: OfficeCalendar? = null

    suspend fun calendar(): OfficeCalendar {
        val base = calendarCache ?: loadCalendar().also { calendarCache = it }
        val needYear = LocalDate.now().plusDays(1).year
        val stale = calDir.listFiles().orEmpty().maxOfOrNull { it.lastModified() }?.let { System.currentTimeMillis() - it > 30L * 86_400_000 } ?: true
        if (needYear !in base.years || stale) {
            withTimeoutOrNull(10_000) { runCatching { refreshCalendar() } }
            calendarCache = loadCalendar()
        }
        return (calendarCache ?: base).withOverrides(settings.calendarOverrides)
    }

    private fun loadCalendar(): OfficeCalendar =
        OfficeCalendar.merge(*calDir.listFiles().orEmpty().sortedBy { it.name }.map { OfficeCalendar.parse(it.readText()) }.toTypedArray())

    /** 抓今年與明年的 CSV（含修正檔），檔名照清單順序，後面的覆蓋前面的 */
    suspend fun refreshCalendar(): Int {
        val y = LocalDate.now().year - 1911
        val urls = CalendarIndexParse.csvUrls(sources.calendarIndex(), setOf(y, y + 1))
        var n = 0
        urls.forEachIndexed { i, u ->
            runCatching { sources.download("辦公日曆", u) }.onSuccess { text ->
                if (OfficeCalendar.parse(text).years.isNotEmpty()) { File(calDir, "%02d.csv".format(i)).writeText(text); n++ }
            }
        }
        calendarCache = null
        return n
    }

    fun calendarInfo(): String {
        val c = loadCalendar()
        val ys = c.years.sorted().joinToString("、") { "${it - 1911} 年" }
        return if (ys.isEmpty()) "還沒下載辦公日曆，暫時用「週一到週五上班」判斷" else "已有 $ys 辦公日曆"
    }

    // ---------------- 颱風（F10～F13） ----------------

    suspend fun typhoons(maxAgeMin: Long = 30): TyphoonState {
        val key = vault.get(Vault.CWA) ?: return TyphoonState(emptyList(), null, available = false)
        val f = File(dir, "typhoon.json")
        val old = runCatching { JSONObject(f.readText()) }.getOrNull()
        val oldAt = old?.optString("at")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (old != null && oldAt != null && java.time.Duration.between(oldAt, LocalDateTime.now()).toMinutes() < maxAgeMin) return readTyphoon(old)
        return try {
            val raws = TyphoonParse.parse(sources.cwaTyphoonTrack(key)).filter(TyphoonParse::relevant)
            val (warning, land) = if (raws.isEmpty()) Typhoon.Warning.NONE to emptySet() else runCatching { TyphoonParse.warning(sources.cwaTyphoonWarning(key)) }.getOrDefault(Typhoon.Warning.NONE to emptySet())
            val list = raws.map { Typhoon(it.name, TyphoonParse.kind(it.maxWind), TyphoonParse.describe(it.lat, it.lon), it.track, warning, land) }
            val o = JSONObject().put("at", LocalDateTime.now().toString()).put("list", JSONArray(list.map(SnapshotJson::typhoon)))
            f.writeText(o.toString())
            readTyphoon(o)
        } catch (e: Exception) {
            old?.let(::readTyphoon) ?: TyphoonState(emptyList(), null, available = false)
        }
    }

    private fun readTyphoon(o: JSONObject) = TyphoonState(
        J.objs(o.optJSONArray("list")).map(SnapshotJson::typhoon), runCatching { LocalDateTime.parse(o.getString("at")) }.getOrNull(), available = true,
    )

    /**
     * 停班停課：縣市 → 公告文字，來源人事總處 nds.html。
     * NCDR Atom 原本是主來源，2026-11-11 核對時要「先登入會員」才拿得到，改成不用（解析器保留）。
     */
    suspend fun offWork(): Map<String, String> =
        runCatching { cachedText("offwork_dgpa", 20) { sources.dgpaOffWork() }?.let(OffWorkParse::dgpa) }.getOrNull().orEmpty()

    /** 家、學校（或目前位置）的颱風影響；明天上班時學校排前面 */
    suspend fun impactPlaces(t: Typhoon): List<Triple<String, Town, TyphoonImpact.Result>> {
        val list = ArrayList<Pair<String, Town>>()
        val school = settings.place(PlaceKind.SCHOOL)
        val home = settings.place(PlaceKind.HOME)
        val workday = runCatching { calendar().isWorkday(LocalDate.now().plusDays(1)) }.getOrDefault(true)
        if (workday) { school?.let { list += "學校" to it }; home?.let { list += "家裡" to it } } else { home?.let { list += "家裡" to it }; school?.let { list += "學校" to it } }
        if (list.isEmpty()) runCatching { resolveTown(PlaceKind.CURRENT) }.getOrNull()?.let { list += "這裡" to it }
        return list.map { (label, town) -> Triple(label, town, TyphoonImpact.assess(t, town)) }
    }

    suspend fun typhoonText(t: Typhoon, short: Boolean): String {
        val places = impactPlaces(t)
        val off = runCatching { offWork() }.getOrDefault(emptyMap())
        val counties = places.map { it.second.county }.filter { it.isNotBlank() }.distinct()
        val offText = if (t.warning == Typhoon.Warning.SEA_LAND || off.keys.any { it in counties }) {
            val hits = counties.mapNotNull { c -> off[c]?.let { "$c：${it.take(60)}" } }
            if (hits.isEmpty()) "停班停課還沒公布" else "人事總處公告，" + hits.joinToString("；")
        } else null
        val closest = TyphoonImpact.closestToTaiwan(t)?.first?.time
        return Broadcast.typhoon(t, closest, LocalDateTime.now(), places.map { it.first to it.third.level }, offText, short)
    }

    // ---------------- 報告（Agent API、主畫面、播報共用） ----------------

    private val hm = DateTimeFormatter.ofPattern("H:mm")
    private fun staleNote(s: Snapshot) = if (s.ageMinutes() > 90) "（這是 ${s.updatedAt.format(hm)} 的資料）" else ""

    suspend fun kindOf(place: String?, default: PlaceKind = PlaceKind.CURRENT): PlaceKind = when (place) {
        null, "" -> default
        "auto" -> autoKind()
        else -> PlaceKind.of(place) ?: throw AppError("INVALID_PARAMS", "地點只能是目前位置、家或學校。")
    }

    suspend fun tomorrowReport(place: String?, includeAir: Boolean = settings.broadcastAir, includeTyphoon: Boolean = settings.broadcastTyphoon): Report {
        val date = LocalDate.now().plusDays(1)
        val kind = kindOf(place ?: "auto")
        val s = snapshot(kind)
        val d = s.day(date) ?: throw AppError("INTERNAL", "還沒有明天的預報，等一下再問我。")
        val air = s.airTomorrow
        val advice = ClothingRules.advise(d, air?.aqi, settings.shift)
        val sb = StringBuilder(Broadcast.tomorrow(d, kind, s.town, advice))
        var airJson: JSONObject? = null
        if (air != null) {
            airJson = JSONObject().put("aqi", air.aqi).put("level", Aqi.level(air.aqi).name.lowercase()).put("area", air.area)
            if (includeAir) Broadcast.airAlert(air, tomorrow = true)?.let { sb.append(it) }
        }
        var tyText: String? = null
        if (includeTyphoon) {
            val ty = runCatching { typhoons() }.getOrNull()?.list?.firstOrNull()
            if (ty != null) { tyText = typhoonText(ty, short = true); sb.append(tyText) }
        }
        sb.append(staleNote(s))
        val workday = calendar().isWorkday(date)
        val data = JSONObject().put("date", date.toString()).put("placeKind", kind.key).put("town", s.town.full).put("isWorkday", workday)
            .put("weather", d.weather).put("feelsMin", d.feelsMin).put("feelsMax", d.feelsMax).put("tempMin", d.tempMin).put("tempMax", d.tempMax)
            .put("rainProbDay", d.rainDay ?: JSONObject.NULL).put("rainProbNight", d.rainNight ?: JSONObject.NULL)
            .put("uvIndex", d.uvIndex ?: JSONObject.NULL).put("beaufortMax", d.beaufortMax ?: JSONObject.NULL)
            .put("clothing", JSONArray(advice.items.map { it.label })).put("air", airJson ?: JSONObject.NULL)
            .put("typhoon", tyText ?: JSONObject.NULL).put("source", s.source).put("updatedAt", s.updatedAt.toString())
        return Report(sb.toString(), data)
    }

    suspend fun nowReport(place: String?): Report {
        val kind = kindOf(place)
        val s = snapshot(kind)
        val n = s.now() ?: throw AppError("INTERNAL", "還沒有現在的天氣資料，等一下再問我。")
        val next = s.hours.filter { it.time.isAfter(LocalDateTime.now()) }.take(2).mapNotNull { it.rain }.maxOrNull() ?: n.rainNext3h
        val rain = next?.let { "，接下來 3 小時降雨機率 $it%" } ?: ""
        val summary = "${s.town.name}現在 ${n.temp} 度，體感 ${n.feels} 度，${n.weather.ifBlank { "天氣資料不完整" }}$rain。${staleNote(s)}"
        return Report(summary, JSONObject().put("town", s.town.full).put("temp", n.temp).put("feels", n.feels).put("weather", n.weather)
            .put("humidity", n.humidity ?: JSONObject.NULL).put("rainNext3h", next ?: JSONObject.NULL).put("source", s.source).put("updatedAt", s.updatedAt.toString()))
    }

    suspend fun weekReport(place: String?): Report {
        val kind = kindOf(place)
        val s = snapshot(kind)
        val days = s.days.filter { !it.date.isBefore(LocalDate.now()) }.take(7)
        if (days.isEmpty()) throw AppError("INTERNAL", "還沒有一週預報，等一下再問我。")
        val lo = days.minOf { it.tempMin }; val hi = days.maxOf { it.tempMax }
        val rainy = days.filter { maxOf(it.rainDay ?: 0, it.rainNight ?: 0) >= 50 }.map { Broadcast.weekdayName(it.date) }
        val rainText = if (rainy.isEmpty()) "這幾天降雨機率都不高" else rainy.joinToString("、") + "降雨機率比較高"
        val coldest = days.minBy { it.tempMin }
        val summary = "未來 ${days.size} 天${s.town.name} $lo 到 $hi 度，$rainText；最冷是${Broadcast.weekdayName(coldest.date)}，低溫 ${coldest.tempMin} 度。${staleNote(s)}"
        return Report(summary, JSONObject().put("town", s.town.full).put("days", JSONArray(days.map(SnapshotJson::day))))
    }

    suspend fun airReport(place: String?, day: String?): Report {
        if (!vault.has(Vault.MOENV)) throw AppError("PERMISSION", "還沒設定環境部金鑰，到天氣 APP 的設定填好就能查空氣品質。")
        val kind = kindOf(place)
        val s = snapshot(kind)
        val tomorrow = day == "tomorrow"
        val a = (if (tomorrow) s.airTomorrow else s.airNow) ?: throw AppError("INTERNAL", "空氣品質資料現在抓不到，等一下再問我。")
        val lv = Aqi.level(a.aqi)
        return Report(Broadcast.air(a), JSONObject().put("aqi", a.aqi).put("level", lv.name.lowercase()).put("color", lv.color)
            .put("pm25", a.pm25 ?: JSONObject.NULL).put("area", a.area).put("forecast", a.isForecast))
    }

    suspend fun typhoonReport(): Report {
        val st = typhoons()
        if (!st.available) throw AppError("PERMISSION", "還沒設定氣象署授權碼，到天氣 APP 的設定填好就能查颱風。")
        val t = st.list.firstOrNull() ?: return Report(Broadcast.noTyphoon(), JSONObject().put("active", false))
        val places = impactPlaces(t)
        val data = JSONObject().put("active", true).put("name", t.name).put("kind", t.kind).put("position", t.position).put("warning", t.warning.name.lowercase())
            .put("closest", TyphoonImpact.closestToTaiwan(t)?.first?.time?.toString() ?: JSONObject.NULL)
            .put("places", JSONArray(places.map { (label, town, r) -> JSONObject().put("label", label).put("town", town.full).put("impact", r.level.label).put("minDistanceKm", r.minDistanceKm.toInt()) }))
        return Report(typhoonText(t, short = false), data)
    }

    fun placeListReport(): Report {
        val home = settings.place(PlaceKind.HOME); val school = settings.place(PlaceKind.SCHOOL); val cur = settings.place(PlaceKind.CURRENT)
        val parts = listOf("家" to home, "學校" to school).map { (k, t) -> if (t == null) "${k}還沒設定" else "${k}在${t.full}" } +
            listOfNotNull(cur?.let { "目前位置判斷在${it.full}" })
        return Report(parts.joinToString("，") + "。", JSONObject().put("home", home?.full ?: JSONObject.NULL).put("school", school?.full ?: JSONObject.NULL).put("current", cur?.full ?: JSONObject.NULL))
    }

    /** 清掉某地點的快取（改地點後） */
    fun invalidate(kind: PlaceKind) { file(kind).delete() }
}
