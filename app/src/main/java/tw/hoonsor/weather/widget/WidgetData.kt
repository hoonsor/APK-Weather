package tw.hoonsor.weather.widget

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.Broadcast
import tw.hoonsor.weather.domain.ClothingItem
import tw.hoonsor.weather.domain.ClothingRules
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.WeatherIcon
import tw.hoonsor.weather.domain.WeatherIcons
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 小工具要顯示的內容（純資料，JVM 可測）。由 [WeatherWidgets.pushAll] 算好寫進 Glance 狀態，
 * 小工具本身不連網、不讀檔（Glance 的 provideContent 不會自己去讀，SoundFlow 踩過的坑）。
 */
data class WidgetData(
    val town: String,
    val temp: Int?,
    val icon: WeatherIcon,
    val hi: Int?,
    val lo: Int?,
    val rainNow: Int?,
    val days: List<Day>,
    val tomorrowLabel: String,
    val tomorrowLo: Int?,
    val tomorrowHi: Int?,
    val tomorrowIcon: WeatherIcon,
    val tomorrowRainDay: Int?,
    val tomorrowRainNight: Int?,
    val tomorrowItems: List<ClothingItem>,
    val badge: String?,           // 「颱風」或「空品橘」；4×1 地點旁的小標籤
    val updated: String,          // 「20:40 更新」
    val stale: Boolean,
) {
    data class Day(val name: String, val icon: WeatherIcon, val hi: Int, val lo: Int)

    fun toJson(): String = JSONObject().put("town", town).put("temp", temp ?: JSONObject.NULL).put("icon", icon.name)
        .put("hi", hi ?: JSONObject.NULL).put("lo", lo ?: JSONObject.NULL).put("rainNow", rainNow ?: JSONObject.NULL)
        .put("days", JSONArray(days.map { JSONObject().put("n", it.name).put("i", it.icon.name).put("hi", it.hi).put("lo", it.lo) }))
        .put("tLabel", tomorrowLabel).put("tLo", tomorrowLo ?: JSONObject.NULL).put("tHi", tomorrowHi ?: JSONObject.NULL).put("tIcon", tomorrowIcon.name)
        .put("tRd", tomorrowRainDay ?: JSONObject.NULL).put("tRn", tomorrowRainNight ?: JSONObject.NULL)
        .put("tItems", JSONArray(tomorrowItems.map { it.name })).put("badge", badge ?: JSONObject.NULL).put("updated", updated).put("stale", stale).toString()

    companion object {
        private fun JSONObject.i(k: String) = if (isNull(k)) null else optInt(k)

        fun fromJson(s: String): WidgetData? = runCatching {
            val o = JSONObject(s)
            WidgetData(
                o.getString("town"), o.i("temp"), WeatherIcon.valueOf(o.getString("icon")), o.i("hi"), o.i("lo"), o.i("rainNow"),
                (0 until o.getJSONArray("days").length()).map { o.getJSONArray("days").getJSONObject(it) }
                    .map { Day(it.getString("n"), WeatherIcon.valueOf(it.getString("i")), it.getInt("hi"), it.getInt("lo")) },
                o.getString("tLabel"), o.i("tLo"), o.i("tHi"), WeatherIcon.valueOf(o.getString("tIcon")), o.i("tRd"), o.i("tRn"),
                (0 until o.getJSONArray("tItems").length()).map { ClothingItem.valueOf(o.getJSONArray("tItems").getString(it)) },
                if (o.isNull("badge")) null else o.getString("badge"), o.getString("updated"), o.getBoolean("stale"),
            )
        }.getOrNull()

        val EMPTY = WidgetData("尚未更新", null, WeatherIcon.CLOUD, null, null, null, emptyList(), "明天", null, null, WeatherIcon.CLOUD, null, null, emptyList(), null, "打開 APP 設定", true)

        private val hm = DateTimeFormatter.ofPattern("H:mm")
        private val shortDay = mapOf("星期一" to "週一", "星期二" to "週二", "星期三" to "週三", "星期四" to "週四", "星期五" to "週五", "星期六" to "週六", "星期日" to "週日")

        /**
         * [now]：目前位置（沒有就用家）的快照；[tomorrow]：明天播報地點的快照（auto 規則）。
         * [typhoonActive]：有颱風時標籤優先於空品。
         */
        fun build(now: Snapshot?, tomorrowKind: PlaceKind, tomorrow: Snapshot?, typhoonActive: Boolean, shift: Int, at: LocalDateTime = LocalDateTime.now()): WidgetData {
            if (now == null && tomorrow == null) return EMPTY
            val base = now ?: tomorrow!!
            val n = base.now(at)
            val night = at.hour >= 18 || at.hour < 6
            val range = base.todayRange(at)
            val days = base.days.filter { it.date.isAfter(at.toLocalDate()) }.take(4).map {
                Day(shortDay[Broadcast.weekdayName(it.date)] ?: "", WeatherIcons.of(it.weatherCode, it.weather), it.tempMax, it.tempMin)
            }
            val t = (tomorrow ?: base)
            val td = t.day(at.toLocalDate().plusDays(1))
            val items = td?.let { ClothingRules.advise(it, t.airTomorrow?.aqi, shift).items } ?: emptyList()
            val label = "明天 · " + when (if (tomorrow != null) tomorrowKind else base.kind) { PlaceKind.SCHOOL -> "學校"; PlaceKind.HOME -> "家"; PlaceKind.CURRENT -> t.town.name }
            val airAlert = listOfNotNull(base.airNow?.aqi, t.airTomorrow?.aqi).maxOrNull()?.let { Aqi.isAlert(it) } == true
            val badge = when {
                typhoonActive -> "颱風"
                airAlert -> "空品" + Aqi.level(listOfNotNull(base.airNow?.aqi, t.airTomorrow?.aqi).max()).color.take(1)
                else -> null
            }
            return WidgetData(
                town = base.town.name, temp = n?.temp, icon = n?.let { WeatherIcons.of(it.weatherCode, it.weather, night) } ?: WeatherIcon.CLOUD,
                hi = range?.second, lo = range?.first, rainNow = n?.rainNext3h, days = days,
                tomorrowLabel = label, tomorrowLo = td?.tempMin, tomorrowHi = td?.tempMax,
                tomorrowIcon = td?.let { WeatherIcons.of(it.weatherCode, it.weather) } ?: WeatherIcon.CLOUD,
                tomorrowRainDay = td?.rainDay, tomorrowRainNight = td?.rainNight, tomorrowItems = items, badge = badge,
                updated = "${base.updatedAt.format(hm)} 更新", stale = base.ageMinutes(at) > 180,
            )
        }
    }
}
