package tw.hoonsor.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.data.Vault
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.ui.theme.Palette
import java.time.LocalDate

/** 設定頁需要 Activity 幫忙的事（權限、檔案選擇、通知測試…） */
interface SettingsActions {
    fun requestLocation()
    fun requestBackgroundLocation()
    fun requestNotifications()
    fun testNotification()
    fun shareBackup()
    fun saveBackupToFile()
    fun importBackup(merge: Boolean)
    fun undoImport(): Boolean
    fun hasUndo(): Boolean
    fun pinWidget(which: Int)
    fun refreshWidgets()
    fun reschedule()
    fun toast(msg: String)
    suspend fun testCwa(): String
    suspend fun testMoenv(): String
    suspend fun useCurrentFor(kind: PlaceKind): String
    suspend fun search(q: String): List<Town>
    suspend fun checkCalendar(): String
}

@Composable
fun SettingsScreen(a: SettingsActions, onHealth: () -> Unit, onTester: () -> Unit, onBack: () -> Unit) {
    val s = Graph.settings
    val v by s.version.collectAsState()
    val vv by Graph.vault.version.collectAsState()
    @Suppress("UNUSED_VARIABLE") val touch = v + vv
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("← 返回", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("設定", color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Section("1　地點") {
            listOf(PlaceKind.HOME to "家", PlaceKind.SCHOOL to "學校").forEach { (kind, label) -> PlaceRow(kind, label, a) }
            val hasLoc = Graph.repo.locator.hasPermission()
            val hasBg = Graph.repo.locator.hasBackground()
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (hasBg) true else if (hasLoc) null else false)
                Spacer(Modifier.width(8.dp))
                Body(if (hasBg) "定位：一律允許" else if (hasLoc) "定位：只在使用時允許（小工具在背景可能抓不到新位置）" else "定位：還沒允許", size = 13)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasLoc) Btn("允許定位", primary = true) { a.requestLocation() }
                else if (!hasBg) Btn("改成一律允許") { a.requestBackgroundLocation() }
            }
        }

        Section("2　資料來源") {
            KeyRow("中央氣象署授權碼", Vault.CWA, "opendata.cwa.gov.tw 會員資訊 → API 授權碼", a) { a.testCwa() }
            KeyRow("環境部金鑰", Vault.MOENV, "data.moenv.gov.tw 申請；效期 1 年", a) { a.testMoenv() }
            s.moenvKeyDate?.let { d ->
                val left = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d.plusYears(1))
                Body("環境部金鑰約 ${d.plusYears(1)} 到期（剩 $left 天）" + if (left < 30) "，記得續期" else "", if (left < 30) Palette.warn else Palette.mute, 12)
            }
            Body("沒有氣象署授權碼時，預報改用 Open-Meteo（備援），颱風與鄉鎮比對無法使用。", Palette.mute, 12)
        }

        Section("3　播報內容與穿著") {
            ToggleRow("播報空品警戒", "明天 AQI 101 以上（橘色）才唸", s.broadcastAir) { s.broadcastAir = it }
            ToggleRow("播報颱風", "有颱風時接在明天天氣後面", s.broadcastTyphoon) { s.broadcastTyphoon = it }
            Text("穿著偏好：${when { s.shift > 0 -> "怕冷 +${s.shift}"; s.shift < 0 -> "怕熱 ${s.shift}"; else -> "一般" }}", color = Palette.ink, fontSize = 14.sp)
            Slider(s.shift.toFloat(), { s.shift = Math.round(it) }, valueRange = -4f..4f, steps = 7,
                colors = SliderDefaults.colors(thumbColor = Palette.accent, activeTrackColor = Palette.accent))
            val sample = DayForecast(LocalDate.now(), "多雲", 4, 18, 25, 17, 24, 20, 40, 5, 3)
            Body("預覽（體感 17–24 度、晚上降雨 40%）：" + ClothingRules.advise(sample, null, s.shift).sentence, Palette.mute, 12)
        }

        Section("4　提醒") {
            ToggleRow("颱風陸上警報通知", "家或學校所在縣市進入陸上警報時，不等晚上先通知", s.notifyTyphoon) { s.notifyTyphoon = it }
            ToggleRow("空品紫色通知", "明天預報 AQI 201 以上時通知", s.notifyAir) { s.notifyAir = it }
            ToggleRow("早上降雨提醒", "今天會下雨（降雨機率 30% 以上）才提醒", s.morningRain) { s.morningRain = it; a.reschedule() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Body("提醒時間 ${s.morningTime}", size = 14)
                Spacer(Modifier.weight(1f))
                Btn("−10 分") { s.morningTime = s.morningTime.minusMinutes(10); a.reschedule() }
                Btn("+10 分") { s.morningTime = s.morningTime.plusMinutes(10); a.reschedule() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("允許通知") { a.requestNotifications() }
                Btn("測試通知", primary = true) { a.testNotification() }
            }
        }

        Section("5　小工具") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Body("更新頻率", size = 14); Spacer(Modifier.weight(1f))
                Chip("30 分", s.refreshMinutes == 30) { s.refreshMinutes = 30; a.reschedule() }
                Chip("60 分", s.refreshMinutes == 60) { s.refreshMinutes = 60; a.reschedule() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Body("4×1 預設版面", size = 14); Spacer(Modifier.weight(1f))
                Chip("A 現在＋4 天", s.widgetDefault == "A") { s.widgetDefault = "A"; a.refreshWidgets() }
                Chip("C 現在｜明天", s.widgetDefault == "C") { s.widgetDefault = "C"; a.refreshWidgets() }
            }
            Body("已放在桌面的 4×1 各自記住版面，點右側圓點切換。", Palette.mute, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("加入 4×1") { a.pinWidget(0) }
                Btn("加入 2×2 現在") { a.pinWidget(1) }
                Btn("加入 2×2 明天") { a.pinWidget(2) }
            }
            Btn("立即更新小工具", primary = true) { a.refreshWidgets() }
        }

        Section("6　上班日") {
            var info by remember { mutableStateOf(Graph.repo.calendarInfo()) }
            Body(info, size = 13)
            val tomorrow = LocalDate.now().plusDays(1)
            val ov = s.calendarOverrides[tomorrow]
            Body("明天 $tomorrow：" + when (ov) { true -> "手動標為上班"; false -> "手動標為放假"; null -> "依辦公日曆" }, size = 13)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("依日曆", ov == null) { s.calendarOverrides = s.calendarOverrides - tomorrow }
                Chip("明天上班", ov == true) { s.calendarOverrides = s.calendarOverrides + (tomorrow to true) }
                Chip("明天放假", ov == false) { s.calendarOverrides = s.calendarOverrides + (tomorrow to false) }
            }
            Btn("立即檢查辦公日曆更新") { scope.launch { info = a.checkCalendar() } }
        }

        Section("7　備份") {
            Body("備份設定與家、學校地點（不含授權碼）。", Palette.mute, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("分享備份", primary = true) { a.shareBackup() }
                Btn("存到檔案") { a.saveBackupToFile() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("匯入（合併）") { a.importBackup(true) }
                Btn("匯入（取代）") { a.importBackup(false) }
            }
            Btn("復原到匯入前", enabled = a.hasUndo()) { if (a.undoImport()) a.toast("已復原到匯入前的設定") }
        }

        Section("8　工具") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("健康檢查", primary = true) { onHealth() }
                Btn("API 測試台") { onTester() }
            }
        }
        Spacer(Modifier.padding(20.dp))
    }
}

