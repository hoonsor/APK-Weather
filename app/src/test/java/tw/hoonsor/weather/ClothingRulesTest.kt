package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.ClothingItem.FOLDING_UMBRELLA
import tw.hoonsor.weather.domain.ClothingItem.HEAVY_COAT
import tw.hoonsor.weather.domain.ClothingItem.LIGHT_JACKET
import tw.hoonsor.weather.domain.ClothingItem.LONG_SLEEVE
import tw.hoonsor.weather.domain.ClothingItem.MASK
import tw.hoonsor.weather.domain.ClothingItem.TEE
import tw.hoonsor.weather.domain.ClothingItem.UMBRELLA
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.DayForecast
import java.time.LocalDate

class ClothingRulesTest {
    private fun day(lo: Int, hi: Int, rd: Int? = 0, rn: Int? = 0, uv: Int? = 3, wind: Int? = 2) =
        DayForecast(LocalDate.of(2026, 11, 11), "多雲", 4, lo, hi, lo, hi, rd, rn, uv, wind)

    @Test fun bands() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), listOf(11, 12, 15, 16, 19, 20, 26, 27).map(ClothingRules::band))
    }

    @Test fun teeWithLightJacketAndNightUmbrella() {
        val a = ClothingRules.advise(day(17, 24, rd = 20, rn = 40))
        assertEquals(listOf(TEE, LIGHT_JACKET, FOLDING_UMBRELLA), a.items)
        assertEquals("建議短袖加薄外套，晚上帶把折傘。", a.sentence)
    }

    @Test fun bigSpreadAndAllDayRain() {
        val a = ClothingRules.advise(day(14, 25, rd = 60, rn = 70))
        assertEquals("早晚溫差大，建議短袖加外套，記得帶傘。", a.sentence)
        assertTrue(UMBRELLA in a.items)
    }

    @Test fun sameBand() {
        assertEquals("建議短袖、穿透氣一點。", ClothingRules.advise(day(27, 33)).sentence)
        assertEquals("建議厚外套或羽絨衣。", ClothingRules.advise(day(6, 11)).sentence)
        assertEquals(listOf(LONG_SLEEVE, HEAVY_COAT), ClothingRules.advise(day(6, 11)).items)
    }

    @Test fun uvWindMask() {
        val a = ClothingRules.advise(day(22, 30, uv = 9, wind = 6), aqi = 120)
        assertTrue(a.sentence.contains("紫外線很強，記得防曬"))
        assertTrue(a.sentence.contains("風大，穿防風外套"))
        assertTrue(MASK in a.items)
        assertFalse(MASK in ClothingRules.advise(day(22, 30), aqi = 100).items)
    }

    @Test fun shiftForColdSensitive() {
        // 怕冷 +2：體感 20–25 當成 18–23 → 短袖加薄外套
        assertEquals("建議短袖加薄外套。", ClothingRules.advise(day(20, 25), shift = 2).sentence)
        assertEquals("建議短袖。", ClothingRules.advise(day(20, 25)).sentence)
    }

    @Test fun missingRainIsSilent() {
        assertEquals("建議短袖。", ClothingRules.advise(day(21, 25, rd = null, rn = null)).sentence)
    }
}
