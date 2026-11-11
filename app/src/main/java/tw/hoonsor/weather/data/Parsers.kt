package tw.hoonsor.weather.data

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.HourPoint
import tw.hoonsor.weather.domain.TrackPoint
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.domain.TyphoonImpact
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Open-Meteo（備援，免金鑰）。WMO 天氣代碼換成氣象署代碼與中文，讓圖示與播報共用 */
object OpenMeteoParse {
    fun wmo(code: Int): Pair<Int, String> = when (code) {
        0 -> 1 to "晴"
        1 -> 2 to "晴時多雲"
        2 -> 3 to "多雲時晴"
        3 -> 7 to "陰"
        45, 48 -> 24 to "有霧"
        51, 53, 55, 56, 57 -> 8 to "短暫毛毛雨"
        61, 63, 66 -> 11 to "短暫雨"
        65, 67 -> 14 to "陣雨"
        80, 81 -> 8 to "短暫陣雨"
        82 -> 14 to "大陣雨"
        95 -> 15 to "雷陣雨"
        96, 99 -> 17 to "雷陣雨伴有冰雹"
        in 71..77, 85, 86 -> 23 to "有雪"
        else -> 4 to "多雲"
    }

    fun parse(json: String): Pair<List<HourPoint>, List<DayForecast>> {
        val root = JSONObject(json)
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        fun col(o: JSONObject, k: String, i: Int): Double? = o.optJSONArray(k)?.let { if (it.isNull(i)) null else it.optDouble(i) }?.takeIf { !it.isNaN() }
        val hours = (0 until times.length()).mapNotNull { i ->
            val t = J.time(times.getString(i)) ?: return@mapNotNull null
            val temp = col(h, "temperature_2m", i)?.roundToInt() ?: return@mapNotNull null
            val (code, text) = wmo(col(h, "weather_code", i)?.toInt() ?: 3)
            HourPoint(
                t, temp, col(h, "apparent_temperature", i)?.roundToInt(), col(h, "precipitation_probability", i)?.roundToInt(), code, text,
                humidity = col(h, "relative_humidity_2m", i)?.roundToInt(),
                beaufort = col(h, "wind_speed_10m", i)?.let { beaufort(it / 3.6) },
            )
        }
        val d = root.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).mapNotNull { i ->
            val date = runCatching { LocalDate.parse(dt.getString(i)) }.getOrNull() ?: return@mapNotNull null
            val (code, text) = wmo(col(d, "weather_code", i)?.toInt() ?: 3)
            val hi = col(d, "temperature_2m_max", i)?.roundToInt() ?: return@mapNotNull null
            val lo = col(d, "temperature_2m_min", i)?.roundToInt() ?: return@mapNotNull null
            val dayRain = hours.filter { it.time.toLocalDate() == date && it.time.hour in 6..17 }.mapNotNull { it.rain }.maxOrNull()
            val nightRain = hours.filter { (it.time.toLocalDate() == date && it.time.hour >= 18) || (it.time.toLocalDate() == date.plusDays(1) && it.time.hour < 6) }.mapNotNull { it.rain }.maxOrNull()
            DayForecast(
                date, text, code, lo, hi,
                col(d, "apparent_temperature_min", i)?.roundToInt() ?: lo, col(d, "apparent_temperature_max", i)?.roundToInt() ?: hi,
                dayRain ?: col(d, "precipitation_probability_max", i)?.roundToInt(), nightRain,
                uvIndex = col(d, "uv_index_max", i)?.roundToInt(),
                beaufortMax = col(d, "wind_speed_10m_max", i)?.let { beaufort(it / 3.6) },
            )
        }
        return hours to days
    }

    /** 風速 m/s → 蒲福風級 */
    fun beaufort(ms: Double): Int = listOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7).indexOfFirst { ms < it }.let { if (it < 0) 12 else it }
}

/** 環境部空品（aqx_p_432 即時測站、aqf_p_01 空品區預報） */
object MoenvParse {
    data class Station(val name: String, val county: String, val aqi: Int?, val pm25: Double?, val lat: Double, val lon: Double, val time: LocalDateTime?)

    private fun records(json: String): List<JSONObject> {
        val t = json.trim()
        return if (t.startsWith("[")) J.objs(JSONArray(t)) else J.objs(J.arr(JSONObject(t), "records", "Records"))
    }

