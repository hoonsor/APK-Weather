package tw.hoonsor.weather

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.data.CalendarIndexParse
import tw.hoonsor.weather.data.CwaParse
import tw.hoonsor.weather.data.MoenvParse
import tw.hoonsor.weather.data.Names
import tw.hoonsor.weather.data.OffWorkParse
import tw.hoonsor.weather.data.OpenMeteoParse
import tw.hoonsor.weather.data.SnapshotJson
import tw.hoonsor.weather.data.TyphoonParse
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.Sun
import tw.hoonsor.weather.domain.Typhoon
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 解析器測試。範例 JSON 是照官方文件的結構手寫的（DATA_SOURCES.md），
 * 拿到授權碼後要換成實際回應再跑一次（PROJECT_STATUS「待實機驗證」）。
 */
class ParsersTest {
    private fun ev(vararg kv: Pair<String, Any>) = JSONArray().put(JSONObject().apply { kv.forEach { (k, v) -> put(k, v) } })
    private fun at(t: String, vararg kv: Pair<String, Any>) = JSONObject().put("DataTime", t).put("ElementValue", ev(*kv))
    private fun span(s: String, e: String, vararg kv: Pair<String, Any>) = JSONObject().put("StartTime", s).put("EndTime", e).put("ElementValue", ev(*kv))
    private fun el(name: String, vararg times: JSONObject) = JSONObject().put("ElementName", name).put("Time", JSONArray(times.toList()))

