package tw.hoonsor.weather.data

import org.json.JSONObject
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.HourPoint
import tw.hoonsor.weather.domain.Town
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 中央氣象署鄉鎮預報（F-D0047-093，locationId 帶「3 天」與「1 週」兩個資料集）解析。
 * 結構依 DATA_SOURCES.md 第 1 節：Locations → LocationsName → Location → WeatherElement(ElementName) → Time → ElementValue。
 * 鍵名大小寫用 [J] 寬鬆比對；2026-11-11 已用實際回應核對（測試資料 src/test/resources/real/cwa_093.json）。
 */
object CwaParse {
    /** 縣市 → (3 天, 1 週) 資料集代碼。單號 3 天、雙號 1 週 */
    val datasets: Map<String, Pair<String, String>> = listOf(
        "宜蘭縣" to 1, "桃園市" to 5, "新竹縣" to 9, "苗栗縣" to 13, "彰化縣" to 17, "南投縣" to 21, "雲林縣" to 25,
        "嘉義縣" to 29, "屏東縣" to 33, "臺東縣" to 37, "花蓮縣" to 41, "澎湖縣" to 45, "基隆市" to 49, "新竹市" to 53,
        "嘉義市" to 57, "臺北市" to 61, "高雄市" to 65, "新北市" to 69, "臺中市" to 73, "臺南市" to 77, "連江縣" to 81, "金門縣" to 85,
    ).associate { (c, n) -> c to ("F-D0047-%03d".format(n) to "F-D0047-%03d".format(n + 2)) }

    data class Period(val start: LocalDateTime?, val end: LocalDateTime?, val at: LocalDateTime?, val v: JSONObject)

    data class Result(val town: Town?, val hours: List<HourPoint>, val days: List<DayForecast>)

    /** 元素名稱（2024-12 起中文）＋舊英文代碼當備援 */
    private val names = mapOf(
        "temp" to listOf("溫度", "T"),
        "feels" to listOf("體感溫度", "AT"),
        "rain3" to listOf("3小時降雨機率", "PoP3h"),
        "rain6" to listOf("6小時降雨機率", "PoP6h"),
        "wx" to listOf("天氣現象", "Wx"),
        "rh" to listOf("相對濕度", "RH"),
        "ws" to listOf("風速", "WS"),
        "wd" to listOf("風向", "WD"),
        "ci" to listOf("舒適度指數", "CI"),
        "maxT" to listOf("最高溫度", "MaxT"),
        "minT" to listOf("最低溫度", "MinT"),
        "maxAT" to listOf("最高體感溫度", "MaxAT"),
        "minAT" to listOf("最低體感溫度", "MinAT"),
        "rain12" to listOf("12小時降雨機率", "PoP12h"),
        "uv" to listOf("紫外線指數", "UVI"),
    )

    fun parse(json: String, townName: String): Result {
        val root = JSONObject(json)
        val want = Names.norm(townName)
        // 所有 Location（鄉鎮），找名稱相符的；3 天與 1 週各一筆
        val locs = J.findObjects(root) { o -> J.arr(o, "WeatherElement", "weatherElement") != null && J.str(o, "LocationName", "locationName") != null }
            .filter { Names.norm(J.str(it, "LocationName", "locationName")!!) == want }
        var town: Town? = null
        var short: Map<String, List<Period>> = emptyMap()
        var week: Map<String, List<Period>> = emptyMap()
        for (loc in locs) {
            val els = elements(loc)
            if (has(els, "maxT") || has(els, "rain12")) week = els else short = els
            if (town == null) {
                val lat = J.num(loc, "Latitude", "lat")
                val lon = J.num(loc, "Longitude", "lon")
                val county = countyOf(root, loc)
                if (lat != null && lon != null && county != null) town = Town(county, want, lat, lon)
            }
        }
        return Result(town, hours(short), days(week, short))
    }

    /** 某縣市資料集裡所有鄉鎮名稱與座標（place_set 比對鄉鎮用） */
    fun towns(json: String): List<Town> {
        val root = JSONObject(json)
        return J.findObjects(root) { J.str(it, "LocationName", "locationName") != null && J.num(it, "Latitude") != null }
            .mapNotNull { loc ->
                val c = countyOf(root, loc) ?: return@mapNotNull null
                Town(c, Names.norm(J.str(loc, "LocationName")!!), J.num(loc, "Latitude")!!, J.num(loc, "Longitude") ?: return@mapNotNull null)
            }.distinctBy { it.full }
    }

