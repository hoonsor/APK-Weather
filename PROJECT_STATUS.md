# PROJECT_STATUS：APK-Weather（79 號：安卓天氣小工具）

## 目前版本

v0.1.0（骨架＋規則引擎，範例資料；尚未連網、尚未有小工具）

## 任務

- [x] 第 1 階段：`docs/FEATURES.md` 倬 2026-11-10 全部確認
- [x] 第 2 階段：`docs/DATA_SOURCES.md` 查證、`docs/AGENT_API.md`、`docs/agent-manifest.json`（規劃版，9 個動作）；語音總控 MANUAL.md 已收錄（APK-VoiceHub 808ad03，已 push）
- [x] 第 3 階段：倬 2026-11-10 確認「定案草稿：A＋C」；`docs/DESIGN.md` 已寫（含設定頁、健康檢查頁的版面規格）
- [x] 第 4 階段：本機骨架（Gradle、CI、共用 SDK、正式版＋LAB 版），v0.1.0
- [ ] 第 4 階段（倬）：建立 GitHub repo `hoonsor/APK-Weather`、加入共用簽章腳本清單、申請氣象署授權碼與環境部金鑰
- [ ] 第 5 階段：分版實作
  - [x] v0.1.0 骨架＋規則引擎（穿著、颱風影響、辦公日曆、播報句、AQI、天氣圖示）＋25 個 JVM 測試
  - [ ] v0.2.0 資料來源：氣象署 F-D0047-093（3 天＋1 週）、環境部 aqx_p_432／aqf_p_01、颱風 W-C0034-005／001、辦公日曆 CSV、停班停課；授權碼存 Keystore；快取；鄉鎮中心點表與定位→鄉鎮（實際欄位要等有金鑰才能核對）
  - [ ] v0.3.0 桌面小工具：4×1（A／C 切換）＋兩個 2×2，WorkManager 定時更新
  - [ ] v0.4.0 APP 主畫面（依 DESIGN.md 第 1 節的順序）
  - [ ] v0.5.0 Agent API：AgentProvider 實作 9 個動作、API 測試台；manifest 拿掉 `status: planned`
  - [ ] v0.6.0 設定頁、健康檢查頁、備份四件套、突發通知（F18）、早上降雨提醒（F19）
- [ ] 第 6 階段：交付
- [ ] 第 7 階段：收工、更新 APP-REGISTRY.md
- [ ] 語音總控（42 號）：定時播報加入「呼叫其他 APP 動作」（另案）

## 版本歷程

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.0.0 | 2026-11-10 | 2ba351f | 建立文件骨架與 FEATURES 草案 | SOP 第 1 階段 |
| v0.1.0 | 2026-11-10 | （本次） | 專案骨架、DESIGN.md、domain 規則引擎與 JVM 測試、骨架畫面 | 倬確認設計後開始實作；先做不需金鑰的純邏輯 |

## 待實機驗證

- [ ] v0.1.0：安裝後打開，骨架畫面顯示明天播報句、颱風句、7 日穿著（範例資料）

## 已知問題

（無）

## ♻️ 可回收候選

（無；2026-11-10 查共用模組庫，沒有適用於 Kotlin 天氣 APP 的模組）

## 下一步

1. 倬：建立 GitHub repo `hoonsor/APK-Weather`（或同意由 AI 用 gh 建立 private repo 並 push），把它加進 `setup-hoonsor-signing.ps1` 的清單再執行一次；申請氣象署授權碼、環境部金鑰（之後在 APP 設定頁填，不進 repo）。
2. AI：做 v0.3.0 小工具（不需金鑰，先接範例資料＋快取介面），同時做 v0.2.0 不需金鑰的部分（辦公日曆、Open-Meteo 備援、鄉鎮中心點表）；拿到金鑰後抓一筆實際資料核對氣象署、颱風、空品的欄位再寫解析。

## 交接紀錄

### 2026-11-10／電腦 A
- 完成：建立 `D:\dev\APK-Weather` 本機 repo（尚無遠端）、AGENTS.md、CLAUDE.md、本檔、`docs/FEATURES.md` 草案；79 號資料夾放 `CODE_LOCATION.txt`。
- 決定＋原因：見 AGENTS.md「關鍵設計決策」。倬同意全部建議，並追加 PM2.5 警戒、颱風侵襲時間與對家／學校的影響判斷。
- 放棄＋原因：由語音總控抓定位（定位權限會分散到兩支 APP）；天氣 APP 自己定時朗讀（會和行程提醒搶著說話）；AI 產生穿著建議（不穩定、花額度）。
- 未完成：（已過時，見下一筆）
- 環境注意事項：之後需要的金鑰名稱（只寫名稱）：`CWA_API_KEY`、`MOENV_API_KEY`，開發時用 dotenvx 設定。

### 2026-11-10（晚）／電腦 A
- 完成：FEATURES 確認、資料來源查證（DATA_SOURCES.md）、AGENT_API.md、agent-manifest.json（規劃版）、語音總控說明書收錄；三個版面方案與「定案草稿：A＋C」。
- 決定＋原因：小工具改 4×1（倬目前用的就是 4×1）；A／C 版面用右側圓點切換（Glance／RemoteViews 不支援滑動手勢）。
- 放棄＋原因：滑動切換：Android 桌面小工具無法偵測滑動，只有 StackView 這類集合元件能滑，Glance 不支援，而且各家桌面（ColorOS）行為不一致。4×1 放整行颱風提示：高度不夠，改成地點旁的小標籤。
- 未完成：（已過時，見下一筆）
- 環境注意事項：設計稿預覽用 79 號資料夾的 `.claude/launch.json`（design-mockups，port 8790）。

### 2026-11-10（深夜）／電腦 A
- 完成：`docs/DESIGN.md`；Android 骨架（從 APK-Todo 複製 Gradle wrapper 與 CI，從語音總控複製 HoonsorAgent.kt 與 agent_compat_check.py）；domain 規則引擎；WeatherAgentCatalog（與 manifest 一致）；骨架畫面。本機 `testProdDebugUnitTest` 25 個測試全過，prod／lab debug APK 都能組建。
- 決定＋原因：
  - 穿著：白天穿什麼看體感最高溫，早晚加什麼看體感最低溫。FEATURES 的級距表沒寫要用哪個溫度，這樣才會得到設計稿「短袖加薄外套」這種說法。
  - 降雨只有白天或晚上其中一段達標時，說「晚上帶把折傘」這種分段說法。
  - ManifestTest 比對時忽略 `status`、`version`；v0.5 實作 AgentProvider 後再拿掉 `status: planned`。
  - 骨架版 Manifest 先不宣告 Agent 權限與 provider，v0.5 再加，避免語音總控掃到但動作不能用。
- 放棄＋原因：Room（資料量小）；Google Play 定位服務（多一個依賴，系統 LocationManager 在 API 30 以上就有 getCurrentLocation）。
- 未完成：GitHub 遠端、金鑰；資料來源、小工具、主畫面、Agent API、設定與健康檢查。
- 下一步：見上方「下一步」。
- 環境注意事項：JDK 用 `C:\Users\hoonsor\.jdks\jbr-21.0.11`；`local.properties` 是從 APK-Todo 複製的（sdk.dir），電腦 B 要自己建。
