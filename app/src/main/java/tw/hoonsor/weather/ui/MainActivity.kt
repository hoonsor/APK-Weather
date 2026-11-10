package tw.hoonsor.weather.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.backup.Backup
import tw.hoonsor.weather.data.AppError
import tw.hoonsor.weather.data.CwaParse
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.ui.theme.Palette
import tw.hoonsor.weather.widget.Weather41Receiver
import tw.hoonsor.weather.widget.WeatherNow22Receiver
import tw.hoonsor.weather.widget.WeatherTomorrow22Receiver
import tw.hoonsor.weather.widget.WeatherWidgets
import tw.hoonsor.weather.work.Notifier
import tw.hoonsor.weather.work.Scheduler
import java.util.Locale

class MainActivity : ComponentActivity(), SettingsActions, HealthActions {
    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_IMPORT_MODE = "importMode"
    }

    private var screen by mutableStateOf("home")
    private var home by mutableStateOf(HomeState())
    private var pendingSection by mutableStateOf<String?>(null)
    private var tts: TextToSpeech? = null
    private var importMerge = false

    private val locationPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load(force = true) }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { Backup.writeTo(this, uri) }.onSuccess { toast("備份已儲存") }.onFailure { toast("儲存失敗：${it.message}") }
    }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { Backup.import(this, uri, importMerge) }
            .onSuccess { at -> toast("已匯入（備份時間 ${at.take(16).replace('T', ' ')}），可在設定頁「復原到匯入前」"); onPlacesChanged() }
            .onFailure { toast("匯入失敗：${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val list = rememberLazyListState()
            LaunchedEffect(pendingSection) {
                when (pendingSection) {
                    "week" -> { screen = "home"; list.animateScrollToItem(HomeItems.WEEK) }
                    "tomorrow" -> { screen = "home"; list.animateScrollToItem(HomeItems.TOMORROW) }
                }
                if (pendingSection == "week" || pendingSection == "tomorrow") pendingSection = null
            }
            BackHandler(screen != "home") { screen = if (screen == "health" || screen == "tester") "settings" else "home" }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.bgTop, .4f to Palette.bgMid, 1f to Palette.bgBottom)).systemBarsPadding()) {
                when (screen) {
                    "settings" -> SettingsScreen(this@MainActivity, { screen = "health" }, { screen = "tester" }) { screen = "home"; load() }
                    "health" -> HealthScreen(this@MainActivity) { screen = "settings" }
                    "tester" -> TesterScreen { screen = "settings" }
                    else -> HomeScreen(home, list, onSelect = { k -> home = home.copy(selected = k); load() }, onRefresh = { load(force = true) },
                        onSettings = { screen = "settings" }, onSpeak = ::speak)
                }
            }
        }
        if (!Graph.repo.locator.hasPermission()) locationPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        else load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        when (val sec = i?.getStringExtra(EXTRA_SECTION)) {
            "import" -> { screen = "settings"; importBackup(i.getStringExtra(EXTRA_IMPORT_MODE) == "merge") }
            "week", "tomorrow" -> pendingSection = sec
        }
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    // ---------------- 主畫面資料 ----------------

    private fun load(force: Boolean = false) {
        val repo = Graph.repo
        val sel = home.selected
        home = home.copy(
            loading = true, hasHome = repo.settings.place(PlaceKind.HOME) != null, hasSchool = repo.settings.place(PlaceKind.SCHOOL) != null,
            hasCwa = repo.vault.has(tw.hoonsor.weather.data.Vault.CWA), hasMoenv = repo.vault.has(tw.hoonsor.weather.data.Vault.MOENV), shift = repo.settings.shift,
            snap = home.snap ?: repo.cached(sel),
        )
        lifecycleScope.launch {
            var err: String? = null
            val snap = try { repo.snapshot(sel, force = force) } catch (e: AppError) { err = e.message; repo.cached(sel) }
            val auto = runCatching { repo.autoKind() }.getOrDefault(PlaceKind.CURRENT)
            val tKind = if (sel == PlaceKind.CURRENT) auto else sel
            val tSnap = if (tKind == sel) snap else runCatching { repo.snapshot(tKind, force = force) }.getOrNull()
            val tText = runCatching { repo.tomorrowReport(tKind.key, includeTyphoon = false).summary }.getOrNull()
            val ty = runCatching { repo.typhoons() }.getOrNull()
            val t = ty?.list?.firstOrNull()
            val impacts = t?.let { runCatching { repo.impactPlaces(it).map { p -> p.first to p.third.level } }.getOrDefault(emptyList()) }.orEmpty()
            val tyText = t?.let { runCatching { repo.typhoonText(it, short = false) }.getOrNull() }
            home = home.copy(snap = snap, autoKind = auto, tomorrowKind = tKind, tomorrowSnap = tSnap, tomorrowText = tText, typhoon = ty, typhoonText = tyText,
                impacts = impacts, loading = false, error = err)
            runCatching { WeatherWidgets.pushAll(this@MainActivity) }
        }
    }

    private fun onPlacesChanged() { home = home.copy(snap = null, tomorrowSnap = null); load(force = true) }

    private fun speak(text: String) {
        val t = tts
        if (t != null) { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tomorrow"); return }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.TAIWAN; tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tomorrow") }
            else toast("手機的文字轉語音沒有開")
        }
    }

    // ---------------- SettingsActions ----------------

    override fun requestLocation() = locationPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    override fun requestBackgroundLocation() {
        // Android 11+ 只能到系統設定選「一律允許」
        toast("請在權限頁的「位置」選「一律允許」")
        openAppDetails()
    }
    override fun requestNotifications() = notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    override fun testNotification() {
        if (!Notifier.canNotify(this)) { requestNotifications(); return }
        Notifier.show(this, Notifier.RAIN, 9, "測試通知", "這是倬的天氣的測試通知，早上降雨提醒長這樣。")
    }
    override fun shareBackup() { runCatching { startActivity(Backup.shareIntent(this, Backup.export(this))) }.onFailure { toast("分享失敗：${it.message}") } }
    override fun saveBackupToFile() = createDoc.launch(Backup.fileName())
    override fun importBackup(merge: Boolean) { importMerge = merge; openDoc.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    override fun undoImport(): Boolean = Backup.undoImport(this).also { if (it) onPlacesChanged() }
    override fun hasUndo() = Backup.hasUndo(this)
    override fun pinWidget(which: Int) {
        val cls = when (which) { 0 -> Weather41Receiver::class.java; 1 -> WeatherNow22Receiver::class.java; else -> WeatherTomorrow22Receiver::class.java }
        val m = getSystemService(AppWidgetManager::class.java)
        if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(this, cls), null, null)
        else toast("這個桌面不支援直接加入，請長按桌面 → 小工具 → 倬的天氣")
    }
    override fun refreshWidgets() { lifecycleScope.launch { runCatching { WeatherWidgets.pushAll(this@MainActivity) }; toast("小工具已更新") } }
    override fun reschedule() = Scheduler.ensure(this, replace = true)
    override fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override suspend fun testCwa(): String = try {
        val t = Graph.settings.place(PlaceKind.SCHOOL) ?: Graph.settings.place(PlaceKind.HOME) ?: Town("臺北市", "中正區", 25.03, 121.52)
        Graph.repo.invalidate(PlaceKind.SCHOOL)
        val text = tw.hoonsor.weather.data.Sources().cwaForecast(Graph.vault.get(tw.hoonsor.weather.data.Vault.CWA)!!, t.county, t.name)
        val r = CwaParse.parse(text, t.name)
        if (r.hours.isEmpty() && r.days.isEmpty()) "連線成功，但解析不到「${t.full}」的資料（欄位格式可能和文件不同，請回報）"
        else "成功：${t.full} 有 ${r.hours.size} 筆逐時、${r.days.size} 天預報"
    } catch (e: Exception) { "失敗：${e.message}" }

    override suspend fun testMoenv(): String = try {
        val text = tw.hoonsor.weather.data.Sources().moenvStations(Graph.vault.get(tw.hoonsor.weather.data.Vault.MOENV)!!)
        val n = tw.hoonsor.weather.data.MoenvParse.stations(text).size
        if (n == 0) "連線成功，但解析不到測站（欄位格式可能不同，請回報）" else "成功：抓到 $n 個測站"
    } catch (e: Exception) { "失敗：${e.message}" }

    override suspend fun useCurrentFor(kind: PlaceKind): String = try {
        val t = Graph.repo.resolveTown(PlaceKind.CURRENT)
        if (t.county.isBlank()) "抓到位置，但判斷不出鄉鎮，請改用「輸入」"
        else { Graph.settings.setPlace(kind, t); Graph.repo.invalidate(kind); onPlacesChanged(); "已設成 ${t.full}" }
    } catch (e: AppError) { e.message ?: "抓不到位置" }

    override suspend fun search(q: String) = runCatching { Graph.repo.searchTown(q) }.getOrDefault(emptyList())

    override suspend fun checkCalendar(): String = try {
        val n = Graph.repo.refreshCalendar()
        if (n == 0) "沒有抓到新的辦公日曆檔案；" + Graph.repo.calendarInfo() else "下載了 $n 個檔案；" + Graph.repo.calendarInfo()
    } catch (e: Exception) { "檢查失敗：${e.message}" }

    // ---------------- HealthActions ----------------

    override fun openAppDetails() { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    override fun requestBatteryExemption() {
        runCatching { startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }.onFailure { openAppDetails() }
    }
    override fun openSettings() { screen = "settings" }
}
