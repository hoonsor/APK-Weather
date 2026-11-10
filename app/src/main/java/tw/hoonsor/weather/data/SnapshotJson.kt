package tw.hoonsor.weather.data

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.weather.domain.AirReading
import tw.hoonsor.weather.domain.DayForecast
import tw.hoonsor.weather.domain.HourPoint
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Snapshot
import tw.hoonsor.weather.domain.Town
import tw.hoonsor.weather.domain.TrackPoint
import tw.hoonsor.weather.domain.Typhoon
import java.time.LocalDate
import java.time.LocalDateTime

/** 快取檔與小工具狀態的 JSON 格式（只存手機，不上傳） */
object SnapshotJson {
    private fun JSONObject.putOpt2(k: String, v: Any?) = apply { if (v != null) put(k, v) }
    private fun JSONObject.intOrNull(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.strOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null

    fun town(t: Town) = JSONObject().put("county", t.county).put("name", t.name).put("lat", t.lat).put("lon", t.lon)
    fun town(o: JSONObject) = Town(o.getString("county"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))

    fun air(a: AirReading) = JSONObject().put("aqi", a.aqi).putOpt2("pm25", a.pm25).put("area", a.area).put("forecast", a.isForecast)
    fun air(o: JSONObject) = AirReading(o.getInt("aqi"), if (o.has("pm25")) o.getDouble("pm25") else null, o.getString("area"), o.getBoolean("forecast"))

    fun day(d: DayForecast) = JSONObject().put("date", d.date.toString()).put("w", d.weather).put("code", d.weatherCode)
        .put("tMin", d.tempMin).put("tMax", d.tempMax).put("fMin", d.feelsMin).put("fMax", d.feelsMax)
        .putOpt2("rD", d.rainDay).putOpt2("rN", d.rainNight).putOpt2("uv", d.uvIndex).putOpt2("bf", d.beaufortMax)
    fun day(o: JSONObject) = DayForecast(
        LocalDate.parse(o.getString("date")), o.getString("w"), o.getInt("code"), o.getInt("tMin"), o.getInt("tMax"), o.getInt("fMin"), o.getInt("fMax"),
        o.intOrNull("rD"), o.intOrNull("rN"), o.intOrNull("uv"), o.intOrNull("bf"),
    )

    fun hour(h: HourPoint) = JSONObject().put("t", h.time.toString()).put("temp", h.temp).putOpt2("feels", h.feels).putOpt2("rain", h.rain)
        .put("code", h.code).put("w", h.weather).putOpt2("rh", h.humidity).putOpt2("bf", h.beaufort).putOpt2("wd", h.windDir).putOpt2("ci", h.comfort)
    fun hour(o: JSONObject) = HourPoint(
        LocalDateTime.parse(o.getString("t")), o.getInt("temp"), o.intOrNull("feels"), o.intOrNull("rain"), o.getInt("code"), o.getString("w"),
        o.intOrNull("rh"), o.intOrNull("bf"), o.strOrNull("wd"), o.strOrNull("ci"),
    )

    fun write(s: Snapshot): JSONObject = JSONObject().put("v", 1).put("kind", s.kind.key).put("town", town(s.town))
        .put("updatedAt", s.updatedAt.toString()).put("source", s.source)
        .put("hours", JSONArray(s.hours.map(::hour))).put("days", JSONArray(s.days.map(::day)))
        .putOpt2("airNow", s.airNow?.let(::air)).putOpt2("airTomorrow", s.airTomorrow?.let(::air))

    fun read(o: JSONObject): Snapshot = Snapshot(
        PlaceKind.of(o.getString("kind")) ?: PlaceKind.CURRENT, town(o.getJSONObject("town")), LocalDateTime.parse(o.getString("updatedAt")),
        o.getString("source"), J.objs(o.getJSONArray("hours")).map(::hour), J.objs(o.getJSONArray("days")).map(::day),
        o.optJSONObject("airNow")?.let(::air), o.optJSONObject("airTomorrow")?.let(::air),
    )

    fun typhoon(t: Typhoon) = JSONObject().put("name", t.name).put("kind", t.kind).put("pos", t.position).put("warning", t.warning.name)
        .put("land", JSONArray(t.landWarningCounties.toList()))
        .put("track", JSONArray(t.track.map { JSONObject().put("t", it.time.toString()).put("lat", it.lat).put("lon", it.lon).putOpt2("r7", it.radius7Km).putOpt2("r70", it.radius70Km) }))
    fun typhoon(o: JSONObject) = Typhoon(
        o.getString("name"), o.getString("kind"), o.getString("pos"),
        J.objs(o.getJSONArray("track")).map { TrackPoint(LocalDateTime.parse(it.getString("t")), it.getDouble("lat"), it.getDouble("lon"), it.intOrNull("r7"), it.intOrNull("r70")) },
        Typhoon.Warning.valueOf(o.getString("warning")),
        (0 until o.getJSONArray("land").length()).map { o.getJSONArray("land").getString(it) }.toSet(),
    )
}
