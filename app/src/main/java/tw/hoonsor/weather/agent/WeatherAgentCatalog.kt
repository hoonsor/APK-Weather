package tw.hoonsor.weather.agent

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.agent.HoonsorAgent

/**
 * 動作清單（describe）。純資料，JVM 測試會跟 docs/agent-manifest.json 比對。
 * 協定與撰寫規則見語音總控的 docs/AGENT_PROTOCOL.md；本 APP 的行為見 docs/AGENT_API.md。
 */
object WeatherAgentCatalog {
    const val APP = "倬的天氣"
    const val PACKAGE = "tw.hoonsor.weather"
    const val DESCRIPTION = "台灣天氣、穿著建議、空氣品質警戒、颱風對家與學校的影響。資料來自中央氣象署與環境部，判斷在手機上用規則完成。"

    private fun s(desc: String, enum: List<String>? = null) = JSONObject().put("type", "string")
        .apply { if (enum != null) put("enum", JSONArray(enum)) }.put("description", desc)
    private fun b(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
    private fun obj(vararg p: Pair<String, JSONObject>, required: List<String> = emptyList()) = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { p.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))

    private val place3 = s("查哪裡，預設 current（目前位置）", listOf("current", "home", "school"))

    val actions: List<JSONObject> = listOf(
        HoonsorAgent.action(
            "weather_tomorrow", "明天天氣",
            "唸明天的天氣、體感溫度、降雨機率與穿著建議；有空品警戒或颱風時一起唸。沒指定地點時，明天要上班就查學校，其他日子查家。只問現在天氣用 weather_now，問一週用 weather_week。",
            "read",
            obj(
                "place" to s("查哪裡：auto＝依明天是否上班自動選（預設）、current＝目前位置、home＝家、school＝學校", listOf("auto", "current", "home", "school")),
                "includeAir" to b("有空品警戒時一起唸，預設 true"),
                "includeTyphoon" to b("有颱風時一起唸，預設 true"),
            ),
            listOf("明天天氣怎樣", "明天要帶傘嗎", "明天穿什麼", "明天學校那邊會不會下雨", "明天家裡那邊冷不冷"),
        ),
        HoonsorAgent.action(
            "weather_now", "現在天氣", "唸目前溫度、體感、天氣狀況與接下來幾小時會不會下雨。不含明天預報。", "read",
            obj("place" to place3),
            listOf("現在外面幾度", "等一下會下雨嗎", "學校現在天氣怎樣"),
        ),
        HoonsorAgent.action(
            "weather_week", "一週天氣", "唸未來 7 天的天氣概況、高低溫與降雨機率較高的日子。", "read",
            obj("place" to place3),
            listOf("這禮拜天氣怎樣", "週末會不會下雨", "下週會變冷嗎"),
        ),
        HoonsorAgent.action(
            "air_quality", "空氣品質", "唸空氣品質指標 AQI、PM2.5 與建議（戴口罩、減少戶外活動）。today 用附近測站即時值，tomorrow 用空品區預報。", "read",
            obj("place" to place3, "day" to s("today＝現在、tomorrow＝明天預報，預設 today", listOf("today", "tomorrow"))),
            listOf("現在空氣好不好", "明天 PM2.5 高不高", "要不要戴口罩"),
        ),
        HoonsorAgent.action(
            "typhoon_status", "颱風動態",
            "唸目前颱風或熱帶性低氣壓的名稱、強度、預估最接近台灣的時間、警報狀態，以及對家和學校的影響程度與停班停課公告。沒有颱風時會說目前沒有。",
            "read", obj(),
            listOf("颱風會不會來", "這個颱風什麼時候最近", "明天會不會放颱風假", "學校那邊會進暴風圈嗎"),
        ),
        HoonsorAgent.action(
            "place_list", "常用地點", "列出設定好的家、學校所在鄉鎮，以及目前位置判斷到的鄉鎮。", "read", obj(),
            listOf("天氣的家設在哪", "我設定的地點有哪些", "現在定位在哪一區"),
        ),
        HoonsorAgent.action(
            "place_set", "設定地點", "把家或學校設成某個鄉鎮市區（例如「新北市板橋區」）。只到鄉鎮層級，不存門牌。可復原。", "reversible",
            obj(
                "which" to s("要設定家還是學校", listOf("home", "school")),
                "town" to s("縣市＋鄉鎮市區，例如「臺中市西屯區」；只講鄉鎮時會比對，重名會反問"),
                "useCurrent" to b("true＝直接用目前位置所在的鄉鎮，這時可以不給 town"),
                required = listOf("which"),
            ),
            listOf("把學校設成現在這裡", "天氣的家改成板橋區", "學校地點設成桃園市中壢區"),
        ),
        HoonsorAgent.action(
            "backup_export", "匯出備份", "把天氣 APP 的設定與常用地點匯出成 zip，存到下載資料夾並開啟分享。", "reversible", obj(),
            listOf("備份天氣設定", "匯出天氣 APP 的備份"),
        ),
        HoonsorAgent.action(
            "backup_import", "匯入備份", "開啟檔案選擇畫面，從備份還原設定。會覆蓋目前設定，一定先確認；匯入前自動備份，可以復原。", "destructive",
            obj("mode" to s("merge＝合併、replace＝取代，預設 replace", listOf("merge", "replace"))),
            listOf("還原天氣備份", "從檔案匯入天氣設定"),
        ),
    )

    fun describe(version: String): JSONObject = HoonsorAgent.describe(APP, PACKAGE, version, DESCRIPTION, actions)
}
