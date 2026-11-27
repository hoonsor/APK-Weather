package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.PlaceMatch
import tw.hoonsor.weather.domain.SavedPlace
import tw.hoonsor.weather.domain.Town

/** 語音說的地點名稱 → 設定裡的地點（v0.10.0 placeName） */
class PlaceMatchTest {
    private val home = Town("桃園市", "大園區", 25.06, 121.2)
    private val school = Town("桃園市", "中壢區", 24.96, 121.22)
    private val grandma = SavedPlace("a1", "阿嬤家", Town("臺中市", "西屯區", 24.18, 120.62))
    private val office = SavedPlace("b2", "西屯區", Town("臺中市", "西屯區", 24.18, 120.62))
    private val tainan = SavedPlace("c3", "東區", Town("臺南市", "東區", 22.98, 120.22))
    private val hsinchu = SavedPlace("d4", "東區", Town("新竹市", "東區", 24.8, 120.97))

    private fun refs(name: String, vararg extras: SavedPlace) = PlaceMatch.match(name, extras.toList(), home, school).map { it.ref }

    @Test fun exactLabelAndTails() {
        assertEquals(listOf("x:a1"), refs("阿嬤家", grandma))
        assertEquals(listOf("x:a1"), refs("阿嬤家那邊", grandma))
        assertEquals(listOf("x:a1"), refs(" 阿嬤家的天氣 ", grandma))
    }

    @Test fun homeAndSchoolWords() {
        assertEquals(listOf("home"), refs("家裡", grandma))
        assertEquals(listOf("school"), refs("學校那邊", grandma))
    }

    @Test fun partialLabel() {
        assertEquals(listOf("x:a1"), refs("阿嬤", grandma))
    }

    @Test fun townName() {
        assertEquals(listOf("x:a1"), refs("西屯", grandma))
        assertEquals(listOf("x:a1"), refs("台中市西屯區", grandma))   // 台／臺
        assertEquals(listOf("home"), refs("大園區", grandma))
    }

    @Test fun exactLabelBeatsTown() {
        // 「西屯區」完全等於 office 的名稱，不會再跟阿嬤家（也在西屯區）一起列出來
        assertEquals(listOf("x:b2"), refs("西屯區", grandma, office))
    }

    @Test fun ambiguousAndMissing() {
        assertEquals(2, refs("東區", tainan, hsinchu).size)
        assertTrue(refs("外婆家", grandma).isEmpty())
        assertTrue(refs("", grandma).isEmpty())
    }
}
