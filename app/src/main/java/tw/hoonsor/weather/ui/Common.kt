package tw.hoonsor.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.weather.domain.ClothingItem
import tw.hoonsor.weather.domain.WeatherIcon
import tw.hoonsor.weather.ui.theme.Palette
import tw.hoonsor.weather.widget.WeatherWidgets

/** 共用元件（DESIGN.md 第 4、5 節） */
val Num = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)

@Composable
fun Card(modifier: Modifier = Modifier, border: Color = Palette.cardLine, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(20.dp)).border(1.dp, border, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
fun CardTitle(title: String, right: String? = null, rightContent: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.inkSoft, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (right != null) Text(right, color = Palette.mute, fontSize = 12.sp)
        rightContent?.invoke()
    }
}

@Composable
fun Body(s: String, color: Color = Palette.ink, size: Int = 14) = Text(s, color = color, fontSize = size.sp, lineHeight = (size * 1.6).sp)

@Composable
fun Weather(icon: WeatherIcon, size: Dp) = Image(painterResource(WeatherWidgets.icon(icon)), null, Modifier.size(size))

@Composable
fun Cloth(item: ClothingItem, size: Dp = 18.dp) = Image(painterResource(WeatherWidgets.cloth(item)), item.label, Modifier.size(size))

@Composable
fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.background(if (on) Palette.accentBg else Color.Transparent, CircleShape)
            .border(1.dp, if (on) Palette.accent.copy(alpha = .6f) else Palette.mute.copy(alpha = .35f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = if (on) Color.White else Palette.inkSoft, fontSize = 13.sp) }
}

/** 有邊框的按鈕（倬的 UX：按鈕要有邊框或對比） */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.background(if (primary) Palette.accentBg else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, (if (primary) Palette.accent else Palette.mute).copy(alpha = if (enabled) .6f else .2f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) (if (primary) Color.White else Palette.ink) else Palette.mute, fontSize = 13.5.sp) }
}

@Composable
fun ToggleRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.ink, fontSize = 14.sp)
            if (sub != null) Text(sub, color = Palette.mute, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.accentBg, checkedThumbColor = Palette.accent, checkedBorderColor = Palette.accent))
    }
}

@Composable
fun StatusDot(ok: Boolean?, size: Dp = 10.dp) =
    Box(Modifier.size(size).background(when (ok) { true -> Palette.ok; false -> Palette.danger; null -> Palette.warn }, CircleShape))

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Palette.amber, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    Card(content = content)
}
