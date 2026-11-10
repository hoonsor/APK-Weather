package tw.hoonsor.weather.domain

import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 颱風對某地的影響（F11，規則見 docs/AGENT_API.md「typhoon_status」）：
 * - 可能直接影響：任一預報點與該地距離 ≤ 七級風半徑＋70% 機率半徑
 * - 外圍影響：距離 < 七級風半徑＋300 公里，或該縣市在陸上警報範圍
 * - 影響低：其他
 */
object TyphoonImpact {
    enum class Level(val label: String) { DIRECT("可能直接影響"), OUTER("外圍影響"), LOW("影響低") }

    const val OUTER_MARGIN_KM = 300.0
    /** 超過這個時數的預報點，播報時加「路徑還不確定」（F12） */
    const val UNCERTAIN_HOURS = 120L

    data class Result(val level: Level, val minDistanceKm: Double, val at: LocalDateTime?)

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun assess(t: Typhoon, town: Town): Result {
        var level = if (town.county in t.landWarningCounties) Level.OUTER else Level.LOW
        var best = Double.MAX_VALUE
        var bestAt: LocalDateTime? = null
        for (p in t.track) {
            val d = distanceKm(p.lat, p.lon, town.lat, town.lon)
            if (d < best) { best = d; bestAt = p.time }
            val r7 = (p.radius7Km ?: 0).toDouble()
            val r70 = (p.radius70Km ?: 0).toDouble()
            if (r7 > 0 && d <= r7 + r70) level = Level.DIRECT
            else if (level == Level.LOW && d < r7 + OUTER_MARGIN_KM) level = Level.OUTER
        }
        return Result(level, best, bestAt)
    }

    /** 台灣本島參考點（北、中、南、東、恆春），用來估「最接近台灣」的時間 */
    private val taiwan = listOf(25.04 to 121.53, 24.15 to 120.67, 22.63 to 120.30, 23.98 to 121.60, 22.00 to 120.75)

    /** 預估最接近台灣的預報點；沒有路徑時回 null */
    fun closestToTaiwan(t: Typhoon): Pair<TrackPoint, Double>? =
        t.track.map { p -> p to taiwan.minOf { (la, lo) -> distanceKm(p.lat, p.lon, la, lo) } }.minByOrNull { it.second }
}
