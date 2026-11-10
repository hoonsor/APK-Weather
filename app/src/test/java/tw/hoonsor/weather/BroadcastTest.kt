package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.Broadcast
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.TrackPoint
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.domain.TyphoonImpact.Level
import tw.hoonsor.weather.domain.WeatherIcon
import tw.hoonsor.weather.domain.WeatherIcons
import java.time.LocalDate
import java.time.LocalDateTime

class BroadcastTest {
    private val town = Town("桃園市", "中壢區", 24.96, 121.22)
    private val now = LocalDateTime.of(2026, 11, 10, 21, 0)   // 星期二

    @Test fun tomorrowSentence() {
        val d = DayForecast(LocalDate.of(2026, 11, 11), "多雲時晴", 3, 18, 25, 17, 24, 20, 40, 6, 3)
        val s = Broadcast.tomorrow(d, PlaceKind.SCHOOL, town, ClothingRules.advise(d))
        assertEquals("明天 11 月 11 日星期三，學校在中壢區，白天多雲時晴，體感 17 到 24 度，晚上降雨機率 40%。建議短袖加薄外套，晚上帶把折傘。", s)
    }

    @Test fun airOnlyWhenAlert() {
        assertNull(Broadcast.airAlert(AirReading(100, null, "北部空品區", true), tomorrow = true))
        assertEquals("明天空氣品質橘色，對敏感族群不健康，外出建議戴口罩。", Broadcast.airAlert(AirReading(105, null, "北部空品區", true), tomorrow = true))
        assertEquals(Aqi.Level.MAROON, Aqi.level(301))
        assertTrue(Broadcast.air(AirReading(40, 8.0, "中壢測站", false)).contains("不用戴口罩"))
    }

    @Test fun typhoonSentence() {
        val t = Typhoon("銀杏颱風", "中度颱風", "恆春東南方約 620 公里", listOf(TrackPoint(now.plusHours(56), 22.6, 122.4, 150, 120)), Typhoon.Warning.SEA)
        val s = Broadcast.typhoon(t, LocalDateTime.of(2026, 11, 13, 6, 0), now, listOf("學校" to Level.DIRECT, "家裡" to Level.OUTER), "停班停課還沒公布", short = true)
        assertEquals(
            "另外，銀杏颱風中度颱風目前在恆春東南方約 620 公里，預估星期五清晨最接近台灣，已發布海上警報。依目前路徑，學校可能進入暴風圈，家裡是外圍影響。路徑還可能改變，會再更新。停班停課還沒公布。",
            s,
        )
    }

    @Test fun typhoonFarIsUncertain() {
        val t = Typhoon("某颱風", "輕度颱風", "關島附近", emptyList(), Typhoon.Warning.NONE)
        val s = Broadcast.typhoon(t, now.plusDays(6), now, listOf("學校" to Level.LOW, "家裡" to Level.LOW), null, short = false)
        assertTrue(s, s.contains("學校和家裡影響不大"))
        assertTrue(s, s.endsWith("還在 5 天以後，路徑還不確定。"))
    }

    @Test fun whenText() {
        assertEquals("明天傍晚", Broadcast.whenText(LocalDateTime.of(2026, 11, 11, 18, 0), now.toLocalDate()))
        assertEquals("今天晚上", Broadcast.whenText(LocalDateTime.of(2026, 11, 10, 22, 0), now.toLocalDate()))
    }

    @Test fun icons() {
        assertEquals(WeatherIcon.SUN, WeatherIcons.of(1))
        assertEquals(WeatherIcon.PARTLY_NIGHT, WeatherIcons.of(3, night = true))
        assertEquals(WeatherIcon.THUNDER, WeatherIcons.of(15))
        assertEquals(WeatherIcon.RAIN, WeatherIcons.of(8))
        assertEquals(WeatherIcon.FOG, WeatherIcons.of(24))
        assertEquals(WeatherIcon.THUNDER, WeatherIcons.of(99, "午後雷陣雨"))
    }
}