    private fun cwaSample(): String {
        val d = "2026-11-11"
        val short = JSONObject().put("LocationName", "中壢區").put("Geocode", "68000020").put("Latitude", "24.9656").put("Longitude", "121.2247")
            .put("WeatherElement", JSONArray(listOf(
                el("溫度", at("${d}T06:00:00+08:00", "Temperature" to "18"), at("${d}T09:00:00+08:00", "Temperature" to "21"), at("${d}T12:00:00+08:00", "Temperature" to "24"),
                    at("${d}T15:00:00+08:00", "Temperature" to "25"), at("${d}T18:00:00+08:00", "Temperature" to "22"), at("${d}T21:00:00+08:00", "Temperature" to "20")),
                el("體感溫度", at("${d}T06:00:00+08:00", "ApparentTemperature" to "17"), at("${d}T09:00:00+08:00", "ApparentTemperature" to "20"), at("${d}T12:00:00+08:00", "ApparentTemperature" to "23"),
                    at("${d}T15:00:00+08:00", "ApparentTemperature" to "24"), at("${d}T18:00:00+08:00", "ApparentTemperature" to "21"), at("${d}T21:00:00+08:00", "ApparentTemperature" to "19")),
                el("3小時降雨機率", span("${d}T06:00:00+08:00", "${d}T09:00:00+08:00", "ProbabilityOfPrecipitation" to "10"), span("${d}T15:00:00+08:00", "${d}T18:00:00+08:00", "ProbabilityOfPrecipitation" to "20"),
                    span("${d}T18:00:00+08:00", "${d}T21:00:00+08:00", "ProbabilityOfPrecipitation" to "40"), span("${d}T21:00:00+08:00", "2026-11-12T00:00:00+08:00", "ProbabilityOfPrecipitation" to "30")),
                el("天氣現象", span("${d}T06:00:00+08:00", "${d}T12:00:00+08:00", "Weather" to "多雲時晴", "WeatherCode" to "03")),
                el("相對濕度", at("${d}T06:00:00+08:00", "RelativeHumidity" to "82")),
                el("風速", at("${d}T06:00:00+08:00", "WindSpeed" to "3", "BeaufortScale" to "2")),
            )))
        val week = JSONObject().put("LocationName", "中壢區").put("Latitude", "24.9656").put("Longitude", "121.2247")
            .put("WeatherElement", JSONArray(listOf(
                el("最高溫度", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "MaxTemperature" to "25"), span("${d}T18:00:00+08:00", "2026-11-12T06:00:00+08:00", "MaxTemperature" to "21")),
                el("最低溫度", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "MinTemperature" to "19"), span("${d}T18:00:00+08:00", "2026-11-12T06:00:00+08:00", "MinTemperature" to "18")),
                el("最高體感溫度", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "MaxApparentTemperature" to "26")),
                el("最低體感溫度", span("${d}T18:00:00+08:00", "2026-11-12T06:00:00+08:00", "MinApparentTemperature" to "16")),
                el("12小時降雨機率", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "ProbabilityOfPrecipitation" to "20"), span("${d}T18:00:00+08:00", "2026-11-12T06:00:00+08:00", "ProbabilityOfPrecipitation" to "-")),
                el("天氣現象", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "Weather" to "多雲時晴", "WeatherCode" to "03")),
                el("紫外線指數", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "UVIndex" to "6", "UVExposureLevel" to "高量級")),
                el("風速", span("${d}T06:00:00+08:00", "${d}T18:00:00+08:00", "WindSpeed" to "4", "BeaufortScale" to "3")),
            )))
        val locs = JSONArray()
            .put(JSONObject().put("DatasetDescription", "臺灣各鄉鎮市區預報資料").put("LocationsName", "桃園市").put("Dataid", "D0047-005").put("Location", JSONArray().put(short)))
            .put(JSONObject().put("DatasetDescription", "臺灣各鄉鎮市區預報資料").put("LocationsName", "桃園市").put("Dataid", "D0047-007").put("Location", JSONArray().put(week)))
        return JSONObject().put("success", "true").put("records", JSONObject().put("Locations", locs)).toString()
    }

    @Test fun cwaForecast() {
        val r = CwaParse.parse(cwaSample(), "中壢區")
        assertEquals("桃園市中壢區", r.town?.full)
        assertEquals(24.9656, r.town!!.lat, 1e-6)
        assertEquals(6, r.hours.size)
        val h = r.hours.first { it.time.hour == 18 }
        assertEquals(22, h.temp); assertEquals(21, h.feels); assertEquals(40, h.rain)
        assertEquals(82, r.hours.first().humidity); assertEquals(2, r.hours.first().beaufort)
        assertEquals(3, r.hours.first().code); assertEquals("多雲時晴", r.hours.first().weather)
        val d = r.days.single()
        assertEquals(LocalDate.of(2026, 11, 11), d.date)
        assertEquals(18, d.tempMin); assertEquals(25, d.tempMax)
        // 3 天資料補細節：白天 3 小時降雨最大 20、晚上 40；體感 17–24
        assertEquals(20, d.rainDay); assertEquals(40, d.rainNight)
        assertEquals(17, d.feelsMin); assertEquals(24, d.feelsMax)
        assertEquals(6, d.uvIndex); assertEquals(3, d.beaufortMax)
    }

    @Test fun cwaLowercaseKeysAndTownList() {
        val lower = cwaSample().replace("\"Locations\"", "\"locations\"").replace("\"Location\"", "\"location\"").replace("\"LocationName\"", "\"locationName\"")
        assertEquals(6, CwaParse.parse(lower, "中壢區").hours.size)
        assertEquals(listOf("桃園市中壢區"), CwaParse.towns(cwaSample()).map { it.full })
        assertEquals("F-D0047-005" to "F-D0047-007", CwaParse.datasets["桃園市"])
        assertEquals("F-D0047-061" to "F-D0047-063", CwaParse.datasets["臺北市"])
        assertEquals(22, CwaParse.datasets.size)
    }

    @Test fun names() {
        assertEquals("桃園市" to "中壢區", Names.split("台灣桃園市 中壢區"))
        assertEquals(null to "板橋區", Names.split("板橋區"))
        assertEquals(setOf("臺北市", "新北市"), Names.countiesIn("台北市、新北市停止上班"))
        assertTrue("新竹市" !in Names.countiesIn("新竹縣停課"))
    }

    @Test fun openMeteo() {
        val hours = (0 until 48).map { LocalDateTime.of(2026, 11, 10, 0, 0).plusHours(it.toLong()) }
        val o = JSONObject()
            .put("hourly", JSONObject().put("time", JSONArray(hours.map { it.toString() })).put("temperature_2m", JSONArray(hours.map { 15 + it.hour % 10 }))
                .put("apparent_temperature", JSONArray(hours.map { 14 + it.hour % 10 })).put("precipitation_probability", JSONArray(hours.map { if (it.hour >= 18) 60 else 10 }))
                .put("weather_code", JSONArray(hours.map { 2 })).put("relative_humidity_2m", JSONArray(hours.map { 80 })).put("wind_speed_10m", JSONArray(hours.map { 18.0 })))
            .put("daily", JSONObject().put("time", JSONArray(listOf("2026-11-10", "2026-11-11"))).put("weather_code", JSONArray(listOf(61, 0)))
                .put("temperature_2m_max", JSONArray(listOf(24.4, 26.0))).put("temperature_2m_min", JSONArray(listOf(15.2, 16.0)))
                .put("apparent_temperature_max", JSONArray(listOf(23.0, 25.0))).put("apparent_temperature_min", JSONArray(listOf(13.6, 15.0)))
                .put("precipitation_probability_max", JSONArray(listOf(60, 10))).put("uv_index_max", JSONArray(listOf(5.4, 7.0))).put("wind_speed_10m_max", JSONArray(listOf(30.0, 10.0))))
        val (h, d) = OpenMeteoParse.parse(o.toString())
        assertEquals(48, h.size); assertEquals(3, h.first().beaufort)   // 18 km/h = 5 m/s → 3 級
        assertEquals(2, d.size)
        assertEquals("短暫雨", d[0].weather); assertEquals(24, d[0].tempMax); assertEquals(15, d[0].tempMin)
        assertEquals(10, d[0].rainDay); assertEquals(60, d[0].rainNight)
        assertEquals(1, d[1].weatherCode)
    }

    @Test fun moenv() {
        val st = JSONObject().put("records", JSONArray()
            .put(JSONObject().put("sitename", "中壢").put("county", "桃園市").put("aqi", "112").put("pm2.5", "34").put("latitude", "24.953").put("longitude", "121.221").put("publishtime", "2026/11/10 20:00:00"))
            .put(JSONObject().put("sitename", "古亭").put("county", "臺北市").put("aqi", "45").put("pm2.5", "9").put("latitude", "25.020").put("longitude", "121.529"))
            .put(JSONObject().put("sitename", "壞資料").put("county", "臺北市").put("aqi", "").put("latitude", "25.0").put("longitude", "121.5")))
        val list = MoenvParse.stations(st.toString())
        assertEquals(3, list.size)
        val near = MoenvParse.nearest(list, 24.96, 121.22)!!
        assertEquals(112, near.aqi); assertEquals("中壢測站", near.area); assertEquals(34.0, near.pm25!!, 0.01)
        val fc = JSONArray()
            .put(JSONObject().put("area", "北部").put("forecastdate", "2026-11-11").put("aqi", "95").put("publishtime", "2026-11-10 10:30"))
            .put(JSONObject().put("area", "北部").put("forecastdate", "2026-11-11").put("aqi", "105").put("publishtime", "2026-11-10 16:30"))
            .put(JSONObject().put("area", "高屏").put("forecastdate", "2026-11-11").put("aqi", "130").put("publishtime", "2026-11-10 16:30"))
        val f = MoenvParse.forecast(fc.toString(), "桃園市", LocalDate.of(2026, 11, 11))!!
        assertEquals(105, f.aqi); assertTrue(f.isForecast); assertEquals("北部空品區", f.area)
        assertNull(MoenvParse.forecast(fc.toString(), "臺東縣", LocalDate.of(2026, 11, 11)))
    }

    @Test fun typhoon() {
        val fix = { t: String, lon: Double, lat: Double, r7: Int, r70: Int -> JSONObject().put("initTime", "2026-11-10T14:00:00+08:00").put("tau", t).put("coordinate", "$lon,$lat")
            .put("maxWindSpeed", 38).put("circleOf15Ms", JSONObject().put("radius", r7)).put("radiusOf70PercentProbability", r70) }
        val o = JSONObject().put("records", JSONObject().put("tropicalCyclones", JSONObject().put("tropicalCyclone", JSONArray().put(
            JSONObject().put("year", 2026).put("typhoonName", "GINKGO").put("cwaTyphoonName", "銀杏").put("cwaTdNo", "25")
                .put("analysisData", JSONObject().put("fix", JSONArray().put(JSONObject().put("fixTime", "2026-11-10T14:00:00+08:00").put("coordinate", "126.2,19.8").put("maxWindSpeed", 38))))
                .put("forecastData", JSONObject().put("fix", JSONArray().put(fix("24", 124.0, 21.0, 180, 100)).put(fix("48", 122.4, 22.6, 200, 180)))),
        ))))
        val raws = TyphoonParse.parse(o.toString())
        assertEquals(1, raws.size)
        val r = raws[0]
        assertEquals("銀杏", r.name); assertEquals(19.8, r.lat, 1e-6); assertEquals(126.2, r.lon, 1e-6)
        assertEquals(2, r.track.size)
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), r.track[1].time)
        assertEquals(200, r.track[1].radius7Km); assertEquals(180, r.track[1].radius70Km)
        assertEquals("中度颱風", TyphoonParse.kind(r.maxWind))
        assertTrue(TyphoonParse.describe(r.lat, r.lon), TyphoonParse.describe(r.lat, r.lon).matches(Regex(".+(東南|南|東)方約 \\d+ 公里")))
        assertTrue(TyphoonParse.parse(JSONObject().put("records", JSONObject()).toString()).isEmpty())
    }

    @Test fun typhoonWarning() {
        val land = """{"records":{"info":[{"headline":"海上陸上颱風警報","areaDesc":"臺北市、新北市、桃園市"}]}}"""
        val (lv, counties) = TyphoonParse.warning(land)
        assertEquals(Typhoon.Warning.SEA_LAND, lv); assertEquals(setOf("臺北市", "新北市", "桃園市"), counties)
        assertEquals(Typhoon.Warning.SEA, TyphoonParse.warning("""{"headline":"海上颱風警報"}""").first)
        assertEquals(Typhoon.Warning.NONE, TyphoonParse.warning("""{"headline":"解除海上颱風警報"}""").first)
    }

    @Test fun offWork() {
        val xml = """<feed><entry><title>[停班停課通知]桃園市</title><summary><![CDATA[桃園市：明天停止上班、停止上課。]]></summary></entry>
            <entry><title>臺北市</title><content type="html">臺北市：照常上班、照常上課。</content></entry></feed>"""
        val m = OffWorkParse.ncdr(xml)
        assertTrue(m["桃園市"]!!.contains("停止上班"))
        assertTrue(m["臺北市"]!!.contains("照常上班"))
        val html = "<table><tr><th>縣市</th><th>情形</th></tr><tr><td><font>桃園市</font></td><td>今天停止上班、停止上課。</td></tr></table>"
        assertEquals("今天停止上班、停止上課。", OffWorkParse.dgpa(html)["桃園市"])
    }

    @Test fun calendarIndex() {
        val o = JSONObject().put("result", JSONObject().put("distribution", JSONArray()
            .put(JSONObject().put("resourceDescription", "115年中華民國政府行政機關辦公日曆表").put("resourceFormat", "CSV").put("resourceDownloadUrl", "https://x/115.csv"))
            .put(JSONObject().put("resourceDescription", "115年中華民國政府行政機關辦公日曆表(Google行事曆專用)").put("resourceFormat", "CSV").put("resourceDownloadUrl", "https://x/115g.csv"))
            .put(JSONObject().put("resourceDescription", "114年中華民國政府行政機關辦公日曆表").put("resourceFormat", "CSV").put("resourceDownloadUrl", "https://x/114.csv"))
            .put(JSONObject().put("resourceDescription", "116年中華民國政府行政機關辦公日曆表").put("resourceFormat", "JSON").put("resourceDownloadUrl", "https://x/116.json"))))
        assertEquals(listOf("https://x/115.csv"), CalendarIndexParse.csvUrls(o.toString(), setOf(115, 116)))
    }

    @Test fun snapshotRoundTrip() {
        val r = CwaParse.parse(cwaSample(), "中壢區")
        val s = Snapshot(PlaceKind.SCHOOL, r.town!!, LocalDateTime.of(2026, 11, 10, 20, 40), "cwa", r.hours, r.days,
            tw.hoonsor.weather.domain.AirReading(112, 34.0, "中壢測站", false), null)
        val back = SnapshotJson.read(JSONObject(SnapshotJson.write(s).toString()))
        assertEquals(s, back)
        val t = Typhoon("銀杏", "中度颱風", "恆春東南方約 620 公里", listOf(tw.hoonsor.weather.domain.TrackPoint(LocalDateTime.of(2026, 11, 12, 14, 0), 22.6, 122.4, 200, null)), Typhoon.Warning.SEA_LAND, setOf("桃園市"))
        assertEquals(t, SnapshotJson.typhoon(SnapshotJson.typhoon(t)))
    }

    @Test fun sunTimes() {
        // 台北 2026-06-21：日出約 05:04、日落約 18:47（中央氣象署天文資料，容許 ±5 分鐘）
        val (rise, set) = Sun.times(LocalDate.of(2026, 6, 21), 25.04, 121.51)!!
        assertTrue("$rise", rise.toSecondOfDay() in (5 * 3600 - 300)..(5 * 3600 + 4 * 60 + 300))
        assertTrue("$set", set.toSecondOfDay() in (18 * 3600 + 47 * 60 - 300)..(18 * 3600 + 47 * 60 + 300))
        assertNotNull(Sun.times(LocalDate.of(2026, 12, 21), 22.0, 120.7))
    }
}
