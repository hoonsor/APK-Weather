package tw.hoonsor.weather.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.R
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.ClothingItem
import tw.hoonsor.weather.domain.Uv
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.WeatherIcon
import tw.hoonsor.weather.ui.MainActivity
import tw.hoonsor.weather.ui.theme.Palette

/** 推送與共用：所有小工具的內容都由這裡算好寫入（DESIGN.md 第 1、5、6 節） */
object WeatherWidgets {
    val KEY_DATA = stringPreferencesKey("data")
    val KEY_MODE = stringPreferencesKey("mode")

    suspend fun pushAll(context: Context) {
        Graph.init(context)
        val repo = Graph.repo
        val now = repo.cached(PlaceKind.CURRENT) ?: repo.cached(PlaceKind.HOME)
        val kind = runCatching { repo.autoKind() }.getOrDefault(PlaceKind.CURRENT)
        val tom = repo.cached(kind) ?: runCatching { repo.snapshot(kind, maxAgeMin = 60) }.getOrNull()
        val ty = runCatching { repo.typhoons(maxAgeMin = 60).list.isNotEmpty() }.getOrDefault(false)
        val json = WidgetData.build(now, kind, tom, ty, repo.settings.shift).toJson()
        val m = GlanceAppWidgetManager(context)
        listOf(Weather41() to Weather41::class.java, WeatherNow22() to WeatherNow22::class.java, WeatherTomorrow22() to WeatherTomorrow22::class.java)
            .forEach { (w, cls) ->
                m.getGlanceIds(cls).forEach { id ->
                    updateAppWidgetState(context, id) { it[KEY_DATA] = json }
                    w.update(context, id)
                }
            }
    }

    fun icon(i: WeatherIcon) = when (i) {
        WeatherIcon.SUN -> R.drawable.ic_w_sun
        WeatherIcon.PARTLY -> R.drawable.ic_w_partly
        WeatherIcon.CLOUD -> R.drawable.ic_w_cloud
        WeatherIcon.RAIN -> R.drawable.ic_w_rain
        WeatherIcon.THUNDER -> R.drawable.ic_w_thunder
        WeatherIcon.FOG -> R.drawable.ic_w_fog
        WeatherIcon.SNOW -> R.drawable.ic_w_snow
        WeatherIcon.MOON -> R.drawable.ic_w_moon
        WeatherIcon.PARTLY_NIGHT -> R.drawable.ic_w_partly_night
    }

    fun cloth(c: ClothingItem) = when (c) {
        ClothingItem.TEE -> R.drawable.ic_c_tee
        ClothingItem.LONG_SLEEVE -> R.drawable.ic_c_long
        ClothingItem.LIGHT_JACKET, ClothingItem.JACKET -> R.drawable.ic_c_jacket
        ClothingItem.HEAVY_COAT -> R.drawable.ic_c_coat
        ClothingItem.UMBRELLA, ClothingItem.FOLDING_UMBRELLA -> R.drawable.ic_c_umbrella
        ClothingItem.SUNSCREEN -> R.drawable.ic_c_uv
        ClothingItem.WINDBREAKER -> R.drawable.ic_c_wind
        ClothingItem.MASK -> R.drawable.ic_c_mask
    }

    fun open(context: Context, section: String? = null) = actionStartActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (section != null) putExtra(MainActivity.EXTRA_SECTION, section) }.setAction("open.$section"),
    )
}

private val ink = ColorProvider(Color.White)
private val soft = ColorProvider(Palette.inkSoft)
private val mute = ColorProvider(Palette.mute)
private val amber = ColorProvider(Palette.amberSoft)
private val rainC = ColorProvider(Palette.rain)
private val typhoonC = ColorProvider(Palette.typhoon)

@Composable
private fun T(s: String, size: TextUnit, color: ColorProvider = ink, bold: Boolean = false, modifier: GlanceModifier = GlanceModifier) =
    Text(s, modifier, style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal), maxLines = 1)

