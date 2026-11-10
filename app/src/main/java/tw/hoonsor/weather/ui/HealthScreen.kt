package tw.hoonsor.weather.ui

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.hoonsor.agent.HoonsorAgent
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.agent.WeatherAgentCatalog
import tw.hoonsor.weather.data.Vault
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.ui.theme.Palette
import tw.hoonsor.weather.widget.Weather41
import tw.hoonsor.weather.widget.WeatherNow22
import tw.hoonsor.weather.widget.WeatherTomorrow22
import tw.hoonsor.weather.work.Notifier

interface HealthActions {
    fun openAppDetails()
    fun requestBatteryExemption()
    fun requestLocation()
    fun requestNotifications()
    fun openSettings()
}

/** 健康檢查（F22，DESIGN.md 第 8 節） */
@Composable
fun HealthScreen(a: HealthActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var widgets by remember { mutableStateOf("檢查中…") }
    var agent by remember { mutableStateOf<Pair<Boolean?, String>>(null to "檢查中…") }
    LaunchedEffect(tick) {
        val m = GlanceAppWidgetManager(ctx)
        val n = m.getGlanceIds(Weather41::class.java).size + m.getGlanceIds(WeatherNow22::class.java).size + m.getGlanceIds(WeatherTomorrow22::class.java).size
        widgets = if (n == 0) "桌面上還沒有小工具" else "桌面上有 $n 個小工具"
        agent = withContext(Dispatchers.IO) { selfCall(ctx, "describe", null) }.let { r ->
            val actions = r?.optJSONArray("actions")?.length()
            if (actions != null) true to "describe 正常，$actions 個動作（語音總控掃得到）" else false to "describe 沒有回應"
        }
    }
    val s = Graph.settings
    val loc = Graph.repo.locator
    val pm = ctx.getSystemService(PowerManager::class.java)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("← 返回", onClick = onBack); Spacer(Modifier.width(10.dp))
            Text("健康檢查", color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Btn("重新檢查") { tick++ }
        }
        Card {
            Check(if (loc.hasBackground()) true else if (loc.hasPermission()) null else false, "定位權限",
                if (loc.hasBackground()) "一律允許" else if (loc.hasPermission()) "只在使用時允許：小工具在背景可能抓不到新位置" else "還沒允許", "允許") { a.requestLocation() }
            Check(Notifier.canNotify(ctx), "通知權限", if (Notifier.canNotify(ctx)) "已允許" else "颱風、降雨提醒需要通知權限", "允許") { a.requestNotifications() }
            Check(pm.isIgnoringBatteryOptimizations(ctx.packageName), "電池最佳化", if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) "已設為不限制" else "ColorOS 可能擋住背景更新，建議設為「不限制」", "設定") { a.requestBatteryExemption() }
            Check(null, "ColorOS 自啟動與關聯啟動", "手機管家 → 權限隱私 → 自啟動：打開「倬的天氣」；關聯啟動也要允許（語音總控要呼叫天氣 APP）。系統不讓 APP 自己檢查，請手動確認。", "應用程式資訊") { a.openAppDetails() }
        }
        Card {
            Check(Graph.vault.has(Vault.CWA), "氣象署授權碼", if (Graph.vault.has(Vault.CWA)) "已設定" else "沒有授權碼：預報用 Open-Meteo 備援，查不到颱風", "設定") { a.openSettings() }
            val moenvLeft = s.moenvKeyDate?.let { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), it.plusYears(1)) }
            Check(if (!Graph.vault.has(Vault.MOENV)) false else if ((moenvLeft ?: 999) < 30) null else true, "環境部金鑰",
                if (!Graph.vault.has(Vault.MOENV)) "沒有金鑰：查不到空品" else moenvLeft?.let { "已設定，約剩 $it 天到期" } ?: "已設定", "設定") { a.openSettings() }
            Check(s.place(PlaceKind.HOME) != null && s.place(PlaceKind.SCHOOL) != null, "家與學校",
                "家：${s.place(PlaceKind.HOME)?.full ?: "未設定"}；學校：${s.place(PlaceKind.SCHOOL)?.full ?: "未設定"}", "設定") { a.openSettings() }
            Check(s.lastError == null, "最近一次更新", (s.lastRefresh?.replace('T', ' ')?.take(16) ?: "還沒更新過") + (s.lastError?.let { "；錯誤：$it" } ?: ""), null) {}
            Check(Graph.repo.calendarInfo().startsWith("已有"), "辦公日曆", Graph.repo.calendarInfo(), null) {}
        }
        Card {
            Check(!widgets.startsWith("桌面上還沒有"), "小工具", widgets, null) {}
            Check(agent.first, "Agent API", agent.second, null) {}
        }
    }
}

