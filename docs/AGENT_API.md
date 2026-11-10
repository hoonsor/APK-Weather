# Agent API：倬的天氣（tw.hoonsor.weather）

> 狀態：**規劃中**（v0.0.0，尚未實作）。協定本身見語音總控 `docs/AGENT_PROTOCOL.md`（api 1），這裡只列本 APP 的動作。
> 機器可讀版：`docs/agent-manifest.json`（實作後與 `describe` 回傳完全一致，JVM 測試比對）。

## 動作總覽

| 動作 | risk | 參數 | 範例句 | 狀態 |
|---|---|---|---|---|
| weather_tomorrow | read | place?（auto／current／home／school）、includeAir?、includeTyphoon? | 明天要帶傘嗎 | 規劃中 |
| weather_now | read | place?（current／home／school） | 現在外面幾度 | 規劃中 |
| weather_week | read | place? | 週末會不會下雨 | 規劃中 |
| air_quality | read | place?、day?（today／tomorrow） | 要不要戴口罩 | 規劃中 |
| typhoon_status | read | — | 颱風會不會來 | 規劃中 |
| place_list | read | — | 我設定的地點有哪些 | 規劃中 |
| place_set | reversible | which（home／school）、town?、useCurrent? | 把學校設成現在這裡 | 規劃中 |
| backup_export | reversible | — | 備份天氣設定 | 規劃中 |
| backup_import | destructive | mode?（merge／replace） | 還原天氣備份 | 規劃中 |

## 共通行為

- **逾時**：要連網的動作覆寫 `timeoutMs` 為 15 秒。先回 30 分鐘內的快取；快取過期才連網，連不上就回最後一次資料並在 summary 說「這是 N 點的資料」。
- **地點**：`auto` 依 F07／F08：明天是辦公日曆的上班日 → school；否則 → home；沒設定家 → current。沒設定學校卻要查 school → `NOT_FOUND`，message「還沒設定學校的位置，要不要把現在這裡設成學校？」
- **summary 是完整播報句**：大腦直接唸，不必再讓 AI 改寫；data 同時給結構化欄位，讓 AI 回答追問（「那後天呢」）。
- **隱私**：data 只放鄉鎮名稱，不放經緯度。

## weather_tomorrow

晚間播報（語音總控定時呼叫，`place=auto`）與隨口問都用這個。

summary 範例：
> 明天 11 月 11 日星期三，學校在中壢區，白天多雲，體感 17 到 24 度，下午降雨機率 60%。早晚溫差大，建議短袖加薄外套，記得帶傘。

有空品警戒（明天 AQI 預報≥101）時接一句：
> 明天空氣品質橘色，對敏感族群不健康，外出建議戴口罩。

有颱風時接 typhoon_status 的精簡版（一到兩句）。

data：
```json
{
  "date": "2026-11-11", "placeKind": "school", "town": "桃園市中壢區", "isWorkday": true,
  "weather": "多雲", "feelsMin": 17, "feelsMax": 24, "tempMin": 18, "tempMax": 25,
  "rainProbDay": 60, "rainProbNight": 20, "uvIndex": 5, "beaufortMax": 3,
  "clothing": ["短袖加薄外套", "記得帶傘"],
  "air": { "aqi": 115, "level": "orange", "area": "北部空品區" },
  "typhoon": null,
  "source": "cwa", "updatedAt": "2026-11-10T20:45:00+08:00"
}
```

## weather_now

summary：「中壢區現在 22 度，體感 21 度，陰天，接下來 3 小時降雨機率 30%。」

## weather_week

summary：「這週中壢區 16 到 26 度，星期四跟星期五降雨機率比較高，週末轉晴。」data 放 7 天陣列。

## air_quality

- today：附近測站即時 AQI 與 PM2.5；tomorrow：空品區預報 AQI。
- summary：「中壢測站現在 AQI 112，橘色，PM2.5 偏高，敏感族群外出戴口罩。」綠色時：「空氣品質良好，不用戴口罩。」

## typhoon_status

- 沒有颱風：「目前沒有颱風或熱帶性低氣壓。」
- 有颱風：名稱、強度、位置、預估最接近台灣的時間、海上／陸上警報、家與學校各自的影響等級、停班停課（F13）。
- 影響等級（F11，本機規則）：

| 等級 | 判斷 |
|---|---|
| 可能直接影響 | 任一路徑預報點的 7 級風暴風半徑（加 70% 機率半徑）涵蓋該地 |
| 外圍影響 | 距最近預報點 < 暴風半徑＋300 公里，或該縣市在陸上警報範圍 |
| 影響低 | 其他 |

- 預估時間超過 5 天：一律加「路徑還不確定」。
- summary 範例：「○○颱風中度颱風，在恆春東南方約 600 公里，預估星期五清晨最接近台灣，已發布海上警報。學校可能進入暴風圈，家裡是外圍影響。停班停課還沒公布。」

## place_list／place_set

- place_set 只接受鄉鎮層級；`town` 重名（例：「東區」）回 `AMBIGUOUS`，candidates 列縣市＋區。
- place_set 成功回 `undo: {action: "place_set", params: {which, town: 舊值}}`。

## backup_export／backup_import

依 SOP 備份四件套。backup_import 沒帶 confirmToken 時回 `NEEDS_CONFIRMATION`；確認後回 `NEEDS_UI`（開檔案選擇畫面，data.leavesApp=true）。

## 錯誤

| 情況 | code | message（會被唸出） |
|---|---|---|
| 沒有定位權限 | PERMISSION | 天氣 APP 沒有定位權限，打開 APP 允許定位後再試一次。 |
| 沒填授權碼 | PERMISSION | 還沒設定氣象署授權碼，到天氣 APP 的設定填好就能用。 |
| 連不上且沒有快取 | INTERNAL | 氣象資料現在抓不到，可能是網路不穩，等一下再問我。 |
| 學校或家未設定 | NOT_FOUND | 還沒設定學校的位置，要不要把現在這裡設成學校？ |

## 語音總控那邊要做的事（42 號，另案）

「明天行程提醒」目前只唸行事曆。要加：
1. 設定頁「一起唸明天天氣」開關（預設開）。
2. 時間到時呼叫 `tw.hoonsor.weather` 的 `weather_tomorrow`（place=auto），把 summary 接在行程後面；天氣 APP 沒裝或逾時就只唸行程。
3. 屬於「需要大腦特別處理」（定時、不經 AI 呼叫其他 APP 動作），依協定第 7 節要改語音總控程式。
