# APK-Weather（79 號：安卓天氣小工具）

> 所有 AI 共用的專案說明書。Claude 透過 `CLAUDE.md`（`@AGENTS.md`）讀這份。

## 專案目的

倬的 Android 桌面天氣小工具，同時提供語音總控（`tw.hoonsor.voicehub`，42 號）每晚播報用的「明天天氣＋穿著建議＋空品警戒＋颱風影響」資料。

## 目前階段

第 5 階段分版實作。FEATURES（F01～F23）、AGENT_API、DESIGN 都已由倬確認（2026-11-10）。版本計畫見 `PROJECT_STATUS.md`。

## 關鍵設計決策＋原因

| 決策 | 原因 |
|---|---|
| 定位、預報、穿著判斷都在天氣 APP；語音總控只呼叫 Agent API | 小工具本來就需要定位；定位權限只放一支 APP；語音總控不寫死任何 APP |
| 晚間播報併進語音總控既有的「明天行程提醒」（預設 21:00） | 只有一段播報，避免兩支 APP 搶著說話 |
| 主資料來源：中央氣象署開放資料（鄉鎮預報、颱風）；備援 Open-Meteo | 台灣鄉鎮準確度最好、有降雨機率與體感；Open-Meteo 免金鑰、可直接用經緯度 |
| 空品：環境部開放資料（AQI 預報＋測站即時值） | 官方來源、有明天預報 |
| 穿著建議、颱風影響判斷用本機規則，不送 AI | 結果固定、可測試、不花額度、不會亂講 |
| 播報地點依「明天要不要上班」決定（上班日唸學校，其他唸家／目前位置） | 晚上人在家，但明天白天在學校 |
| 家、學校地點只存在 APP 設定，不進 repo | 個資（住址） |

## 技術棧

Kotlin、minSdk 30／targetSdk 35、Compose＋M3、Jetpack Glance（小工具）、WorkManager（背景更新）、套件名 `tw.hoonsor.weather`、正式版＋LAB 版、共用簽章、GitHub Actions 編譯、共用 SDK `HoonsorAgent.kt`。不用 Room（資料量小，快取用 JSON 檔）、不用 Google Play 定位服務（用系統 LocationManager）。

## 建置與測試

```powershell
$env:JAVA_HOME="C:\Users\hoonsor\.jdks\jbr-21.0.11"; .\gradlew.bat testProdDebugUnitTest assembleProdDebug assembleLabDebug
```
- 不要用 Android Studio 內附的 JBR 25（Gradle 8.11 不支援）；`local.properties` 只在本機（指向 Android SDK），不進 Git
- CI：`.github/workflows/build.yml`（相容檢查 → 單元測試 → 正式版＋LAB 版；main → latest、vX.Y.Z tag → Release）；共用簽章由 Secrets 提供
- 改了 Agent API 動作：同步 `agent/WeatherAgentCatalog.kt`、`docs/agent-manifest.json`（ManifestTest 會比對，`status`／`version` 不比）、`docs/AGENT_API.md`、語音總控 `docs/manual/MANUAL.md`

## 目錄

