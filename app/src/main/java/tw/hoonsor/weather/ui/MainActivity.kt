package tw.hoonsor.weather.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.hoonsor.weather.BuildConfig
import tw.hoonsor.weather.domain.Broadcast
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.TyphoonImpact
import tw.hoonsor.weather.ui.theme.Palette
import java.time.LocalDateTime

/**
 * v0.1.0 骨架：只用範例資料把規則引擎的結果顯示出來，確認建置與播報句。
 * 正式主畫面（DESIGN.md 第 1 節的順序）在 v0.4.0 做。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SkeletonScreen() }
    }
}

@Composable
private fun SkeletonScreen() {
    val now = LocalDateTime.now()
    val week = Sample.week()
    val tomorrow = week.first()
    val advice = ClothingRules.advise(tomorrow, aqi = Sample.air.aqi)
    val ty = Sample.typhoon(now)
    val places = listOf(
        "學校" to TyphoonImpact.assess(ty, Sample.town).level,
        "家裡" to TyphoonImpact.assess(ty, Sample.home).level,
    )
    val closest = TyphoonImpact.closestToTaiwan(ty)?.first?.time

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Palette.bgTop, .4f to Palette.bgMid, 1f to Palette.bgBottom))
            .systemBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("倬的天氣 v${BuildConfig.VERSION_NAME}（骨架版，範例資料）", color = Palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Card("明天播報") {
            Body(Broadcast.tomorrow(tomorrow, PlaceKind.SCHOOL, Sample.town, advice))
            Broadcast.airAlert(Sample.air.copy(isForecast = true), tomorrow = true)?.let { Body(it) }
            Body(Broadcast.typhoon(ty, closest, now, places, "停班停課還沒公布", short = true))
            Body("穿著圖示：" + advice.items.joinToString("、") { it.label })
        }
        Card("7 日") {
            week.forEach { d ->
                val a = ClothingRules.advise(d)
                Body("${Broadcast.weekdayName(d.date)} ${d.weather} ${d.tempMin}–${d.tempMax}° 雨 ${maxOf(d.rainDay ?: 0, d.rainNight ?: 0)}%　${a.items.joinToString(" ") { it.label }}")
            }
        }
        Card("接下來的版本") {
            Body("v0.2 抓氣象署、環境部資料　v0.3 桌面小工具 4×1／2×2　v0.4 主畫面　v0.5 語音總控動作　v0.6 設定、健康檢查、備份")
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(20.dp))
            .border(1.dp, Palette.cardLine, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Palette.inkSoft, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Body(s: String) = Text(s, color = Palette.ink, fontSize = 14.sp, lineHeight = 22.sp)
