package tw.hoonsor.weather.agent

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.agent.AgentRequest
import tw.hoonsor.agent.HoonsorAgent
import tw.hoonsor.agent.HoonsorAgentProvider
import tw.hoonsor.weather.BuildConfig
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.backup.Backup
import tw.hoonsor.weather.data.AppError
import tw.hoonsor.weather.data.Report
import tw.hoonsor.weather.data.Vault
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.ui.MainActivity
import tw.hoonsor.weather.widget.WeatherWidgets

/**
 * Agent API（語音總控呼叫）。所有資料都經過 Repository；summary 由規則組好，大腦直接唸。
 * 行為細節見 docs/AGENT_API.md。
 */
class AgentProvider : HoonsorAgentProvider() {
    override fun onCreate(): Boolean {
        context?.let { Graph.init(it) }   // ContentProvider 比 Application.onCreate 早建立
        return true
    }

    override fun describe(): JSONObject = WeatherAgentCatalog.describe(BuildConfig.VERSION_NAME)
        .put("package", context?.packageName ?: WeatherAgentCatalog.PACKAGE)

    /** 要連網的動作給 15 秒（協定上限 30 秒） */
    override fun timeoutMs(action: String): Long = if (action in setOf("place_list", "backup_export", "backup_import")) 5_000L else 15_000L

    override fun health(): JSONObject {
        val issues = JSONArray()
        val ctx = context ?: return JSONObject().put("ok", false).put("issues", issues.put("APP 尚未啟動"))
        Graph.init(ctx)
        if (!Graph.vault.has(Vault.CWA)) issues.put("還沒設定氣象署授權碼（目前用 Open-Meteo 備援，查不到颱風）")
        if (!Graph.vault.has(Vault.MOENV)) issues.put("還沒設定環境部金鑰（查不到空品）")
        if (!Graph.repo.locator.hasPermission()) issues.put("天氣 APP 沒有定位權限")
        if (Graph.settings.place(PlaceKind.SCHOOL) == null) issues.put("還沒設定學校的位置")
        if (Graph.settings.place(PlaceKind.HOME) == null) issues.put("還沒設定家的位置")
        Graph.settings.lastError?.let { issues.put("上次更新失敗：$it") }
        return JSONObject().put("ok", issues.length() == 0).put("issues", issues)
    }

    override fun execute(request: AgentRequest): JSONObject = runBlocking {
        val ctx = context!!
        Graph.init(ctx)
        val repo = Graph.repo
        val p = request.params
        try {
            when (request.action) {
                "weather_tomorrow" -> ok(repo.tomorrowReport(p.optString("place", "auto").ifBlank { "auto" },
                    p.optBoolean("includeAir", repo.settings.broadcastAir), p.optBoolean("includeTyphoon", repo.settings.broadcastTyphoon)))
                "weather_now" -> ok(repo.nowReport(p.optString("place").ifBlank { null }))
                "weather_week" -> ok(repo.weekReport(p.optString("place").ifBlank { null }))
                "air_quality" -> ok(repo.airReport(p.optString("place").ifBlank { null }, p.optString("day").ifBlank { null }))
                "typhoon_status" -> ok(repo.typhoonReport())
                "place_list" -> ok(repo.placeListReport())
                "place_set" -> placeSet(p, request.dryRun)
                "backup_export" -> {
                    if (request.dryRun) return@runBlocking HoonsorAgent.ok("會把天氣設定備份到下載資料夾。")
                    val name = Backup.saveToDownloads(ctx)
                    HoonsorAgent.ok("天氣設定已經備份到下載資料夾，檔名 $name。", JSONObject().put("file", name))
                }
                "backup_import" -> {
                    val token = "import-" + (p.optString("mode", "replace").ifBlank { "replace" })
                    if (request.confirmToken != token) return@runBlocking HoonsorAgent.needsConfirmation("匯入備份會覆蓋目前的天氣設定（匯入前會先自動備份），確定要開啟檔案選擇畫面嗎？", token)
                    val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_SECTION, "import").putExtra(MainActivity.EXTRA_IMPORT_MODE, p.optString("mode", "replace"))
                    HoonsorAgent.fail(HoonsorAgent.NEEDS_UI, "請在天氣 APP 選擇備份檔。", JSONObject().put("intentUri", intent.toUri(Intent.URI_INTENT_SCHEME)).put("leavesApp", true))
                }
                else -> HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "天氣 APP 沒有這個動作：${request.action}")
            }
        } catch (e: AppError) {
            HoonsorAgent.fail(codeOf(e.code), e.message ?: "天氣 APP 發生錯誤")
        } catch (e: Exception) {
            HoonsorAgent.fail(HoonsorAgent.INTERNAL, "天氣資料現在抓不到，可能是網路不穩，等一下再問我。")
        }
    }

    private fun ok(r: Report) = HoonsorAgent.ok(r.summary, r.data)

    private fun codeOf(c: String) = when (c) {
        "PERMISSION" -> HoonsorAgent.PERMISSION_DENIED
        "NOT_FOUND" -> HoonsorAgent.NOT_FOUND
        "INVALID_PARAMS" -> HoonsorAgent.INVALID_PARAMS
        "AMBIGUOUS" -> HoonsorAgent.AMBIGUOUS
        else -> HoonsorAgent.INTERNAL
    }

    private suspend fun placeSet(p: JSONObject, dryRun: Boolean): JSONObject {
        val repo = Graph.repo
        val kind = when (p.optString("which")) { "home" -> PlaceKind.HOME; "school" -> PlaceKind.SCHOOL; else -> return HoonsorAgent.fail(HoonsorAgent.INVALID_PARAMS, "要設定家還是學校？") }
        val label = if (kind == PlaceKind.HOME) "家" else "學校"
        val town = if (p.optBoolean("useCurrent", false) || p.optString("town").isBlank()) {
            repo.resolveTown(PlaceKind.CURRENT).takeIf { it.county.isNotBlank() } ?: return HoonsorAgent.fail(HoonsorAgent.INTERNAL, "現在判斷不出在哪個鄉鎮，請說出鄉鎮名稱，例如「桃園市中壢區」。")
        } else {
            val hits = repo.searchTown(p.optString("town"))
            when {
                hits.isEmpty() -> return HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "找不到「${p.optString("town")}」，請加上縣市再說一次，例如「桃園市中壢區」。")
                hits.size > 1 -> return HoonsorAgent.ambiguous("「${p.optString("town")}」有好幾個，是哪一個？", hits.map { it.full })
                else -> hits[0]
            }
        }
        val old = repo.settings.place(kind)
        if (dryRun) return HoonsorAgent.ok("會把${label}設成${town.full}。")
        repo.settings.setPlace(kind, town)
        repo.invalidate(kind)
        runCatching { WeatherWidgets.pushAll(context!!) }
        val undo = old?.let { JSONObject().put("which", kind.key).put("town", it.full) }
        return HoonsorAgent.ok("好，${label}設成${town.full}。", JSONObject().put("which", kind.key).put("town", town.full), if (undo != null) "place_set" else null, undo)
    }
}
