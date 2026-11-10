package tw.hoonsor.weather.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 地點種類：auto 只出現在請求，實際查詢一定解析成其中一個 */
enum class PlaceKind(val key: String) {
    CURRENT("current"), HOME("home"), SCHOOL("school");

    companion object {
        fun of(key: String?): PlaceKind? = entries.firstOrNull { it.key == key }
    }
}

/** 鄉鎮層級的地點。座標是鄉鎮中心點（內建表），不是使用者的實際位置 */
data class Town(
    val county: String,      // 桃園市
    val name: String,        // 中壢區
    val lat: Double,
    val lon: Double,
) {
    val full get() = county + name
}

/** 一天的預報（明天播報、7 日預報共用） */
data class DayForecast(
    val date: LocalDate,
    val weather: String,          // 多雲時晴
    val weatherCode: Int,         // 氣象署天氣代碼
    val tempMin: Int,
    val tempMax: Int,
    val feelsMin: Int,
    val feelsMax: Int,
    val rainDay: Int?,            // 白天（06–18）降雨機率 %，資料缺時 null
    val rainNight: Int?,          // 晚上（18–06）
    val uvIndex: Int? = null,
    val beaufortMax: Int? = null,
)

data class NowWeather(
    val time: LocalDateTime,
    val temp: Int,
    val feels: Int,
    val weather: String,
    val weatherCode: Int,
    val humidity: Int?,
    val rainNext3h: Int?,
)

data class AirReading(
    val aqi: Int,
    val pm25: Double? = null,
    val area: String,             // 測站名或空品區名
    val isForecast: Boolean,
)

/** 颱風路徑預報點（W-C0034-005 ForecastData 一筆） */
data class TrackPoint(
    val time: LocalDateTime,
    val lat: Double,
    val lon: Double,
    val radius7Km: Int?,          // 七級風暴風半徑
    val radius70Km: Int?,         // 70% 機率半徑
)

data class Typhoon(
    val name: String,             // 銀杏
    val kind: String,             // 中度颱風、熱帶性低氣壓
    val position: String,         // 恆春東南方約 620 公里
    val track: List<TrackPoint>,
    val warning: Warning,
    val landWarningCounties: Set<String> = emptySet(),
) {
    enum class Warning(val label: String) { NONE("尚未發布警報"), SEA("已發布海上警報"), SEA_LAND("已發布海上陸上警報") }
}
