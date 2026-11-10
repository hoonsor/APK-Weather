package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.ClothingItem
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.HourPoint
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.WeatherIcon
import tw.hoonsor.weather.widget.WidgetData
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetDataTest {
    private val now = LocalDateTime.of(2026, 11, 10, 20, 40)
    private val town = Town("桃園市", "中壢區", 24.96, 121.22)

    private fun snap(kind: PlaceKind, aqiTomorrow: Int? = null, updated: LocalDateTime = now.minusMinutes(10)): Snapshot {
        val hours = (0..8).map { HourPoint(now.toLocalDate().atTime(12, 0).plusHours(it * 3L), 19 + (it % 3), 18, 20, 3, "多雲時晴", 80) }
        val days = (0..5).map { i ->
            DayForecast(LocalDate.of(2026, 11, 10).plusDays(i.toLong()), "多雲時晴", 3, 16 + i, 21 + i, 15 + i, 20 + i, 20, 40, 5, 2)
        }
        return Snapshot(kind, town, updated, "cwa", hours, days, AirReading(60, 15.0, "中壢測站", false), aqiTomorrow?.let { AirReading(it, null, "北部空品區", true) })
    }

    @Test fun buildA_and_C() {
        val d = WidgetData.build(snap(PlaceKind.CURRENT), PlaceKind.SCHOOL, snap(PlaceKind.SCHOOL, aqiTomorrow = 112), typhoonActive = false, shift = 0, at = now)
        assertEquals("中壢區", d.town)
        assertEquals(WeatherIcon.PARTLY_NIGHT, d.icon)               // 晚上
        assertEquals(4, d.days.size)
        assertEquals("週三", d.days[0].name)
        assertEquals("明天 · 學校", d.tomorrowLabel)
        assertEquals(17, d.tomorrowLo); assertEquals(22, d.tomorrowHi)
        assertTrue(ClothingItem.MASK in d.tomorrowItems)
        assertEquals("空品橘", d.badge)
        assertFalse(d.stale)
        assertEquals(d, WidgetData.fromJson(d.toJson()))
    }

    @Test fun typhoonBadgeWinsAndStale() {
        val d = WidgetData.build(snap(PlaceKind.CURRENT, updated = now.minusHours(5)), PlaceKind.HOME, null, typhoonActive = true, shift = 0, at = now)
        assertEquals("颱風", d.badge)
        assertTrue(d.stale)
        assertEquals("明天 · 中壢區", d.tomorrowLabel)    // 沒有明天地點的快照時，退回目前位置
    }

    @Test fun emptyWhenNothing() {
        assertEquals(WidgetData.EMPTY, WidgetData.build(null, PlaceKind.HOME, null, false, 0, now))
    }
}
