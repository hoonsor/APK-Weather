package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.data.CwaParse
import tw.hoonsor.weather.data.MoenvParse
import tw.hoonsor.weather.data.OffWorkParse
import tw.hoonsor.weather.data.Sources
import tw.hoonsor.weather.data.TyphoonParse
import tw.hoonsor.weather.domain.OfficeCalendar
import tw.hoonsor.weather.domain.Typhoon
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 用 2026-11-11 22 時左右抓的實際回應（src/test/resources/real/）測解析器。
 * 回應裡沒有授權碼（授權碼只在網址）；空品預報只留最新兩次發布、content 截短。
 * 資料來源格式改了，這裡會先失敗：重抓一份換掉再修 *Parse。
 */
class RealDataTest {
    private fun res(name: String) = javaClass.getResource("/real/$name")!!.readText(Charsets.UTF_8).removePrefix("﻿")

    @Test fun cwaTownForecast() {
        val r = CwaParse.parse(res("cwa_093.json"), "中壢區")
        assertEquals("桃園市中壢區", r.town?.full)
        assertEquals(24.967495, r.town!!.lat, 1e-6)
        assertTrue("逐時筆數 ${r.hours.size}", r.hours.size >= 40)
        val first = r.hours.first()
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), first.time)
        assertEquals(20, first.temp); assertEquals(20, first.feels); assertEquals(10, first.rain)
        assertEquals(1, first.code); assertEquals("晴", first.weather)
        assertEquals(80, first.humidity); assertEquals("舒適", first.comfort)
        // 01:00 沒有風的資料（風逐 3 小時），沿用 00:00 的 3 級東北風
        val h1 = r.hours.first { it.time == LocalDateTime.of(2026, 11, 12, 1, 0) }
        assertEquals(3, h1.beaufort); assertEquals("東北風", h1.windDir)
        assertTrue(r.hours.all { it.beaufort != null })

        val d = r.days.first { it.date == LocalDate.of(2026, 11, 12) }
        assertTrue("最高溫 ${d.tempMax}", d.tempMax >= 28)
        assertTrue("最低溫 ${d.tempMin}", d.tempMin <= 19)
        assertEquals(5, d.uvIndex)
        assertNotNull(d.rainDay); assertNotNull(d.rainNight)
        assertTrue(d.weather.isNotBlank())
        assertTrue("天數 ${r.days.size}", r.days.size >= 6)
        assertEquals(listOf("桃園市中壢區"), CwaParse.towns(res("cwa_093.json")).map { it.full })
    }

    @Test fun typhoonTrackAndFarAway() {
        val raws = TyphoonParse.parse(res("typhoon_track.json"))
        assertEquals(1, raws.size)
        val r = raws[0]
        assertEquals("查帕卡", r.name)
        assertEquals(2, r.track.size)
        assertEquals(LocalDateTime.of(2026, 11, 11, 20, 0), r.track[0].time)   // InitialTime 14 時＋6 小時
        assertEquals(150, r.track[0].radius7Km); assertEquals(40, r.track[0].radius70Km)
        assertNotNull(r.maxWind)
        // 在日本東方，離台灣很遠：不算「有颱風」
        assertFalse(TyphoonParse.relevant(r))
        assertTrue(TyphoonParse.relevant(r.copy(lat = 21.0, lon = 125.0, track = emptyList())))
    }

    @Test fun typhoonWarningEnded() {
        assertEquals(Typhoon.Warning.NONE to emptySet<String>(), TyphoonParse.warning(res("typhoon_warn.json")))
        // 同樣格式、改成發布中的陸上警報
        val active = res("typhoon_warn.json").replace("\"Past\"", "\"Immediate\"").replace("解除颱風警報", "海上陸上颱風警報")
        val (lv, counties) = TyphoonParse.warning(active)
        assertEquals(Typhoon.Warning.SEA_LAND, lv)
        assertTrue("桃園市" in counties)
    }

    private fun bytes(name: String) = javaClass.getResource("/real/$name")!!.readBytes()

    @Test fun officeCalendarCsv() {
        val c = OfficeCalendar.parse(Sources.decodeText(bytes("cal_115.csv")))
        assertEquals(setOf(2026), c.years)
        assertFalse(c.isWorkday(LocalDate.of(2026, 1, 1)))     // 開國紀念日
        assertTrue(c.isWorkday(LocalDate.of(2026, 1, 2)))
        assertFalse(c.isWorkday(LocalDate.of(2026, 1, 3)))     // 週六
        // 114 年修正檔是 Big5：要能正確解出備註
        val b = OfficeCalendar.parse(Sources.decodeText(bytes("cal_114_big5.csv")))
        assertEquals("開國紀念日", b.note(LocalDate.of(2025, 1, 1)))
    }

    @Test fun dgpaNoOffWork() {
        assertTrue(OffWorkParse.dgpa(Sources.decodeText(bytes("dgpa_none.html"))).isEmpty())
    }

    @Test fun airQuality() {
        val st = MoenvParse.stations(res("aqx.json"))
        assertEquals(84, st.size)
        val near = MoenvParse.nearest(st, 24.9675, 121.2169)!!
        assertTrue(near.area, near.area.endsWith("測站"))
        assertTrue(st.first().time != null)
        val f = MoenvParse.forecast(res("aqf.json"), "桃園市", LocalDate.of(2026, 11, 12))!!
        assertEquals(60, f.aqi); assertEquals("北部空品區", f.area)
        assertEquals(80, MoenvParse.forecast(res("aqf.json"), "臺北市", LocalDate.of(2026, 11, 14))!!.aqi)
    }
}