@Composable
private fun Img(res: Int, size: Int) = Image(ImageProvider(res), null, GlanceModifier.size(size.dp))

@Composable
private fun Badge(text: String) {
    Row(GlanceModifier.background(ImageProvider(R.drawable.bg_badge)).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_w_storm), null, GlanceModifier.size(11.dp))
        T(text, 10.sp, typhoonC)
    }
}

private fun deg(v: Int?) = v?.let { "$it°" } ?: "--"

// ---------------- 4×1 ----------------

class Weather41 : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    /** 用實際大小排版：桌面格子矮時切到精簡排法 */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.init(context)
        val defMode = Graph.settings.widgetDefault
        provideContent {
            val prefs = currentState<Preferences>()
            val d = prefs[WeatherWidgets.KEY_DATA]?.let(WidgetData::fromJson) ?: WidgetData.EMPTY
            val mode = prefs[WeatherWidgets.KEY_MODE] ?: defMode
            if (mode == "C") ModeC(context, d) else ModeA(context, d)
        }
    }

    @Composable
    private fun Switch(mode: String) {
        Box(
            GlanceModifier.width(26.dp).height(64.dp).background(ImageProvider(R.drawable.bg_switch)).clickable(actionRunCallback<ToggleMode>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(if (mode == "A") R.drawable.ic_dot_on else R.drawable.ic_dot_off), null, GlanceModifier.width(6.dp).height(if (mode == "A") 16.dp else 6.dp))
                Spacer(GlanceModifier.height(6.dp))
                Image(ImageProvider(if (mode == "C") R.drawable.ic_dot_on else R.drawable.ic_dot_off), null, GlanceModifier.width(6.dp).height(if (mode == "C") 16.dp else 6.dp))
            }
        }
    }

    /**
     * 版面 A：左「現在」（地點、溫度、今天高低溫）；旁邊一欄上面天氣圖示＋水滴降雨機率、下面外出穿著圖示；
     * 中間未來 4 天（高溫、低溫各一行，倬要求維持原樣）；右側切換鈕。格子矮（< 100dp）時字與圖示縮小。
     */
    @Composable
    private fun ModeA(context: Context, d: WidgetData) {
        val compact = LocalSize.current.height < 100.dp
        val vPad = if (compact) 3.dp else 6.dp
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_scrim)).padding(start = 10.dp, end = 4.dp, top = vPad, bottom = vPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.width(86.dp).fillMaxHeight().clickable(WeatherWidgets.open(context)), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(d.town, if (compact) 12.sp else 13.sp)
                    if (d.badge != null) { Spacer(GlanceModifier.width(4.dp)); Badge(d.badge) }
                }
                T(deg(d.temp), if (compact) 30.sp else 36.sp)
                T(if (d.stale) d.updated else "${deg(d.hi)} / ${deg(d.lo)}", if (compact) 11.sp else 12.sp, if (d.stale) mute else soft)
            }
            // 倬 2026-11-27：溫度右邊的空位補滿：上面天氣圖示＋水滴降雨機率，下面外出穿著圖示
            Column(
                GlanceModifier.width(84.dp).fillMaxHeight().clickable(WeatherWidgets.open(context, "tomorrow")),
                verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Img(WeatherWidgets.icon(d.icon), if (compact) 28 else 34)
                    Spacer(GlanceModifier.width(3.dp))
                    RainPct(d.rainNow, if (compact) 12.sp else 13.sp, if (compact) 12 else 14)
                }
                if (d.wearItems.isNotEmpty()) {
                    Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        d.wearItems.take(3).forEachIndexed { i, it ->
                            if (i > 0) Spacer(GlanceModifier.width(3.dp))
                            Img(WeatherWidgets.cloth(it), if (compact) 18 else 22)
                        }
                        // 只有一件時空間夠，加上文字（倬看不懂外套圖示）
                        if (d.wearItems.size == 1) T(" " + d.wearItems[0].label, 12.sp, soft)
                    }
                }
            }
            Row(GlanceModifier.defaultWeight().fillMaxHeight().clickable(WeatherWidgets.open(context, "week")), verticalAlignment = Alignment.CenterVertically) {
                d.days.forEach { day ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        // 倬 2026-11-27：4 天維持原本樣式（高溫、低溫各一行）；格子矮時只把圖示縮小，不併行
                        T(day.name, 12.sp, soft)
                        Img(WeatherWidgets.icon(day.icon), if (compact) 20 else 24)
                        T("${day.hi}°", 12.sp)
                        T("${day.lo}°", 12.sp, mute)
                    }
                }
            }
            Switch("A")
        }
    }

    /**
     * 版面 C：三等分（倬 2026-11-27 要求）：明天天氣｜明天紫外線｜明天空品（AQI 預報＋附近測站現在的 PM2.5）。
     * 環境部只有「空品區 AQI 預報」，沒有 PM2.5 濃度預報，所以 PM2.5 顯示的是現在值。
     */
    @Composable
    private fun ModeC(context: Context, d: WidgetData) {
        val compact = LocalSize.current.height < 100.dp
        val big = if (compact) 18.sp else 22.sp
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_panel_c)).padding(start = 7.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 明天天氣
            Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_block_tomorrow)).padding(horizontal = 7.dp, vertical = 4.dp).clickable(WeatherWidgets.open(context, "tomorrow"))) {
                // 倬 2026-11-27：氣溫放大、獨佔中間一整行；天氣圖示移到標題列右側，降雨機率靠右下
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        if (d.badge != null) { T("明天", 11.sp, amber); Spacer(GlanceModifier.width(3.dp)); Badge(d.badge) } else T(d.tomorrowLabel, 11.sp, amber)
                    }
                    Img(WeatherWidgets.icon(d.tomorrowIcon), if (compact) 20 else 24)
                }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(if (d.tomorrowLo != null) "${d.tomorrowLo}–${d.tomorrowHi}°" else "--", if (compact) 21.sp else 25.sp)
                }
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    RainPct(listOfNotNull(d.tomorrowRainDay, d.tomorrowRainNight).maxOrNull(), 11.sp)
                }
            }
            Spacer(GlanceModifier.width(5.dp))
            // 明天紫外線
            val uvLevel = d.tomorrowUv?.let(Uv::level)
            val uvC = uvLevel?.let { ColorProvider(Palette.uv(it)) } ?: mute
            Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_block)).padding(horizontal = 7.dp, vertical = 4.dp).clickable(WeatherWidgets.open(context, "tomorrow"))) {
                Row(verticalAlignment = Alignment.CenterVertically) { Img(R.drawable.ic_c_uv, 12); T(" 紫外線", 11.sp, soft) }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(d.tomorrowUv?.toString() ?: "--", big, uvC)
                    Spacer(GlanceModifier.width(4.dp))
                    T(uvLevel?.label ?: "", 11.sp, uvC)
                }
                T(if (d.tomorrowUv == null) "沒有資料" else if (d.tomorrowUv >= 6) "中午要防曬" else "不用特別防曬", 11.sp, soft)
            }
            Spacer(GlanceModifier.width(5.dp))
            // 明天空品
            val aqiLevel = d.tomorrowAqi?.let(Aqi::level)
            val aqiC = aqiLevel?.let { ColorProvider(Palette.aqi(it)) } ?: mute
            Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_block)).padding(horizontal = 7.dp, vertical = 4.dp).clickable(WeatherWidgets.open(context))) {
                Row(verticalAlignment = Alignment.CenterVertically) { Img(R.drawable.ic_haze, 12); T(" 空品", 11.sp, soft) }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(d.tomorrowAqi?.toString() ?: "--", big, aqiC)
                    Spacer(GlanceModifier.width(4.dp))
                    T(aqiLevel?.let(::aqiShort) ?: "", 11.sp, aqiC)
                }
                T(d.pm25Now?.let { "PM2.5 $it" } ?: "沒有空品資料", 11.sp, soft)
            }
            Switch("C")
        }
    }
}

