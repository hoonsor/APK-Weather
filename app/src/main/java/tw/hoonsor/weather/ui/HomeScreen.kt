package tw.hoonsor.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.weather.R
import tw.hoonsor.weather.data.TyphoonState
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.Broadcast
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.Sun
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.domain.TyphoonImpact
import tw.hoonsor.weather.domain.WeatherIcons
import tw.hoonsor.weather.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 主畫面要的資料（MainActivity 載入後傳進來） */
data class HomeState(
    val selected: PlaceKind = PlaceKind.CURRENT,
    val autoKind: PlaceKind = PlaceKind.CURRENT,
    val snap: Snapshot? = null,
    val tomorrowSnap: Snapshot? = null,
    val tomorrowKind: PlaceKind = PlaceKind.CURRENT,
    val tomorrowText: String? = null,
    val typhoon: TyphoonState? = null,
    val typhoonText: String? = null,
    val impacts: List<Pair<String, TyphoonImpact.Level>> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasHome: Boolean = false,
    val hasSchool: Boolean = false,
    val hasCwa: Boolean = false,
    val hasMoenv: Boolean = false,
    val shift: Int = 0,
)

/** LazyColumn 的項目順序（小工具點擊要捲到 week／tomorrow） */
object HomeItems {
    const val TOMORROW = 3
    const val WEEK = 4
}

@Composable
fun HomeScreen(
    st: HomeState,
    list: LazyListState,
    onSelect: (PlaceKind) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Stars()
        LazyColumn(state = list, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "bar") { TopBar(st, onSelect, onRefresh, onSettings) }
            item(key = "hero") { Hero(st) }
            item(key = "typhoon") { TyphoonCard(st) }
            item(key = "tomorrow") { TomorrowCard(st, onSpeak) }
            item(key = "week") { WeekCard(st.snap) }
            item(key = "hourly") { HourlyCard(st.snap) }
            item(key = "air") { AirCard(st) }
            item(key = "minis") { Minis(st.snap) }
            item(key = "source") { SourceNote(st) }
        }
    }
}

@Composable
private fun Stars() {
    Canvas(Modifier.fillMaxSize()) {
        var x = 7L
        repeat(80) {
            x = (x * 9301 + 49297) % 233280; val a = x / 233280f
            x = (x * 9301 + 49297) % 233280; val b = x / 233280f
            x = (x * 9301 + 49297) % 233280; val r = (x / 233280f) * 1.1f + .3f
            drawCircle(Color.White.copy(alpha = (.12f + r / 5f)), r * density, Offset(a * size.width, b * size.height * .7f))
        }
    }
}

@Composable
private fun TopBar(st: HomeState, onSelect: (PlaceKind) -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("目前位置", st.selected == PlaceKind.CURRENT) { onSelect(PlaceKind.CURRENT) }
        if (st.hasHome) Chip("家" + if (st.autoKind == PlaceKind.HOME) " ●" else "", st.selected == PlaceKind.HOME) { onSelect(PlaceKind.HOME) }
        if (st.hasSchool) Chip("學校" + if (st.autoKind == PlaceKind.SCHOOL) " ●" else "", st.selected == PlaceKind.SCHOOL) { onSelect(PlaceKind.SCHOOL) }
        Spacer(Modifier.weight(1f))
        if (st.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Palette.accent, strokeWidth = 2.dp)
        else IconBtn(R.drawable.ic_refresh, onRefresh)
        IconBtn(R.drawable.ic_gear, onSettings)
    }
}

@Composable
private fun IconBtn(res: Int, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).border(1.dp, Palette.mute.copy(alpha = .35f), RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(res), null, Modifier.size(18.dp))
    }
}

