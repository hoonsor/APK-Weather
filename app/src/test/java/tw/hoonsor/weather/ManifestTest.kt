package tw.hoonsor.weather

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.agent.WeatherAgentCatalog
import java.io.File

/** docs/agent-manifest.json 必須與 describe 完全一致（版本號、規劃中標記除外）；語音總控 AGENT_PROTOCOL.md 第 6 節 */
class ManifestTest {
    private val file = listOf(File("../docs/agent-manifest.json"), File("docs/agent-manifest.json")).first { it.exists() }

    @Test fun manifestMatchesDescribe() {
        val actual = WeatherAgentCatalog.describe("x").apply { remove("version") }
        val expected = JSONObject(file.readText(Charsets.UTF_8)).apply { remove("version"); remove("status") }
        assertTrue("docs/agent-manifest.json 與 WeatherAgentCatalog 不一致", expected.similar(actual))
    }

    @Test fun actionNames() {
        val names = WeatherAgentCatalog.actions.map { it.getString("name") }
        assertEquals(
            listOf("weather_tomorrow", "weather_now", "weather_week", "air_quality", "typhoon_status", "place_list", "place_set", "backup_export", "backup_import"),
            names,
        )
        assertTrue(names.any { it.endsWith("_list") })
        assertEquals("destructive", WeatherAgentCatalog.actions.first { it.getString("name") == "backup_import" }.getString("risk"))
    }
}