/** 小工具空間小，AQI 等級用兩個字：良好、普通，橘色以上直接講顏色 */
private fun aqiShort(l: Aqi.Level) = when (l) {
    Aqi.Level.GREEN -> "良好"; Aqi.Level.YELLOW -> "普通"; Aqi.Level.ORANGE -> "橘色"
    Aqi.Level.RED -> "紅色"; Aqi.Level.PURPLE -> "紫色"; Aqi.Level.MAROON -> "褐紅"
}

/** 水滴＋降雨機率（沒有資料就不顯示） */
@Composable
private fun RainPct(p: Int?, size: TextUnit, drop: Int = 10) {
    if (p == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Img(R.drawable.ic_drop, drop)
        T(" $p%", size, rainC)
    }
}

/** 點右側圓點：切換這一個小工具的版面（每個小工具各自記住） */
class ToggleMode : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Graph.init(context)
        val def = Graph.settings.widgetDefault
        updateAppWidgetState(context, glanceId) { p -> p[WeatherWidgets.KEY_MODE] = if ((p[WeatherWidgets.KEY_MODE] ?: def) == "A") "C" else "A" }
        Weather41().update(context, glanceId)
    }
}

// ---------------- 2×2 ----------------

class WeatherNow22 : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val d = currentState<Preferences>()[WeatherWidgets.KEY_DATA]?.let(WidgetData::fromJson) ?: WidgetData.EMPTY
            Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_glass)).padding(12.dp).clickable(WeatherWidgets.open(context))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(d.town, 12.sp, soft)
                    if (d.badge != null) { Spacer(GlanceModifier.width(4.dp)); Badge(d.badge) }
                }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(deg(d.temp), 38.sp, modifier = GlanceModifier.defaultWeight())
                    Img(WeatherWidgets.icon(d.icon), 40)
                }
                T("${deg(d.hi)}/${deg(d.lo)}" + (d.rainNow?.let { " · 雨 $it%" } ?: ""), 12.sp, soft)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    d.tomorrowItems.take(3).forEach { Img(WeatherWidgets.cloth(it), 17) }
                    T(" 明天" + d.tomorrowItems.take(2).joinToString("＋", prefix = " ") { it.label }, 11.sp, soft)
                }
            }
        }
    }
}