@Composable
private fun Hero(st: HomeState) {
    val s = st.snap
    if (s == null) {
        Card { Body(if (st.loading) "正在抓天氣資料…" else st.error ?: "還沒有資料，按右上角重新整理。") }
        return
    }
    val now = LocalDateTime.now()
    val n = s.now(now)
    val range = s.todayRange(now)
    Column {
        if (st.error != null) Body("⚠ ${st.error}", Palette.warn, 12)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.town.name, color = Palette.inkSoft, fontSize = 15.sp)
                Text("${n?.temp ?: "--"}°", color = Palette.ink, fontSize = 84.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 84.sp)
                Text("${n?.weather?.ifBlank { null } ?: "—"} · 體感 ${n?.feels ?: "--"}°", color = Palette.inkSoft, fontSize = 14.sp)
                if (range != null) Text("${range.second}° / ${range.first}°", color = Palette.inkSoft, fontSize = 14.sp)
            }
            n?.let { Weather(WeatherIcons.of(it.weatherCode, it.weather, now.hour >= 18 || now.hour < 6), 116.dp) }
        }
        val h = s.hourAt(now)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Fact("濕度", h?.humidity?.let { "$it%" } ?: "--", Modifier.weight(1f))
            Fact("風", h?.let { listOfNotNull(it.windDir, it.beaufort?.let { b -> "$b 級" }).joinToString(" ") }?.ifBlank { null } ?: "--", Modifier.weight(1f))
            Fact("3 小時雨", n?.rainNext3h?.let { "$it%" } ?: "--", Modifier.weight(1f))
            Fact("紫外線", s.day(now.toLocalDate())?.uvIndex?.toString() ?: "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Fact(k: String, v: String, modifier: Modifier) {
    Column(modifier.background(Palette.accentBg.copy(alpha = .12f), RoundedCornerShape(12.dp)).border(1.dp, Palette.cardLine, RoundedCornerShape(12.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(k, color = Palette.mute, fontSize = 11.5.sp)
        Text(v, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TyphoonCard(st: HomeState) {
    val t: Typhoon = st.typhoon?.list?.firstOrNull() ?: return
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color.hsl(14f, .6f, .30f, .35f), Color.hsl(260f, .3f, .18f, .35f))), RoundedCornerShape(20.dp))
            .border(1.dp, Color.hsl(14f, .7f, .55f, .55f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_w_storm), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${t.name} · ${t.kind}", color = Palette.typhoon, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t.warning.label.removePrefix("已發布"), color = Palette.mute, fontSize = 12.sp)
        }
        st.typhoonText?.let { Body(it, Color.hsl(20f, .3f, .85f), 13) }
        if (st.impacts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            st.impacts.forEach { (label, lv) ->
                Column(Modifier.weight(1f).background(Color.hsl(230f, .3f, .08f, .4f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(label, color = Palette.mute, fontSize = 12.sp)
                    Text(lv.label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = when (lv) { TyphoonImpact.Level.DIRECT -> Palette.danger; TyphoonImpact.Level.OUTER -> Palette.warn; else -> Palette.ok })
                }
            }
        }
    }
}

private val md = DateTimeFormatter.ofPattern("M/d")

@Composable
private fun TomorrowCard(st: HomeState, onSpeak: (String) -> Unit) {
    val s = st.tomorrowSnap ?: st.snap ?: return
    val date = LocalDate.now().plusDays(1)
    val d = s.day(date) ?: return
    val where = when (st.tomorrowKind) { PlaceKind.SCHOOL -> "學校"; PlaceKind.HOME -> "家"; else -> s.town.name }
    val advice = ClothingRules.advise(d, s.airTomorrow?.aqi, st.shift)
    Card {
        CardTitle("明天 ${date.format(md)} ${Broadcast.weekdayName(date).replace("星期", "週")} · $where") {
            Row(
                Modifier.border(1.dp, Palette.accent.copy(alpha = .5f), CircleShape).clickable { onSpeak(st.tomorrowText ?: Broadcast.tomorrow(d, st.tomorrowKind, s.town, advice)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_play), null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text("唸給我聽", color = Palette.accent, fontSize = 12.5.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Weather(WeatherIcons.of(d.weatherCode, d.weather), 64.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${d.tempMin}–${d.tempMax}°", color = Palette.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 42.sp)
                Text("${d.weather} · 體感 ${d.feelsMin}–${d.feelsMax}°", color = Palette.mute, fontSize = 13.sp)
            }
        }
        Body(st.tomorrowText ?: Broadcast.tomorrow(d, st.tomorrowKind, s.town, advice))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            advice.items.forEach { item ->
                Row(
                    Modifier.background(Color.hsl(40f, .6f, .6f, .10f), RoundedCornerShape(12.dp)).border(1.dp, Color.hsl(40f, .6f, .7f, .22f), RoundedCornerShape(12.dp))
                        .padding(start = 7.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Cloth(item); Spacer(Modifier.width(5.dp)); Text(item.label, color = Color.hsl(40f, .7f, .85f), fontSize = 12.5.sp) }
            }
        }
    }
}

@Composable
private fun WeekCard(s: Snapshot?) {
    val days = s?.days?.filter { !it.date.isBefore(LocalDate.now().plusDays(1)) }?.take(7) ?: return
    if (days.isEmpty()) return
    Card {
        CardTitle("7 日預報", "每天的穿著建議")
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Broadcast.weekdayName(d.date).replace("星期", "週"), color = Palette.inkSoft, fontSize = 12.sp)
                    Text(d.date.format(md), color = Palette.mute, fontSize = 10.5.sp)
                    Weather(WeatherIcons.of(d.weatherCode, d.weather), 30.dp)
                    Text("${d.tempMax}°", color = Palette.ink, fontSize = 12.sp)
                    Text("${d.tempMin}°", color = Palette.mute, fontSize = 12.sp)
                    Text("${maxOf(d.rainDay ?: 0, d.rainNight ?: 0)}%", color = Palette.rain, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    ClothingRules.advise(d).items.filter { it.ordinal <= 6 }.take(2).forEach { Cloth(it, 16.dp) }
                }
            }
        }
    }
}

@Composable
private fun HourlyCard(s: Snapshot?) {
    val now = LocalDateTime.now().minusHours(2)
    val pts = s?.hours?.filter { it.time.isAfter(now) }?.take(9) ?: return
    if (pts.size < 3) return
    Card {
        CardTitle("逐時", "溫度與降雨機率")
        val tempC = Palette.amber; val ink = Palette.ink; val mute = Palette.mute; val rainC = Palette.rain
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val p = 16.dp.toPx(); val step = (size.width - 2 * p) / (pts.size - 1)
            val mx = pts.maxOf { it.temp }; val mn = pts.minOf { it.temp }
            fun y(t: Int) = 30.dp.toPx() + if (mx == mn) 25.dp.toPx() else (mx - t).toFloat() / (mx - mn) * 55.dp.toPx()
            val path = Path()
            pts.forEachIndexed { i, h -> val x = p + i * step; if (i == 0) path.moveTo(x, y(h.temp)) else path.lineTo(x, y(h.temp)) }
            drawPath(path, tempC, style = Stroke(2.4.dp.toPx()))
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
            pts.forEachIndexed { i, h ->
                val x = p + i * step
                drawCircle(tempC, 2.6.dp.toPx(), Offset(x, y(h.temp)))
                paint.color = ink.toArgb(); paint.textSize = 13.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText("${h.temp}°", x, y(h.temp) - 8.dp.toPx(), paint)
                val r = (h.rain ?: 0) * .4f
                drawRoundRect(rainC.copy(alpha = .75f), Offset(x - 6.dp.toPx(), (132 - r).dp.toPx()), Size(12.dp.toPx(), (r + 2).dp.toPx()), CornerRadius(3.dp.toPx()))
                paint.color = mute.toArgb(); paint.textSize = 11.5.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText(if (i == 0) "${h.time.hour}時" else "%02d".format(h.time.hour), x, 146.dp.toPx(), paint)
            }
        }
    }
}

@Composable
private fun AirCard(st: HomeState) {
    if (!st.hasMoenv) return
    val a = st.snap?.airNow ?: return
    val lv = Aqi.level(a.aqi)
    val color = when (lv) { Aqi.Level.GREEN -> Palette.ok; Aqi.Level.YELLOW -> Color.hsl(50f, .85f, .62f); Aqi.Level.ORANGE -> Palette.warn; Aqi.Level.RED -> Color.hsl(0f, .75f, .62f); Aqi.Level.PURPLE -> Color.hsl(285f, .45f, .62f); Aqi.Level.MAROON -> Color.hsl(345f, .5f, .42f) }
    Card(border = if (Aqi.isAlert(a.aqi)) Palette.warn.copy(alpha = .45f) else Palette.cardLine) {
        CardTitle("空氣品質", a.area)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(88.dp, 56.dp)) {
                val sw = 8.dp.toPx(); val r = 36.dp.toPx(); val c = Offset(44.dp.toPx(), 44.dp.toPx())
                drawArc(Color.hsl(220f, .2f, .3f), 180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawArc(color, 180f, minOf(a.aqi, 300) / 300f * 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; this.color = color.toArgb(); textSize = 20.sp.toPx() }
                drawContext.canvas.nativeCanvas.drawText("${a.aqi}", c.x, c.y, paint)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("${lv.effect}（${lv.color.removeSuffix("色")}）", color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val tom = st.snap.airTomorrow?.let { "明天${it.area}預報 AQI ${it.aqi}。" } ?: ""
                Body((a.pm25?.let { "PM2.5 ${it.toInt()} μg/m³。" } ?: "") + tom + lv.advice + "。", Palette.mute, 12)
            }
        }
    }
}

@Composable
private fun Minis(s: Snapshot?) {
    s ?: return
    val today = LocalDate.now()
    val tom = s.day(today.plusDays(1))
    val h = s.hourAt(LocalDateTime.now())
    val sun = Sun.times(today, s.town.lat, s.town.lon)
    val items = listOf(
        Triple("紫外線（明天）", tom?.uvIndex?.toString() ?: "--", tom?.uvIndex?.let { if (it >= 8) "很強，中午防曬" else if (it >= 6) "高量級，中午防曬" else "不強" } ?: ""),
        Triple("風", h?.windDir ?: "--", h?.beaufort?.let { "$it 級" } ?: ""),
        Triple("日出／日落", sun?.let { "${it.first.toString().take(5)} / ${it.second.toString().take(5)}" } ?: "--", sun?.let { val m = java.time.Duration.between(it.first, it.second).toMinutes(); "白天 ${m / 60} 小時 ${m % 60} 分" } ?: ""),
        Triple("舒適度", h?.comfort ?: "--", h?.feels?.let { "體感 $it°" } ?: ""),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, v, sub) ->
                    Card(Modifier.weight(1f)) {
                        Text(k, color = Palette.mute, fontSize = 12.sp)
                        Text(v, color = Palette.ink, fontSize = if (v.length > 8) 18.sp else 24.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        if (sub.isNotBlank()) Text(sub, color = Palette.mute, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceNote(st: HomeState) {
    val s = st.snap ?: return
    val src = if (s.source == "cwa") "資料來源：中央氣象署" else "資料來源：Open-Meteo（備援）"
    val air = if (s.airNow != null) "、環境部" else ""
    val hint = if (!st.hasCwa) "　還沒填氣象署授權碼，颱風資訊與鄉鎮預報要到設定頁填。" else ""
    Text("$src$air · ${s.updatedAt.format(DateTimeFormatter.ofPattern("M/d H:mm"))} 更新$hint", color = Palette.mute, fontSize = 11.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
}
