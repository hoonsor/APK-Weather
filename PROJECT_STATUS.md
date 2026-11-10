# PROJECT_STATUS：APK-Weather（79 號：安卓天氣小工具）

## 目前版本

v0.0.0（只有文件，尚無程式）

## 任務

- [x] 第 1 階段：`docs/FEATURES.md` 倬 2026-11-10 全部確認
- [x] 第 2 階段：`docs/DATA_SOURCES.md` 查證、`docs/AGENT_API.md`、`docs/agent-manifest.json`（規劃版，9 個動作）；語音總控 MANUAL.md 已收錄（APK-VoiceHub 808ad03，已 push）
- [ ] 第 3 階段：三個方案已做（`docs/design/mockups.html`，claude.ai artifact「倬的天氣：版面設計方案」），等倬選定後寫 `docs/DESIGN.md`，再補設定與健康檢查頁
- [ ] 第 4 階段：骨架（請倬建立 GitHub repo `hoonsor/APK-Weather`、加入共用簽章腳本清單）
- [ ] 第 5 階段：分版實作
- [ ] 第 6 階段：交付
- [ ] 第 7 階段：收工、更新 APP-REGISTRY.md
- [ ] 語音總控（42 號）：定時播報加入「呼叫其他 APP 動作」（另案）

## 版本歷程

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.0.0 | 2026-11-10 | （本次） | 建立文件骨架與 FEATURES 草案 | SOP 第 1 階段 |

## 待實機驗證

（無）

## 已知問題

（無）

## ♻️ 可回收候選

（無；2026-11-10 查共用模組庫，沒有適用於 Kotlin 天氣 APP 的模組）

## 下一步

1. 倬從方案 A（夜空）、B（石墨網格）、C（明天優先）選一個或混搭，參考圖是倬目前用的天氣 APP 截圖。
2. 定案後寫 `docs/DESIGN.md`（唯一色票），補設定頁、健康檢查頁版面，進第 4 階段骨架（請倬建 GitHub repo、加入共用簽章腳本清單、申請氣象署與環境部金鑰）。

## 交接紀錄

### 2026-11-10／電腦 A
- 完成：建立 `D:\dev\APK-Weather` 本機 repo（尚無遠端）、AGENTS.md、CLAUDE.md、本檔、`docs/FEATURES.md` 草案；79 號資料夾放 `CODE_LOCATION.txt`。
- 決定＋原因：見 AGENTS.md「關鍵設計決策」。倬同意全部建議，並追加 PM2.5 警戒、颱風侵襲時間與對家／學校的影響判斷。
- 放棄＋原因：由語音總控抓定位（定位權限會分散到兩支 APP）；天氣 APP 自己定時朗讀（會和行程提醒搶著說話）；AI 產生穿著建議（不穩定、花額度）。
- 未完成：FEATURES 確認；資料來源代碼尚未查證。
- 環境注意事項：之後需要的金鑰名稱（只寫名稱）：`CWA_API_KEY`、`MOENV_API_KEY`，開發時用 dotenvx 設定。
