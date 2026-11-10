package tw.hoonsor.weather.domain

/**
 * 氣象署天氣代碼（WeatherCode）→ 圖示。
 * 對照依氣象署「天氣現象代碼表」：01 晴、02～03 晴時多雲／多雲時晴、04～07 多雲／陰、
 * 08～22、29～41 有雨（其中含雷雨的另列）、23、37、42 有雪、24～28、38、39 有霧。
 * 未確認：代碼表是否有新增代碼，第 5 階段用實際資料核對；不認得的代碼用文字判斷。
 */
enum class WeatherIcon { SUN, PARTLY, CLOUD, RAIN, THUNDER, FOG, SNOW, MOON, PARTLY_NIGHT }

object WeatherIcons {
    private val thunder = setOf(15, 16, 17, 18, 21, 22, 33, 34, 35, 36, 41)
    private val snow = setOf(23, 37, 42)
    private val fog = setOf(24, 25, 26, 27, 28, 38, 39)

    fun of(code: Int, text: String = "", night: Boolean = false): WeatherIcon {
        val icon = when {
            code == 1 -> WeatherIcon.SUN
            code in 2..3 -> WeatherIcon.PARTLY
            code in 4..7 -> WeatherIcon.CLOUD
            code in thunder -> WeatherIcon.THUNDER
            code in snow -> WeatherIcon.SNOW
            code in fog -> WeatherIcon.FOG
            code in 8..41 -> WeatherIcon.RAIN
            "雷" in text -> WeatherIcon.THUNDER
            "雨" in text -> WeatherIcon.RAIN
            "霧" in text -> WeatherIcon.FOG
            "雪" in text -> WeatherIcon.SNOW
            "晴" in text && "雲" in text -> WeatherIcon.PARTLY
            "晴" in text -> WeatherIcon.SUN
            else -> WeatherIcon.CLOUD
        }
        return if (!night) icon else when (icon) {
            WeatherIcon.SUN -> WeatherIcon.MOON
            WeatherIcon.PARTLY -> WeatherIcon.PARTLY_NIGHT
            else -> icon
        }
    }
}
