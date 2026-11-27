package tw.hoonsor.weather.domain

/** AQI 分級（環境部 2025-01-01 新版）。101 以上（橘）才播報（F09） */
object Aqi {
    const val ALERT = 101

    enum class Level(val color: String, val effect: String, val advice: String) {
        GREEN("綠色", "良好", "不用戴口罩"),
        YELLOW("黃色", "普通", "極特殊敏感的人注意一下"),
        ORANGE("橘色", "對敏感族群不健康", "外出建議戴口罩"),
        RED("紅色", "對所有族群不健康", "外出戴口罩，減少戶外活動"),
        PURPLE("紫色", "非常不健康", "盡量待在室內"),
        MAROON("褐紅色", "危害健康", "避免外出"),
    }

    fun level(aqi: Int): Level = when {
        aqi <= 50 -> Level.GREEN
        aqi <= 100 -> Level.YELLOW
        aqi <= 150 -> Level.ORANGE
        aqi <= 200 -> Level.RED
        aqi <= 300 -> Level.PURPLE
        else -> Level.MAROON
    }

    fun isAlert(aqi: Int) = aqi >= ALERT
}

/** 紫外線指數分級（氣象署：0–2 低量、3–5 中量、6–7 高量、8–10 過量、11 以上危險） */
object Uv {
    enum class Level(val label: String) { LOW("低量"), MODERATE("中量"), HIGH("高量"), VERY_HIGH("過量"), EXTREME("危險") }

    fun level(uv: Int): Level = when {
        uv <= 2 -> Level.LOW
        uv <= 5 -> Level.MODERATE
        uv <= 7 -> Level.HIGH
        uv <= 10 -> Level.VERY_HIGH
        else -> Level.EXTREME
    }
}
