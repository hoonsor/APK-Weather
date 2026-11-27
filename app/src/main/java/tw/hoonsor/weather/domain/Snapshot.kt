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

    /**
     * 今天「從現在到晚上」的預報（weather_today 用）。
     * 7 日資料的今天有時只剩晚上那段，所以溫度、體感、降雨都用今天剩下的逐時資料補；
     * 已經過去的時段不算（早上 7 點問，凌晨的低溫不唸）。過了 18 點就沒有白天降雨。
     */
    fun todayRest(t: LocalDateTime = LocalDateTime.now()): DayForecast? {
        val d = t.toLocalDate()
        val day = day(d)
        val hs = hours.filter { it.time.toLocalDate() == d && !it.time.isBefore(t.minusMinutes(90)) }
        if (hs.isEmpty() && day == null) return null
        val daytime = hs.filter { it.time.hour in 6..17 }
        val pick = (daytime.ifEmpty { hs }).groupingBy { it.weather }.eachCount().maxByOrNull { it.value }?.key
        val weather = day?.weather ?: pick ?: ""
        val code = day?.weatherCode ?: hs.firstOrNull { it.weather == pick }?.code ?: 0
        val temps = hs.map { it.temp } + listOfNotNull(day?.tempMin, day?.tempMax)
        val feels = hs.map { it.feels ?: it.temp } + listOfNotNull(day?.feelsMin, day?.feelsMax)
        val rainDay = if (t.hour >= 18) null else daytime.mapNotNull { it.rain }.maxOrNull() ?: day?.rainDay
        val rainNight = hs.filter { it.time.hour >= 18 }.mapNotNull { it.rain }.maxOrNull() ?: day?.rainNight
        return DayForecast(
            d, weather, code, temps.min(), temps.max(), feels.min(), feels.max(), rainDay, rainNight,
            day?.uvIndex, listOfNotNull(day?.beaufortMax, hs.mapNotNull { it.beaufort }.maxOrNull()).maxOrNull(),
        )
    }

    fun ageMinutes(t: LocalDateTime = LocalDateTime.now()) = ChronoUnit.MINUTES.between(updatedAt, t)
}