@Composable
private fun Check(ok: Boolean?, title: String, detail: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(ok); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.ink, fontSize = 14.sp)
            Text(detail, color = Palette.mute, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (action != null) { Spacer(Modifier.width(8.dp)); Btn(action, onClick = onAction) }
    }
}

/** 呼叫自己的 Agent provider（和語音總控走同一條路） */
fun selfCall(ctx: Context, method: String, json: JSONObject?): JSONObject? = runCatching {
    val extras = Bundle().apply { if (json != null) putString(HoonsorAgent.EXTRA_JSON, json.toString()) }
    val b = ctx.contentResolver.call("${ctx.packageName}.agent", method, null, extras)
    b?.getString(HoonsorAgent.EXTRA_JSON)?.let { JSONObject(it) }
}.getOrNull()

/** API 測試台（F23）：選動作、填參數、執行，看 summary 與 data */
@Composable
fun TesterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val names = WeatherAgentCatalog.actions.map { it.getString("name") }
    var action by remember { mutableStateOf(names.first()) }
    var params by remember { mutableStateOf("{}") }
    var dry by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("← 返回", onClick = onBack); Spacer(Modifier.width(10.dp))
            Text("API 測試台", color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Card {
            CardTitle("動作")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEach { n -> Chip(n, n == action) { action = n; token = null; params = WeatherAgentCatalog.actions.first { it.getString("name") == n }.let { example(n) } } }
            }
            Body(WeatherAgentCatalog.actions.first { it.getString("name") == action }.getString("description"), Palette.mute, 12)
            OutlinedTextField(params, { params = it }, Modifier.fillMaxWidth(), label = { Text("params（JSON）", color = Palette.mute) }, colors = fieldColors())
            ToggleRow("dryRun（只試算、不執行）", null, dry) { dry = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("執行", primary = true) {
                    scope.launch {
                        result = "執行中…"
                        val req = JSONObject().put("api", 1).put("requestId", "tester-${System.currentTimeMillis()}").put("action", action)
                            .put("params", runCatching { JSONObject(params) }.getOrDefault(JSONObject())).put("dryRun", dry).put("utterance", "API 測試台")
                            .apply { token?.let { put("confirmToken", it) } }
                        val r = withContext(Dispatchers.IO) { selfCall(ctx, "execute", req) }
                        token = r?.optJSONObject("error")?.optJSONObject("data")?.optString("confirmToken")?.ifBlank { null }
                        result = r?.toString(2) ?: "沒有回應"
                    }
                }
                if (token != null) Body("已收到確認權杖，再按一次「執行」就會帶上（模擬倬說「確定」）", Palette.warn, 12)
            }
        }
        if (result.isNotBlank()) Card {
            CardTitle("結果")
            Text(result, color = Palette.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
                modifier = Modifier.background(Palette.bgBottom.copy(alpha = .5f), RoundedCornerShape(8.dp)).padding(8.dp))
        }
    }
}

private fun example(n: String) = when (n) {
    "weather_tomorrow" -> """{"place":"auto"}"""
    "weather_now", "weather_week" -> """{"place":"current"}"""
    "air_quality" -> """{"day":"tomorrow"}"""
    "place_set" -> """{"which":"school","useCurrent":true}"""
    "backup_import" -> """{"mode":"replace"}"""
    else -> "{}"
}
