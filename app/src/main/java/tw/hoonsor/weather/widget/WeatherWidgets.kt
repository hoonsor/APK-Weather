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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import tw.hoonsor.weather.domain.ClothingItem
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

    @Composable
    private fun ModeA(context: Context, d: WidgetData) {
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_scrim)).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.width(108.dp).fillMaxHeight().clickable(WeatherWidgets.open(context)), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(d.town, 13.sp)
                    if (d.badge != null) { Spacer(GlanceModifier.width(4.dp)); Badge(d.badge) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(deg(d.temp), 36.sp)
                    Spacer(GlanceModifier.width(2.dp))
                    Img(WeatherWidgets.icon(d.icon), 30)
                }
                T(if (d.stale) d.updated else "${deg(d.hi)} / ${deg(d.lo)}", 12.sp, if (d.stale) mute else soft)
            }
            Row(GlanceModifier.defaultWeight().fillMaxHeight().clickable(WeatherWidgets.open(context, "week")), verticalAlignment = Alignment.CenterVertically) {
                d.days.forEach { day ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(day.name, 12.sp, soft)
                        Img(WeatherWidgets.icon(day.icon), 24)
                        T("${day.hi}°", 12.sp)
                        T("${day.lo}°", 12.sp, mute)
                    }
                }
            }
            Switch("A")
        }
    }

    @Composable
    private fun ModeC(context: Context, d: WidgetData) {
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bg_panel_c)).padding(start = 7.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_block)).padding(horizontal = 9.dp, vertical = 5.dp).clickable(WeatherWidgets.open(context))) {
                T("現在 · ${d.town}", 11.sp, soft)
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(deg(d.temp), 26.sp, modifier = GlanceModifier.defaultWeight())
                    Img(WeatherWidgets.icon(d.icon), 28)
                }
                T("${deg(d.hi)}/${deg(d.lo)}" + (d.rainNow?.let { " · 雨 $it%" } ?: ""), 11.sp, soft)
            }
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.bg_block_tomorrow)).padding(horizontal = 9.dp, vertical = 5.dp).clickable(WeatherWidgets.open(context, "tomorrow"))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(d.tomorrowLabel, 11.sp, amber)
                    if (d.badge != null) { Spacer(GlanceModifier.width(3.dp)); Badge(d.badge) }
                }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(if (d.tomorrowLo != null) "${d.tomorrowLo}–${d.tomorrowHi}°" else "--", 22.sp, modifier = GlanceModifier.defaultWeight())
                    Img(WeatherWidgets.icon(d.tomorrowIcon), 28)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    d.tomorrowItems.take(3).forEach { Img(WeatherWidgets.cloth(it), 15) }
                    val r = listOfNotNull(d.tomorrowRainDay, d.tomorrowRainNight).maxOrNull()
                    if (r != null) T(" 雨 $r%", 11.sp, rainC)
                }
            }
            Switch("C")
        }
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
                    T(" 明天", 11.sp, soft)
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
                Row { d.tomorrowItems.take(4).forEach { Img(WeatherWidgets.cloth(it), 18) } }
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