    fun stations(json: String): List<Station> = records(json).mapNotNull { r ->
        Station(
            name = J.str(r, "sitename", "SiteName") ?: return@mapNotNull null,
            county = Names.norm(J.str(r, "county", "County") ?: ""),
            aqi = J.int(r, "aqi", "AQI"),
            pm25 = J.num(r, "pm2.5", "PM2.5", "pm25"),
            lat = J.num(r, "latitude", "Latitude") ?: return@mapNotNull null,
            lon = J.num(r, "longitude", "Longitude") ?: return@mapNotNull null,
            time = J.time(J.str(r, "publishtime", "PublishTime", "datacreationdate")),
        )
    }

    fun nearest(list: List<Station>, lat: Double, lon: Double): AirReading? =
        list.filter { it.aqi != null }.minByOrNull { TyphoonImpact.distanceKm(it.lat, it.lon, lat, lon) }
            ?.let { AirReading(it.aqi!!, it.pm25, "${it.name}測站", isForecast = false) }

    /** 縣市 → 空品區 */
    fun areaOf(county: String): String = when (Names.norm(county)) {
        "基隆市", "臺北市", "新北市", "桃園市" -> "北部"
        "新竹市", "新竹縣", "苗栗縣" -> "竹苗"
        "臺中市", "彰化縣", "南投縣" -> "中部"
        "雲林縣", "嘉義市", "嘉義縣", "臺南市" -> "雲嘉南"
        "高雄市", "屏東縣" -> "高屏"
        "宜蘭縣" -> "宜蘭"
        "花蓮縣", "臺東縣" -> "花東"
        "澎湖縣" -> "澎湖"
        "金門縣" -> "金門"
        "連江縣" -> "馬祖"
        else -> "北部"
    }

    /**
     * 某空品區某天的預報 AQI。實際資料（2026-11-11 核對）：一天可能分「上午／下午」等 timeperiod 好幾筆，
     * 而且同一天會被發布好幾次。取最新一次發布裡最差（最高）的 AQI。
     */
    fun forecast(json: String, county: String, date: LocalDate): AirReading? {
        val area = areaOf(county)
        val hits = records(json).filter { r ->
            (J.str(r, "area", "Area") ?: "").contains(area) && J.str(r, "forecastdate", "ForecastDate")?.take(10)?.replace('/', '-') == date.toString()
        }
        val latest = hits.maxOfOrNull { J.str(it, "publishtime", "PublishTime") ?: "" } ?: return null
        return hits.filter { (J.str(it, "publishtime", "PublishTime") ?: "") == latest }
            .mapNotNull { J.int(it, "aqi", "AQI") }.maxOrNull()
            ?.let { AirReading(it, null, "${area}空品區", isForecast = true) }
    }
}

/** 颱風路徑（W-C0034-005）與警報（W-C0034-001）。鍵名未核對，用深度搜尋（DATA_SOURCES.md 第 2 節） */
object TyphoonParse {
    data class Raw(val name: String, val maxWind: Double?, val lat: Double, val lon: Double, val track: List<TrackPoint>)

    private fun coord(o: JSONObject): Pair<Double, Double>? {
        J.str(o, "coordinate", "Coordinate")?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { (a, b) ->
            return if (a > 90) b to a else a to b      // 官方多半是「經度,緯度」
        }
        val lat = J.num(o, "CoordinateLatitude", "latitude", "lat") ?: return null
        val lon = J.num(o, "CoordinateLongitude", "longitude", "lon") ?: return null
        return lat to lon
    }

