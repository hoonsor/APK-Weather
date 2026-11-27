package tw.hoonsor.weather.ui.theme

import androidx.compose.ui.graphics.Color

/** 色票：照抄 docs/DESIGN.md 第 2 節，改色先改文件 */
object Palette {
    val bgTop = Color.hsl(226f, .45f, .16f)
    val bgMid = Color.hsl(232f, .38f, .09f)
    val bgBottom = Color.hsl(236f, .30f, .07f)
    val card = Color.hsl(226f, .40f, .22f, .35f)
    val cardLine = Color.hsl(220f, .50f, .80f, .10f)
    val ink = Color.hsl(220f, .20f, .92f)
    val inkSoft = Color.hsl(220f, .18f, .80f)
    val mute = Color.hsl(220f, .12f, .62f)
    val accent = Color.hsl(198f, .70f, .75f)
    val accentBg = Color.hsl(198f, .60f, .40f, .35f)
    val amber = Color.hsl(38f, .90f, .68f)
    val amberSoft = Color.hsl(35f, .70f, .78f)
    val rain = Color.hsl(205f, .85f, .65f)
    val warn = Color.hsl(30f, .90f, .72f)
    val danger = Color.hsl(8f, .85f, .70f)
    val typhoon = Color.hsl(18f, .90f, .78f)
    val ok = Color.hsl(150f, .50f, .65f)
    val panelC1 = Color.hsl(212f, .40f, .18f, .95f)
    val panelC2 = Color.hsl(250f, .28f, .16f, .95f)

    /** AQI 顏色（DESIGN.md 第 2 節，環境部 2025 新版） */
    fun aqi(l: tw.hoonsor.weather.domain.Aqi.Level): Color = when (l) {
        tw.hoonsor.weather.domain.Aqi.Level.GREEN -> ok
        tw.hoonsor.weather.domain.Aqi.Level.YELLOW -> Color.hsl(50f, .85f, .62f)
        tw.hoonsor.weather.domain.Aqi.Level.ORANGE -> warn
        tw.hoonsor.weather.domain.Aqi.Level.RED -> Color.hsl(0f, .75f, .62f)
        tw.hoonsor.weather.domain.Aqi.Level.PURPLE -> Color.hsl(285f, .45f, .62f)
        tw.hoonsor.weather.domain.Aqi.Level.MAROON -> Color.hsl(345f, .5f, .42f)
    }

    /** 紫外線顏色：低量 ok、中量黃、高量 warn、過量 danger、危險紫 */
    fun uv(l: tw.hoonsor.weather.domain.Uv.Level): Color = when (l) {
        tw.hoonsor.weather.domain.Uv.Level.LOW -> ok
        tw.hoonsor.weather.domain.Uv.Level.MODERATE -> Color.hsl(50f, .85f, .62f)
        tw.hoonsor.weather.domain.Uv.Level.HIGH -> warn
        tw.hoonsor.weather.domain.Uv.Level.VERY_HIGH -> danger
        tw.hoonsor.weather.domain.Uv.Level.EXTREME -> Color.hsl(285f, .45f, .62f)
    }
}
