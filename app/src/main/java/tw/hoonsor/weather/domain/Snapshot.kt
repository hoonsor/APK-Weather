package tw.hoonsor.weather.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** 逐 3 小時（或逐時）的一個點 */
data class HourPoint(
    val time: LocalDateTime,
    val temp: Int,
    val feels: Int?,
    val rain: Int?,
    val code: Int,
    val weather: String,
    val humidity: Int? = null,
    val beaufort: Int? = null,
    val windDir: String? = null,
    val comfort: String? = null,
)

/** 某地點一次完整的天氣資料（存成快取檔，小工具、主畫面、Agent API 共用） */
data class Snapshot(
    val kind: PlaceKind,
    val town: Town,
    val updatedAt: LocalDateTime,
    val source: String,            // "cwa" 或 "open-meteo"
    val hours: List<HourPoint>,
    val days: List<DayForecast>,
    val airNow: AirReading? = null,
    val airTomorrow: AirReading? = null,
) {
    fun hourAt(t: LocalDateTime): HourPoint? = hours.minByOrNull { abs(ChronoUnit.MINUTES.between(it.time, t)) }

    fun now(t: LocalDateTime = LocalDateTime.now()): NowWeather? {
        val h = hourAt(t) ?: return null
        val today = day(t.toLocalDate())
        return NowWeather(t, h.temp, h.feels ?: h.temp, h.weather, h.code, h.humidity, h.rain ?: today?.let { if (t.hour in 6..17) it.rainDay else it.rainNight })
    }

    fun day(d: LocalDate): DayForecast? = days.firstOrNull { it.date == d }

    /** 今天的最高／最低：優先用逐時資料（7 日資料的今天有時只剩晚上那段） */
    fun todayRange(t: LocalDateTime = LocalDateTime.now()): Pair<Int, Int>? {
        val d = t.toLocalDate()
        val hs = hours.filter { it.time.toLocalDate() == d }.map { it.temp }
        val day = day(d)
        val lo = listOfNotNull(hs.minOrNull(), day?.tempMin).minOrNull() ?: return null
        val hi = listOfNotNull(hs.maxOrNull(), day?.tempMax).maxOrNull() ?: return null
        return lo to hi
    }

    fun ageMinutes(t: LocalDateTime = LocalDateTime.now()) = ChronoUnit.MINUTES.between(updatedAt, t)
}