    private fun radius(o: JSONObject, keyPart: Regex): Int? {
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!keyPart.containsMatchIn(k)) continue
            when (val v = o.opt(k)) {
                is JSONObject -> J.num(v, "radius", "Radius")?.let { r -> return r.roundToInt() }
                is Number -> return v.toInt()
                is String -> v.toDoubleOrNull()?.let { r -> return r.roundToInt() }
            }
        }
        return null
    }

    private val r7 = Regex("15\\s*ms|15Ms|C15", RegexOption.IGNORE_CASE)
    private val r70 = Regex("70", RegexOption.IGNORE_CASE)

    fun parse(json: String): List<Raw> {
        val root = JSONObject(json)
        val cyclones = J.findObjects(root) { o -> J.key(o, "cwaTyphoonName") != null || J.key(o, "typhoonName") != null || J.key(o, "cwaTdNo") != null }
        return cyclones.mapNotNull { c ->
            val name = J.str(c, "cwaTyphoonName", "CwaTyphoonName") ?: J.str(c, "typhoonName", "TyphoonName")?.let { it }
                ?: J.str(c, "cwaTdNo", "CwaTdNo")?.let { "（編號 $it）" } ?: "（未命名）"
            val analysis = J.findObjects(J.any(c, "analysisData", "AnalysisData")) { coord(it) != null }
            val last = analysis.maxByOrNull { J.str(it, "fixTime", "FixTime", "DateTime") ?: "" } ?: analysis.lastOrNull()
            val fc = J.findObjects(J.any(c, "forecastData", "ForecastData")) { coord(it) != null }
            val track = fc.mapNotNull { f ->
                val (la, lo) = coord(f)!!
                val time = J.time(J.str(f, "forecastTime", "ForecastTime"))
                    ?: J.time(J.str(f, "initTime", "InitialTime"))?.plusHours((J.num(f, "tau", "ForecastHr", "forecastHour") ?: 0.0).toLong())
                    ?: return@mapNotNull null
                TrackPoint(time, la, lo, radius(f, r7), radius(f, r70))
            }.sortedBy { it.time }
            val pos = last?.let { coord(it) } ?: track.firstOrNull()?.let { it.lat to it.lon } ?: return@mapNotNull null
            Raw(name, last?.let { J.num(it, "maxWindSpeed", "MaxWindSpeed") }, pos.first, pos.second, track)
        }.distinctBy { it.name }
    }

    /** 中心最大風速（m/s）→ 強度 */
    fun kind(maxWind: Double?): String = when {
        maxWind == null -> "颱風"
        maxWind < 17.2 -> "熱帶性低氣壓"
        maxWind < 32.7 -> "輕度颱風"
        maxWind < 51.0 -> "中度颱風"
        else -> "強烈颱風"
    }

    /** 離台灣多近才算「有颱風」（目前位置或任一預報點）：遠在日本東方的颱風不顯示、不播報 */
    const val RELEVANT_KM = 1000.0

    fun relevant(r: Raw): Boolean {
        val pts = listOf(r.lat to r.lon) + r.track.map { it.lat to it.lon }
        return pts.any { (la, lo) -> TyphoonImpact.distanceKm(la, lo, 23.7, 121.0) <= RELEVANT_KM }
    }

    /**
     * 警報：看標題（headline／alert_title）判斷海上／海上陸上；陸上警報的縣市取自 areaDesc。
     * 實際資料（2026-11-11 核對，CAP 格式）：沒有警報時仍會回最後一則「解除颱風警報」，urgency 是 Past。
     */
    fun warning(json: String): Pair<Typhoon.Warning, Set<String>> {
        val root = runCatching { JSONObject(json) }.getOrNull()
        val infos = root?.let { r -> J.findObjects(r) { J.key(it, "headline") != null || J.key(it, "areaDesc") != null && J.key(it, "event") != null } }.orEmpty()
        if (infos.isNotEmpty() && infos.all { J.str(it, "urgency")?.equals("Past", true) == true }) return Typhoon.Warning.NONE to emptySet()
        val titles = infos.flatMap { o ->
            listOfNotNull(J.str(o, "headline")) + J.objs(J.arr(o, "parameter")).filter { J.str(it, "valueName") == "alert_title" }.mapNotNull { J.str(it, "value") }
        }
        val text = if (titles.isEmpty()) json else titles.joinToString(" ")
        val level = when {
            Regex("海上陸上颱風警報").containsMatchIn(text) && !Regex("解除海上陸上颱風警報").containsMatchIn(text) -> Typhoon.Warning.SEA_LAND
            Regex("海上颱風警報").containsMatchIn(text) && !Regex("解除海上颱風警報").containsMatchIn(text) -> Typhoon.Warning.SEA
            else -> Typhoon.Warning.NONE
        }
        if (level != Typhoon.Warning.SEA_LAND) return level to emptySet()
        val areas = runCatching { J.findObjects(JSONObject(json)) { J.key(it, "areaDesc") != null }.mapNotNull { J.str(it, "areaDesc") } }.getOrDefault(emptyList())
        return level to Names.countiesIn(if (areas.isEmpty()) text else areas.joinToString(" "))
    }

    private val refs = listOf(
        "彭佳嶼" to (25.63 to 122.07), "基隆" to (25.13 to 121.74), "宜蘭" to (24.76 to 121.75), "花蓮" to (23.98 to 121.61),
        "成功" to (23.10 to 121.37), "臺東" to (22.75 to 121.15), "蘭嶼" to (22.04 to 121.55), "鵝鑾鼻" to (21.90 to 120.85),
        "恆春" to (22.00 to 120.75), "高雄" to (22.63 to 120.30), "臺南" to (22.99 to 120.21), "澎湖" to (23.57 to 119.58),
        "臺中" to (24.15 to 120.67), "新竹" to (24.80 to 120.97), "臺北" to (25.04 to 121.53),
    )
    private val dirs = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")

    /** 「恆春東南方約 620 公里」：用最近的參考點描述位置（氣象署報導的說法） */
    fun describe(lat: Double, lon: Double): String {
        val (name, p) = refs.minBy { (_, p) -> TyphoonImpact.distanceKm(lat, lon, p.first, p.second) }
        val d = TyphoonImpact.distanceKm(p.first, p.second, lat, lon)
        if (d < 30) return "${name}附近"
        val y = sin(Math.toRadians(lon - p.second)) * cos(Math.toRadians(lat))
        val x = cos(Math.toRadians(p.first)) * sin(Math.toRadians(lat)) - sin(Math.toRadians(p.first)) * cos(Math.toRadians(lat)) * cos(Math.toRadians(lon - p.second))
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
        val dir = dirs[((bearing + 22.5) / 45).toInt() % 8]
        return "${name}${dir}方約 ${(d / 10).roundToInt() * 10} 公里"
    }
}

