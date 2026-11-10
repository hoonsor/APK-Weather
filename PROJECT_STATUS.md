# PROJECT_STATUS：APK-Weather（79 號：安卓天氣小工具）

## 目前版本

v0.0.0（只有文件，尚無程式）

## 任務

- [x] 第 1 階段：`docs/FEATURES.md` 倬 2026-11-10 全部確認
- [x] 第 2 階段：`docs/DATA_SOURCES.md` 查證、`docs/AGENT_API.md`、`docs/agent-manifest.json`（規劃版，9 個動作）；語音總控 MANUAL.md 已收錄（APK-VoiceHub 808ad03，已 push）
- [ ] 第 3 階段：三個方案已做；倬 11/10 回饋後做了「定案草稿：A＋C」（`docs/design/mockups.html` 第一個分頁，claude.ai artifact「倬的天氣：版面設計方案」）：4×1 小工具 A／C 版面點圓點切換、APP 用 A 樣式＋C 的明天大卡。等倬確認草稿後寫 `docs/DESIGN.md`，再補設定與健康檢查頁
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

1. 倬確認「定案草稿：A＋C」。倬的決定：桌面小工具是 4×1（不是 4×2）；小工具 A 和 C 都要，可切換；APP 以 A 為主，明天的部分用 C 的配置。滑動切換在 Android 小工具做不到（見 AGENTS.md 的坑），改用點圓點切換。
2. 定案後寫 `docs/DESIGN.md`（唯一色票），補設定頁、健康檢查頁版面，進第 4 階段骨架（請倬建 GitHub repo、加入共用簽章腳本清單、申請氣象署與環境部金鑰）。

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
- 未完成：倬確認定案草稿、寫 DESIGN.md、設定頁與健康檢查頁版面；第 4 階段需要倬建 GitHub repo、加入共用簽章腳本清單、申請氣象署與環境部金鑰。
- 下一步：見上方「下一步」。
- 環境注意事項：設計稿預覽用 79 號資料夾的 `.claude/launch.json`（design-mockups，port 8790）。
