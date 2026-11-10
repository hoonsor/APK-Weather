package tw.hoonsor.weather.domain

import kotlin.math.max

/** 穿著圖示（小工具、7 日、明天卡共用） */
enum class ClothingItem(val label: String) {
    TEE("短袖"), LONG_SLEEVE("長袖"), LIGHT_JACKET("薄外套"), JACKET("外套"), HEAVY_COAT("厚外套"),
    UMBRELLA("帶傘"), FOLDING_UMBRELLA("折傘"), SUNSCREEN("防曬"), WINDBREAKER("防風外套"), MASK("口罩"),
}

data class ClothingAdvice(val items: List<ClothingItem>, val sentence: String)

/**
 * 穿著規則（F06，docs/FEATURES.md「穿著建議規則草案」）。
 * 白天穿什麼看體感最高溫，早晚加什麼看體感最低溫。
 * [shift]：怕冷填正數（例 2＝當成冷 2 度），怕熱填負數。
 */
object ClothingRules {
    /** 體感溫度級距：0＝<12、1＝12–16、2＝16–20、3＝20–26、4＝>26 */
    fun band(feels: Int): Int = when {
        feels < 12 -> 0
        feels < 16 -> 1
        feels < 20 -> 2
        feels <= 26 -> 3
        else -> 4
    }

    private val single = listOf("厚外套或羽絨衣", "外套加長袖", "長袖或薄外套", "短袖", "短袖、穿透氣一點")
    private val outerName = listOf("厚外套", "外套", "薄外套", "薄外套")
    private val outerItem = listOf(ClothingItem.HEAVY_COAT, ClothingItem.JACKET, ClothingItem.LIGHT_JACKET, ClothingItem.LIGHT_JACKET)

    fun advise(day: DayForecast, aqi: Int? = null, shift: Int = 0): ClothingAdvice {
        val hi = day.feelsMax - shift
        val lo = day.feelsMin - shift
        val dayBand = band(hi)
        val nightBand = band(lo)
        val items = mutableListOf<ClothingItem>()
        val parts = mutableListOf<String>()

        val wear = if (dayBand <= nightBand) {
            items += when (dayBand) {
                0 -> listOf(ClothingItem.LONG_SLEEVE, ClothingItem.HEAVY_COAT)
                1 -> listOf(ClothingItem.LONG_SLEEVE, ClothingItem.JACKET)
                2 -> listOf(ClothingItem.LONG_SLEEVE, ClothingItem.LIGHT_JACKET)
                else -> listOf(ClothingItem.TEE)
            }
            single[dayBand]
        } else {
            val top = if (dayBand >= 3) ClothingItem.TEE else ClothingItem.LONG_SLEEVE
            items += top
            items += outerItem[nightBand]
            "${top.label}加${outerName[nightBand]}"
        }
        val spread = day.feelsMax - day.feelsMin
        parts += (if (spread >= 8) "早晚溫差大，建議" else "建議") + wear

        rainPhrase(day)?.let { (item, text) -> items += item; parts += text }
        if ((day.uvIndex ?: 0) >= 8) { items += ClothingItem.SUNSCREEN; parts += "紫外線很強，記得防曬" }
        if ((day.beaufortMax ?: 0) >= 6) { items += ClothingItem.WINDBREAKER; parts += "風大，穿防風外套" }
        if ((aqi ?: 0) >= Aqi.ALERT) items += ClothingItem.MASK
        return ClothingAdvice(items.distinct(), parts.joinToString("，") + "。")
    }

    /** 降雨：≥50 帶傘、≥30 折傘；只有白天或晚上達標時說是哪一段 */
    private fun rainPhrase(d: DayForecast): Pair<ClothingItem, String>? {
        val dd = d.rainDay ?: 0
        val nn = d.rainNight ?: 0
        val top = max(dd, nn)
        val (item, verb, limit) = when {
            top >= 50 -> Triple(ClothingItem.UMBRELLA, "記得帶傘", 50)
            top >= 30 -> Triple(ClothingItem.FOLDING_UMBRELLA, "帶把折傘", 30)
            else -> return null
        }
        val period = when {
            dd >= limit && nn >= limit -> ""
            dd >= limit -> "白天"
            else -> "晚上"
        }
        return item to period + verb
    }
}
