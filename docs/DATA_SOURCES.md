# 資料來源查證（2026-11-10）

> 依官方文件查證；標「未確認」的要在第 5 階段用授權碼抓一筆實際資料核對後再寫解析程式。

## 1. 中央氣象署：鄉鎮天氣預報

- 端點：`https://opendata.cwa.gov.tw/api/v1/rest/datastore/{代碼}?Authorization=…&LocationName=…&ElementName=…&format=JSON`
- 代碼：
  - 全台：F-D0047-089（3 天，逐 3 小時）、F-D0047-091（1 週）。**LocationName 只能填縣市**。
  - 各縣市：單號 001～087＝3 天、雙號 003～088＝1 週（例：宜蘭縣 001／003），LocationName 填**鄉鎮名**。
  - 跨縣市：F-D0047-093，`locationId` 必填（一次 1～5 個），LocationName 填鄉鎮名。
  - **採用**：F-D0047-093 一次查家＋學校＋目前位置（最多 3 組 locationId），呼叫次數最少。
- 要素（2024-12-10 起 ElementName 是中文）：
  - 3 天：溫度、體感溫度、露點溫度、相對濕度、舒適度指數、風向、風速、3小時降雨機率、天氣現象、天氣預報綜合描述
  - 1 週：平均溫度、最高／最低溫度、最高／最低體感溫度、12小時降雨機率、紫外線指數、風速、風向、天氣現象、天氣預報綜合描述等 15 項
  - 值欄位（英文）：Temperature、ApparentTemperature、ProbabilityOfPrecipitation、WindSpeed（m/s）、BeaufortScale、UVIndex、UVExposureLevel、Weather、WeatherCode、WeatherDescription
- 結構：Locations → LocationsName → Location（LocationName、Geocode、Latitude、Longitude）→ WeatherElement → Time
- 發布：每天 05:30、11:30、17:30、23:30。**紫外線只在 1 週資料**；晚上 21:00 播報用 17:30 那版。
- 授權碼：註冊會員後在「會員資訊 → API授權碼」取得。一般會員每 24 小時 2 萬次、2 GB；必須標示出處（顯名聲明）。

## 2. 中央氣象署：颱風

- 熱帶氣旋路徑 **W-C0034-005**：AnalysisData（過去／現在）、ForecastData（InitialTime、ForecastHr、CoordinateLatitude、CoordinateLongitude、MaxWindSpeed、Pressure、七級風半徑 Circle15ms 或 C15ms、十級風半徑 Circle25ms、QuadrantRadii、Radius70PercentProbability；單位公里）。參數 CwaTdNo、Dataset、ForecastHour（6～120）。有熱帶氣旋時每 6 小時更新，警報期間每 3 小時更新。
- 颱風警報 **W-C0034-001**：CAP（海上、海上陸上、解除）；REST 有 JSON／XML，可用 headline、areaDesc／geocode、expires、cwaTyNo 篩選。
- 未確認：JSON 鍵名大小寫、七級風半徑實際鍵名。

## 3. 環境部：空氣品質

- 端點：`https://data.moenv.gov.tw/api/v2/{代碼}?api_key=…&format=json&limit=…&offset=…`
- **aqx_p_432** 即時測站（每小時）：sitename、county、aqi、pollutant、status、pm2.5、pm2.5_avg、pm10、o3_8hr、longitude、latitude、siteid、publishtime…
- **aqf_p_01** 空品預報（每天 10:30、16:30、22:00，未來 4 天）：area（空品區）、forecastdate、aqi、majorpollutant、content、publishtime
- 金鑰：免費註冊、效期 1 年（要提醒倬續期），會員單一 API 每天 5000 次；實測不帶金鑰會被拒絕。
- AQI 分級（2025-01-01 新版；括號為 PM2.5 日平均 μg/m³）：0–50 綠（0–12.4）、51–100 黃（12.5–30.4）、**101–150 橘（30.5–50.4）＝警戒門檻**、151–200 紅、201–300 紫、301–500 褐紅。

## 4. 人事總處：辦公日曆表（F08）

- data.gov.tw 資料集 14718，CSV（UTF-8 BOM）：西元日期、星期、是否放假（0 上班／2 放假）、備註。已有 115、116 年；曾發布修正檔。
- 做法：APP 內建當年＋次年 CSV，每月檢查一次有沒有新檔或修正檔；查不到時退回「週一到週五上班」。
- 未確認：每年幾月發布。

## 5. 停班停課（F13）

- data.gov.tw 資料集 20457（來源 NCDR）：CAP RSS `https://alerts.ncdr.nat.gov.tw/RssAtomFeed.ashx?AlertType=33`。
- 人事總處 `https://www.dgpa.gov.tw/typh/daily/nds.html` 只有 HTML 表格（縣市名稱／停班停課情形），當備援解析。
- 未確認：NCDR CAP 的欄位結構與更新頻率。

## 6. Open-Meteo（備援）

- 免金鑰；daily 有 apparent_temperature_max/min、precipitation_probability_max、uv_index_max、wind_speed_10m_max（預設 km/h，要換算）。
- 免費版**限非商業使用**（每天 1 萬次）、CC BY 4.0。本 APP 是倬個人自用、無廣告，符合；**日後若上架或加廣告要改付費或拿掉**。

## 對設計的影響

1. 颱風影響判斷（F11）用 Circle15ms（七級風半徑）＋Radius70PercentProbability，見 `docs/AGENT_API.md`。
2. 晚間播報的明天資料：溫度、降雨用 3 天資料（F-D0047-093 的 3 天版），紫外線用 1 週資料，兩次呼叫合併。
3. 金鑰名稱：`CWA_API_KEY`、`MOENV_API_KEY`（APP 內 Keystore 加密，開發用 dotenvx）。