@Composable
private fun PlaceRow(kind: PlaceKind, label: String, a: SettingsActions) {
    val s = Graph.settings
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Town>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val t = s.place(kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.mute, fontSize = 12.sp)
            Text(t?.full ?: "還沒設定", color = if (t == null) Palette.warn else Palette.ink, fontSize = 15.sp)
        }
        Btn("用目前位置") { scope.launch { msg = a.useCurrentFor(kind) } }
        Spacer(Modifier.width(6.dp))
        Btn(if (editing) "取消" else "輸入") { editing = !editing; results = emptyList() }
    }
    msg?.let { Body(it, Palette.mute, 12) }
    if (editing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("例：桃園市中壢區", color = Palette.mute) }, colors = fieldColors())
            Spacer(Modifier.width(6.dp))
            Btn("搜尋", primary = true) { scope.launch { results = a.search(q); if (results.isEmpty()) msg = "找不到「$q」，請加上縣市再試" } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            results.take(4).forEach { r -> Chip(r.full) { s.setPlace(kind, r); Graph.repo.invalidate(kind); editing = false; results = emptyList(); msg = "${label}已設成 ${r.full}"; a.refreshWidgets() } }
        }
        if (t != null) Btn("清除$label") { s.setPlace(kind, null); Graph.repo.invalidate(kind); editing = false }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.ink, unfocusedTextColor = Palette.ink, focusedBorderColor = Palette.accent, unfocusedBorderColor = Palette.mute.copy(alpha = .4f), cursorColor = Palette.accent,
)

@Composable
private fun KeyRow(title: String, name: String, hint: String, a: SettingsActions, test: suspend () -> String) {
    val scope = rememberCoroutineScope()
    val has = Graph.vault.has(name)
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(has); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.ink, fontSize = 14.sp)
            Text(if (has) "已設定（••••••）" else hint, color = Palette.mute, fontSize = 12.sp)
        }
        Btn(if (editing) "取消" else if (has) "更換" else "填入") { editing = !editing; value = "" }
        Spacer(Modifier.width(6.dp))
        Btn("測試", enabled = has) { scope.launch { result = "測試中…"; result = test() } }
    }
    if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, { value = it }, Modifier.weight(1f), singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = fieldColors())
        Spacer(Modifier.width(6.dp))
        Btn("儲存", primary = true, enabled = value.isNotBlank()) {
            Graph.vault.set(name, value)
            if (name == Vault.MOENV) Graph.settings.moenvKeyDate = LocalDate.now()
            editing = false; value = ""
            scope.launch { result = "測試中…"; result = test() }
        }
    }
    result?.let { Body(it, Palette.mute, 12) }
}