```
app/src/main/java/
├─ tw/hoonsor/agent/HoonsorAgent.kt   共用 Agent SDK（從語音總控複製，不要在這裡改）
└─ tw/hoonsor/weather/
   ├─ App.kt    Graph 單例（Repository、Settings、Vault）；Application 建通知頻道與排程
   ├─ domain/   純邏輯（不 import android）：Models、Snapshot、Clothing（F06）、TyphoonImpact（F11）、Workday（F07/F08）、Broadcast（播報句）、Aqi、WeatherIcon、Sun（日出日落）
   ├─ data/     Repository（唯一入口：地點、快照快取、颱風、空品、辦公日曆、停班停課、報告）
   │            Sources（所有連線）、*Parse（純解析：CwaParse、Parsers.kt、SnapshotJson、JsonX 寬鬆讀取）
   │            Settings（settings.xml）、Vault（授權碼，Keystore AES-GCM，不備份）、Locator（LocationManager＋Geocoder）
   ├─ widget/   WidgetData（小工具內容，純資料）、WeatherWidgets（Glance：4×1 A／C、2×2 現在、2×2 明天、ToggleMode）
   ├─ agent/    WeatherAgentCatalog（describe）、AgentProvider（9 個動作）
   ├─ work/     Scheduler、RefreshWorker（定時更新＋小工具＋突發通知）、MorningWorker（F19）、Alerts、Notifier
   ├─ backup/   Backup（分享、存下載、SAF 存檔、匯入合併／取代、匯入前自動備份與復原）
   └─ ui/       MainActivity（畫面切換、權限、檔案選擇、朗讀）、HomeScreen、SettingsScreen、HealthScreen（含 API 測試台）、Common、theme/Palette
app/src/test/   JVM 測試：穿著、颱風、辦公日曆、播報句、解析器（照文件手寫的範例）、小工具內容、manifest 一致性
docs/           FEATURES、DATA_SOURCES、AGENT_API、agent-manifest.json、DESIGN、design/mockups.html
```

## 絕對不要重踩的坑

- Gradle 不能放中文路徑：程式碼一律在 `D:\dev\APK-Weather`
- ColorOS 會擋背景排程與小工具更新：健康檢查頁要引導「電池不限制」「自啟動」「關聯啟動」
- 背景定位需要「一律允許」；沒給時退回「最後已知位置＋常用地點」，不能讓小工具空白
- 桌面小工具不能偵測滑動手勢（Glance／RemoteViews 只有點擊）；4×1 的 A／C 版面切換用右側圓點的點擊（actionRunCallback 改該 widget 的 Glance state），每個 widget id 各自記住版面
- 小工具內容只能由 `WeatherWidgets.pushAll()` 寫進 Glance 狀態（provideContent 不會自己讀檔）；圖示必須是 `res/drawable` 的 XML，由 `python tools/gen_drawables.py app/src/main/res/drawable` 產生（改圖示或色票改這支腳本再重跑）；Glance 一個 Row／Column 最多 10 個子元件
- ContentProvider 比 Application.onCreate 早建立：`AgentProvider.onCreate` 自己呼叫 `Graph.init`
- 資料格式已用 2026-11-11 的實際回應核對，存在 `app/src/test/resources/real/`，`RealDataTest` 比對。來源改格式時：設定頁「測試」會說「連線成功但解析不到」，就重抓一份實際回應換掉測試資料再修 *Parse（授權碼只在網址，不能存進檔案）
- 開發用授權碼在 `.env`（dotenvx 加密：`CWA_API_KEY`、`MOENV_API_KEY`），用 `dotenvx run -f .env -- <指令>` 帶進去；`.env`、`.env.keys` 已在 .gitignore
- 颱風路徑 W-C0034-005 會列出整個西北太平洋的颱風，只留離台灣 1000 公里內的（`TyphoonParse.relevant`）；警報 W-C0034-001 沒有警報時也會回「解除颱風警報」，要看 urgency
- 停班停課只用人事總處網頁：NCDR RSS 要登入會員
- `HoonsorAgent.PERMISSION` 是權限名稱字串，錯誤碼要用 `HoonsorAgent.PERMISSION_DENIED`
- API 授權碼（氣象署、環境部）用 Keystore 加密存在手機；開發用值走 dotenvx，絕不寫進 repo

## Always／Ask／Never

- Always：改程式前 commit；純邏輯（穿著規則、颱風距離判斷、播報文字組裝）寫 JVM 測試；動作改變時同步 `docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控 `docs/manual/MANUAL.md`
- Ask：建立 GitHub 遠端 repo、改語音總控程式、新增付費服務
- Never：把家／學校座標或住址寫進 repo 或 log；把定位送 AI；破壞 Agent API 相容規則
