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
        // 版面 C 三等分用的資料：明天紫外線、明天空品預報、附近測站現在的 PM2.5
        assertEquals(5, d.tomorrowUv); assertEquals(112, d.tomorrowAqi); assertEquals(15, d.pm25Now)
        // A 版外出穿著：20:40 已經是準備明天，跟明天的建議一樣
        assertEquals(d.tomorrowItems, d.wearItems)
        assertEquals(d, WidgetData.fromJson(d.toJson()))
        // 舊版存的 JSON 沒有新欄位也要讀得出來（升級後第一次更新前）
        val old = org.json.JSONObject(d.toJson()).apply { remove("tUv"); remove("tAqi"); remove("pm") }.toString()
        assertEquals(null, WidgetData.fromJson(old)!!.tomorrowUv)
        val older = org.json.JSONObject(d.toJson()).apply { remove("wItems") }.toString()
        assertEquals(emptyList<ClothingItem>(), WidgetData.fromJson(older)!!.wearItems)
    }

    @Test fun wearUsesTodayBeforeSix() {
        val morning = now.withHour(7)
        val d = WidgetData.build(snap(PlaceKind.CURRENT), PlaceKind.HOME, null, typhoonActive = false, shift = 0, at = morning)
        // 早上看今天（體感 15–20），明天是 16–21：兩者的建議來源不同，至少要有東西
        assertTrue(d.wearItems.isNotEmpty())
    }

    @Test fun placeLabelForSwitchedPlace() {
        // 小工具切到自訂地點：地名顯示自訂名稱，明天標題也用它
        val d = WidgetData.build(snap(PlaceKind.CURRENT), PlaceKind.CURRENT, snap(PlaceKind.CURRENT), typhoonActive = false, shift = 0, at = now, placeLabel = "阿嬤家")
        assertEquals("阿嬤家", d.town)
        assertEquals("明天 · 阿嬤家", d.tomorrowLabel)
    }

    @Test fun uvLevels() {
        assertEquals(tw.hoonsor.weather.domain.Uv.Level.LOW, tw.hoonsor.weather.domain.Uv.level(2))
        assertEquals("中量", tw.hoonsor.weather.domain.Uv.level(5).label)
        assertEquals("高量", tw.hoonsor.weather.domain.Uv.level(7).label)
        assertEquals("過量", tw.hoonsor.weather.domain.Uv.level(10).label)
        assertEquals("危險", tw.hoonsor.weather.domain.Uv.level(11).label)
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
