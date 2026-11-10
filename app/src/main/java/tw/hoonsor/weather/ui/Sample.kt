package tw.hoonsor.weather.ui

import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.TrackPoint
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.Typhoon
import java.time.LocalDate
import java.time.LocalDateTime

/** 範例資料（和設計稿相同）：v0.1.0 骨架畫面、LAB 預覽與測試用。颱風是虛構的 */
object Sample {
    val town = Town("桃園市", "中壢區", 24.96, 121.22)
    val home = Town("桃園市", "桃園區", 24.99, 121.30)

    fun week(start: LocalDate = LocalDate.now().plusDays(1)): List<DayForecast> = listOf(
        Triple("多雲時晴", 3, listOf(18, 25, 17, 24, 20, 40)),
        Triple("晴", 1, listOf(20, 29, 20, 30, 10, 10)),
        Triple("晴", 1, listOf(21, 30, 21, 31, 10, 10)),
        Triple("多雲時晴", 3, listOf(22, 27, 22, 28, 30, 20)),
        Triple("短暫雨", 8, listOf(19, 24, 18, 23, 70, 60)),
        Triple("陰短暫雨", 11, listOf(17, 21, 16, 20, 60, 50)),
        Triple("多雲", 4, listOf(16, 22, 15, 21, 20, 20)),
    ).mapIndexed { i, (w, code, n) ->
        DayForecast(start.plusDays(i.toLong()), w, code, n[0], n[1], n[2], n[3], n[4], n[5], uvIndex = 6, beaufortMax = 3)
    }

    val air = AirReading(112, 34.0, "中壢測站", isForecast = false)

    fun typhoon(now: LocalDateTime = LocalDateTime.now()) = Typhoon(
        name = "銀杏（範例）", kind = "中度颱風", position = "恆春東南方約 620 公里",
        track = listOf(
            TrackPoint(now.plusHours(24), 20.2, 125.0, 200, 120),
            TrackPoint(now.plusHours(48), 22.6, 122.4, 200, 180),
            TrackPoint(now.plusHours(72), 24.8, 121.0, 180, 250),
        ),
        warning = Typhoon.Warning.SEA,
    )
}