    /** 往上找包住這個 Location 的 Locations 物件的 LocationsName（縣市） */
    private fun countyOf(root: JSONObject, loc: JSONObject): String? {
        val parent = J.findObjects(root) { o -> J.objs(J.arr(o, "Location", "location")).any { it === loc } }.firstOrNull()
        return J.str(parent, "LocationsName", "locationsName")?.let(Names::norm)
    }

    private fun has(els: Map<String, List<Period>>, k: String) = names.getValue(k).any { it in els }

    private fun elements(loc: JSONObject): Map<String, List<Period>> =
        J.objs(J.arr(loc, "WeatherElement", "weatherElement")).associate { el ->
            val name = J.str(el, "ElementName", "elementName") ?: ""
            name to J.objs(J.arr(el, "Time", "time")).map { t ->
                val vals = J.arr(t, "ElementValue", "elementValue")
                val merged = JSONObject()
                J.objs(vals).forEach { v -> v.keys().forEach { k -> merged.put(k, v.opt(k)) } }
                Period(J.time(J.str(t, "StartTime", "startTime")), J.time(J.str(t, "EndTime", "endTime")), J.time(J.str(t, "DataTime", "dataTime")), merged)
            }
        }

    private fun get(els: Map<String, List<Period>>, k: String): List<Period> = names.getValue(k).firstNotNullOfOrNull { els[it] } ?: emptyList()

    /** 值：先找指定鍵名，找不到且只有一個值時用那個 */
    private fun value(p: Period, vararg keys: String): String? =
        J.str(p.v, *keys) ?: if (p.v.length() == 1) J.str(p.v, p.v.keys().next()) else null

    private fun covering(list: List<Period>, t: LocalDateTime): Period? =
        list.firstOrNull { val s = it.start; val e = it.end; s != null && e != null && !t.isBefore(s) && t.isBefore(e) }
            ?: list.firstOrNull { it.at == t }

    /**
     * 逐時點（DataTime）用：同一時間的值，沒有就用 3 小時內最近的前一筆。
     * 實際資料：溫度、體感、濕度、舒適度前段逐 1 小時；風速、風向逐 3 小時（2026-11-11 核對）。
     */
    private fun atOrBefore(list: List<Period>, t: LocalDateTime): Period? =
        list.firstOrNull { it.at == t } ?: covering(list, t)
            ?: list.filter { val a = it.at; a != null && !a.isAfter(t) && a.isAfter(t.minusHours(3)) }.maxByOrNull { it.at!! }

    private fun hours(els: Map<String, List<Period>>): List<HourPoint> {
        val temps = get(els, "temp")
        val feels = get(els, "feels")
        val rain = get(els, "rain3").ifEmpty { get(els, "rain6") }
        val wx = get(els, "wx")
        val rh = get(els, "rh")
        val ws = get(els, "ws")
        val wd = get(els, "wd")
        val ci = get(els, "ci")
        return temps.mapNotNull { p ->
            val t = p.at ?: p.start ?: return@mapNotNull null
            val temp = value(p, "Temperature")?.toDoubleOrNull()?.let { Math.round(it).toInt() } ?: return@mapNotNull null
            val w = covering(wx, t)
            HourPoint(
                time = t, temp = temp,
                feels = atOrBefore(feels, t)?.let { value(it, "ApparentTemperature")?.toDoubleOrNull()?.let { v -> Math.round(v).toInt() } },
                rain = covering(rain, t)?.let { value(it, "ProbabilityOfPrecipitation")?.toIntOrNull() },
                code = w?.let { value(it, "WeatherCode")?.toIntOrNull() } ?: 0,
                weather = w?.let { J.str(it.v, "Weather") } ?: "",
                humidity = atOrBefore(rh, t)?.let { value(it, "RelativeHumidity")?.toIntOrNull() },
                beaufort = atOrBefore(ws, t)?.let { J.str(it.v, "BeaufortScale")?.filter { c -> c.isDigit() }?.take(2)?.toIntOrNull() },
                windDir = atOrBefore(wd, t)?.let { J.str(it.v, "WindDirection") },
                comfort = atOrBefore(ci, t)?.let { J.str(it.v, "ComfortIndexDescription") },
            )
        }.sortedBy { it.time }
    }

