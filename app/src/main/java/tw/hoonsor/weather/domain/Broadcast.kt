package tw.hoonsor.weather.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 播報句組裝（Agent API 的 summary 會被語音總控直接唸出）。
 * 語氣範例見 docs/FEATURES.md「晚間播報範例」。
 */
object Broadcast {
    private val weekday = mapOf(
        DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二", DayOfWeek.WEDNESDAY to "三", DayOfWeek.THURSDAY to "四",
        DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六", DayOfWeek.SUNDAY to "日",
    )

    fun weekdayName(d: LocalDate) = "星期" + weekday.getValue(d.dayOfWeek)
    fun dateText(d: LocalDate) = "${d.monthValue} 月 ${d.dayOfMonth} 日${weekdayName(d)}"

    /** 「星期五清晨」：給颱風最接近時間用 */
    fun whenText(t: LocalDateTime, today: LocalDate): String {
        val day = when (t.toLocalDate()) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.plusDays(2) -> "後天"
            else -> weekdayName(t.toLocalDate())
        }
        val part = when (t.hour) {
            in 0..4 -> "凌晨"
            in 5..7 -> "清晨"
            in 8..10 -> "上午"
            in 11..12 -> "中午"
            in 13..16 -> "下午"
            in 17..18 -> "傍晚"
            else -> "晚上"
        }
        return day + part
    }

    fun placeText(kind: PlaceKind, town: Town) = when (kind) {
        PlaceKind.SCHOOL -> "學校在${town.name}"
        PlaceKind.HOME -> "家裡${town.name}"
        PlaceKind.CURRENT -> town.name
    }

    private fun rainText(d: DayForecast): String {
        val dd = d.rainDay
        val nn = d.rainNight
        return when {
            dd == null && nn == null -> ""
            (dd ?: -1) >= (nn ?: -1) -> "白天降雨機率 $dd%"
            else -> "晚上降雨機率 $nn%"
        }
    }

    /** weather_tomorrow 的天氣部分（不含空品、颱風） */
    fun tomorrow(d: DayForecast, kind: PlaceKind, town: Town, advice: ClothingAdvice): String {
        val rain = rainText(d)
        val head = "明天 ${dateText(d.date)}，${placeText(kind, town)}，白天${d.weather}，體感 ${d.feelsMin} 到 ${d.feelsMax} 度" +
            (if (rain.isNotEmpty()) "，$rain" else "") + "。"
        return head + advice.sentence
    }

    /** 空品警戒句；未達 101 回 null（F09 平常不唸） */
    fun airAlert(air: AirReading, tomorrow: Boolean): String? {
        if (!Aqi.isAlert(air.aqi)) return null
        val lv = Aqi.level(air.aqi)
        return "${if (tomorrow) "明天" else "現在"}空氣品質${lv.color}，${lv.effect}，${lv.advice}。"
    }

    fun air(air: AirReading): String {
        val lv = Aqi.level(air.aqi)
        val pm = air.pm25?.let { "，PM2.5 ${it.roundToInt()}" } ?: ""
        return if (Aqi.isAlert(air.aqi)) "${air.area}${if (air.isForecast) "明天預報" else "現在"} AQI ${air.aqi}，${lv.color}$pm，${lv.effect}，${lv.advice}。"
        else "${air.area}${if (air.isForecast) "明天預報" else "現在"} AQI ${air.aqi}，${lv.color}，空氣品質${lv.effect}，${lv.advice}。"
    }

    /**
     * 颱風句（F10～F13）。[places] 依序是（稱呼, 影響），例如 ("學校", DIRECT)。
     * [short]＝接在明天天氣後面的精簡版。
     */
    fun typhoon(
        t: Typhoon,
        closestAt: LocalDateTime?,
        now: LocalDateTime,
        places: List<Pair<String, TyphoonImpact.Level>>,
        offWork: String?,
        short: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append(if (short) "另外，" else "").append("${t.name}${t.kind}目前在${t.position}")
        if (closestAt != null) {
            sb.append("，預估${whenText(closestAt, now.toLocalDate())}最接近台灣")
        }
        sb.append("，${t.warning.label}。")
        if (places.isNotEmpty()) {
            val (worst, rest) = places.partition { it.second == places.minBy { p -> p.second.ordinal }.second }
            sb.append("依目前路徑，")
            sb.append(worst.joinToString("和") { it.first }).append(impactVerb(worst.first().second))
            rest.forEach { sb.append("，${it.first}是${it.second.label}") }
            sb.append("。")
        }
        val far = closestAt != null && java.time.Duration.between(now, closestAt).toHours() > TyphoonImpact.UNCERTAIN_HOURS
        sb.append(if (far) "還在 5 天以後，路徑還不確定。" else if (short) "路徑還可能改變，會再更新。" else "")
        if (!offWork.isNullOrBlank()) sb.append(offWork.trimEnd('。')).append("。")
        return sb.toString()
    }

    private fun impactVerb(l: TyphoonImpact.Level) = when (l) {
        TyphoonImpact.Level.DIRECT -> "可能進入暴風圈"
        TyphoonImpact.Level.OUTER -> "是外圍影響"
        TyphoonImpact.Level.LOW -> "影響不大"
    }

    fun noTyphoon() = "目前沒有颱風或熱帶性低氣壓。"
}