/**
 * 停班停課（F13）：人事總處網頁表格（實際使用）；NCDR CAP Atom 解析器保留備用（2026-11-11 起要登入會員，沒在用）。
 * 人事總處沒有消息時只有一列合併儲存格「無停班停課訊息」，cells < 2 會被略過。
 */
object OffWorkParse {
    private fun clean(s: String) = s.replace(Regex("<!\\[CDATA\\[|]]>"), "").replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    /** Atom：每個 entry 的標題＋摘要，依提到的縣市分組（越前面越新） */
    fun ncdr(xml: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val body = m.groupValues[1]
            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::clean) ?: ""
            val summary = Regex("<(summary|content)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(2)?.let(::clean) ?: ""
            val text = listOf(title, summary).filter { it.isNotBlank() }.distinct().joinToString("：")
            Names.countiesIn(text).forEach { c -> out.putIfAbsent(c, text) }
        }
        return out
    }

    /** 人事總處 nds.html 的表格：第一格縣市、第二格停班停課情形 */
    fun dgpa(html: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(html).forEach { row ->
            val cells = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(row.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
            if (cells.size < 2) return@forEach
            val c = Names.countiesIn(cells[0]).firstOrNull() ?: return@forEach
            out[c] = cells.drop(1).joinToString(" ")
        }
        return out
    }
}

/** data.gov.tw 資料集 14718（辦公日曆表）的下載清單 */
object CalendarIndexParse {
    /** 回傳指定民國年的 CSV 下載網址（排除 Google 行事曆專用格式） */
    fun csvUrls(json: String, rocYears: Set<Int>): List<String> =
        J.findObjects(JSONObject(json)) { o -> o.keys().asSequence().any { it.contains("DownloadUrl", true) } }.mapNotNull { o ->
            val url = o.keys().asSequence().firstOrNull { it.contains("DownloadUrl", true) }?.let { o.optString(it) } ?: return@mapNotNull null
            val desc = o.keys().asSequence().filter { it.contains("Description", true) || it.contains("Name", true) }.joinToString(" ") { o.optString(it) }
            val fmt = o.keys().asSequence().firstOrNull { it.contains("Format", true) }?.let { o.optString(it) } ?: ""
            val isCsv = fmt.contains("CSV", true) || url.contains(".csv", true)
            val year = rocYears.firstOrNull { desc.contains("${it}年") }
            if (isCsv && year != null && !desc.contains("Google", true)) url else null
        }.distinct()
}
