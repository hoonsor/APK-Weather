package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.TrackPoint
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.domain.TyphoonImpact
import tw.hoonsor.weather.domain.TyphoonImpact.Level
import java.time.LocalDateTime

class TyphoonImpactTest {
    private val t0 = LocalDateTime.of(2026, 11, 10, 20, 0)
    private val zhongli = Town("桃園市", "中壢區", 24.96, 121.22)
    private val hengchun = Town("屏東縣", "恆春鎮", 22.00, 120.75)

    private fun ty(vararg p: TrackPoint, land: Set<String> = emptySet()) =
        Typhoon("測試颱風", "中度颱風", "某處", p.toList(), Typhoon.Warning.SEA, land)

    @Test fun distance() {
        // 台北到高雄約 300 公里
        val d = TyphoonImpact.distanceKm(25.04, 121.53, 22.63, 120.30)
        assertTrue("$d", d in 280.0..310.0)
    }

    @Test fun directWhenWithinRadiusPlusProbability() {
        val t = ty(TrackPoint(t0.plusHours(48), 22.6, 122.4, 150, 120))
        val dh = TyphoonImpact.assess(t, hengchun)
        val dz = TyphoonImpact.assess(t, zhongli)
        assertEquals("恆春 ${dh.minDistanceKm}", Level.DIRECT, dh.level)
        assertEquals("中壢 ${dz.minDistanceKm}", Level.OUTER, dz.level)
    }

    @Test fun lowWhenFar() {
        val t = ty(TrackPoint(t0.plusHours(72), 18.0, 130.0, 100, 200))
        assertEquals(Level.LOW, TyphoonImpact.assess(t, zhongli).level)
    }

    @Test fun landWarningCountyIsAtLeastOuter() {
        val t = ty(TrackPoint(t0.plusHours(72), 18.0, 130.0, 100, 200), land = setOf("桃園市"))
        assertEquals(Level.OUTER, TyphoonImpact.assess(t, zhongli).level)
    }

    @Test fun missingRadiusNeverDirect() {
        val t = ty(TrackPoint(t0, 24.96, 121.22, null, null))
        assertEquals(Level.OUTER, TyphoonImpact.assess(t, zhongli).level)
    }

    @Test fun closestPointToTaiwan() {
        val t = ty(
            TrackPoint(t0.plusHours(24), 18.0, 128.0, 100, 100),
            TrackPoint(t0.plusHours(48), 22.0, 122.0, 100, 100),
            TrackPoint(t0.plusHours(72), 26.0, 126.0, 100, 100),
        )
        assertEquals(t0.plusHours(48), TyphoonImpact.closestToTaiwan(t)!!.first.time)
    }
}