class WeatherTomorrow22 : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val d = currentState<Preferences>()[WeatherWidgets.KEY_DATA]?.let(WidgetData::fromJson) ?: WidgetData.EMPTY
            Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_glass)).padding(12.dp).clickable(WeatherWidgets.open(context, "tomorrow"))) {
                T(d.tomorrowLabel, 12.sp, amber)
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(if (d.tomorrowLo != null) "${d.tomorrowLo}–${d.tomorrowHi}°" else "--", 28.sp, modifier = GlanceModifier.defaultWeight())
                    Img(WeatherWidgets.icon(d.tomorrowIcon), 34)
                }
                T("降雨 ${d.tomorrowRainDay?.let { "$it%" } ?: "--"} → ${d.tomorrowRainNight?.let { "$it%" } ?: "--"}", 12.sp, rainC)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    d.tomorrowItems.take(3).forEach { Img(WeatherWidgets.cloth(it), 18) }
                    T(" " + d.tomorrowItems.take(3).joinToString("＋") { it.label }, 11.sp, soft)
                }
            }
        }
    }
}

class Weather41Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Weather41()
    override fun onEnabled(context: Context) { super.onEnabled(context); tw.hoonsor.weather.work.Scheduler.refreshNow(context) }
}
class WeatherNow22Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherNow22()
    override fun onEnabled(context: Context) { super.onEnabled(context); tw.hoonsor.weather.work.Scheduler.refreshNow(context) }
}
class WeatherTomorrow22Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherTomorrow22()
    override fun onEnabled(context: Context) { super.onEnabled(context); tw.hoonsor.weather.work.Scheduler.refreshNow(context) }
}
