# APK-Weather（79 號：安卓天氣小工具）

> 所有 AI 共用的專案說明書。Claude 透過 `CLAUDE.md`（`@AGENTS.md`）讀這份。

## 專案目的

倬的 Android 桌面天氣小工具，同時提供語音總控（`tw.hoonsor.voicehub`，42 號）每晚播報用的「明天天氣＋穿著建議＋空品警戒＋颱風影響」資料。

## 目前階段

第 1 階段意圖訪談：`docs/FEATURES.md` 草案等倬確認。**確認前不寫功能程式**（依 hoonsor-android-app SOP）。

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

## 技術棧（依 SOP 標準骨架，第 4 階段建立）

Kotlin、minSdk 30／targetSdk 35、Compose＋M3、Jetpack Glance（小工具）、WorkManager（背景更新）、套件名 `tw.hoonsor.weather`、正式版＋LAB 版、共用簽章、GitHub Actions 編譯、共用 SDK `HoonsorAgent.kt`。

## 絕對不要重踩的坑

- Gradle 不能放中文路徑：程式碼一律在 `D:\dev\APK-Weather`
- ColorOS 會擋背景排程與小工具更新：健康檢查頁要引導「電池不限制」「自啟動」「關聯啟動」
- 背景定位需要「一律允許」；沒給時退回「最後已知位置＋常用地點」，不能讓小工具空白
- 桌面小工具不能偵測滑動手勢（Glance／RemoteViews 只有點擊）；4×1 的 A／C 版面切換用右側圓點的點擊（actionRunCallback 改該 widget 的 Glance state），每個 widget id 各自記住版面
- API 授權碼（氣象署、環境部）用 Keystore 加密存在手機；開發用值走 dotenvx，絕不寫進 repo

## Always／Ask／Never

- Always：改程式前 commit；純邏輯（穿著規則、颱風距離判斷、播報文字組裝）寫 JVM 測試；動作改變時同步 `docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控 `docs/manual/MANUAL.md`
- Ask：建立 GitHub 遠端 repo、改語音總控程式、新增付費服務
- Never：把家／學校座標或住址寫進 repo 或 log；把定位送 AI；破壞 Agent API 相容規則