    /** 1 週資料是 12 小時一段（06–18 白天、18–06 晚上），合成每天一筆 */
    private fun days(week: Map<String, List<Period>>, short: Map<String, List<Period>>): List<DayForecast> {
        val maxT = get(week, "maxT"); val minT = get(week, "minT")
        val maxAT = get(week, "maxAT"); val minAT = get(week, "minAT")
        val pop = get(week, "rain12"); val wx = get(week, "wx"); val uv = get(week, "uv"); val ws = get(week, "ws")
        val dates = (maxT + minT + wx).mapNotNull { it.start?.let { s -> if (s.hour < 6) s.toLocalDate().minusDays(1) else s.toLocalDate() } }.distinct().sorted()
        fun dayP(list: List<Period>, d: LocalDate) = list.firstOrNull { val s = it.start; s != null && s.toLocalDate() == d && s.hour in 6..17 }
        fun nightP(list: List<Period>, d: LocalDate) = list.firstOrNull { val s = it.start ?: return@firstOrNull false; (s.toLocalDate() == d && s.hour >= 18) || (s.toLocalDate() == d.plusDays(1) && s.hour < 6) }
        fun ints(list: List<Period>, d: LocalDate, vararg keys: String) = listOfNotNull(dayP(list, d), nightP(list, d)).mapNotNull { value(it, *keys)?.toDoubleOrNull()?.let { v -> Math.round(v).toInt() } }
        return dates.mapNotNull { d ->
            val hi = ints(maxT, d, "MaxTemperature").maxOrNull() ?: return@mapNotNull null
            val lo = ints(minT, d, "MinTemperature").minOrNull() ?: return@mapNotNull null
            val w = dayP(wx, d) ?: nightP(wx, d)
            DayForecast(
                date = d,
                weather = w?.let { J.str(it.v, "Weather") } ?: "",
                weatherCode = w?.let { value(it, "WeatherCode")?.toIntOrNull() } ?: 0,
                tempMin = lo, tempMax = hi,
                feelsMin = ints(minAT, d, "MinApparentTemperature").minOrNull() ?: lo,
                feelsMax = ints(maxAT, d, "MaxApparentTemperature").maxOrNull() ?: hi,
                rainDay = dayP(pop, d)?.let { value(it, "ProbabilityOfPrecipitation")?.toIntOrNull() },
                rainNight = nightP(pop, d)?.let { value(it, "ProbabilityOfPrecipitation")?.toIntOrNull() },
                uvIndex = dayP(uv, d)?.let { J.str(it.v, "UVIndex")?.toIntOrNull() },
                beaufortMax = ints(ws, d, "BeaufortScale").maxOrNull(),
            )
        }.let { list -> refineWithShort(list, short) }
    }

    /**
     * 3 天資料比較細（逐 3 小時），有的話用它補明天的降雨與體感：
     * 白天 06–18 取 3 小時降雨機率最大值、晚上 18–06 同理；體感取該日 06–24 的最高／最低。
     */
    private fun refineWithShort(days: List<DayForecast>, short: Map<String, List<Period>>): List<DayForecast> {
        if (short.isEmpty()) return days
        val hs = hours(short)
        if (hs.isEmpty()) return days
        return days.map { d ->
            val dayHs = hs.filter { it.time.toLocalDate() == d.date && it.time.hour in 6..23 }
            if (dayHs.size < 4) return@map d
            val pd = hs.filter { it.time.toLocalDate() == d.date && it.time.hour in 6..17 }.mapNotNull { it.rain }.maxOrNull()
            val pn = hs.filter { (it.time.toLocalDate() == d.date && it.time.hour >= 18) || (it.time.toLocalDate() == d.date.plusDays(1) && it.time.hour < 6) }.mapNotNull { it.rain }.maxOrNull()
            val f = dayHs.mapNotNull { it.feels }
            d.copy(
                rainDay = pd ?: d.rainDay, rainNight = pn ?: d.rainNight,
                feelsMin = f.minOrNull() ?: d.feelsMin, feelsMax = f.maxOrNull() ?: d.feelsMax,
            )
        }
    }
}
