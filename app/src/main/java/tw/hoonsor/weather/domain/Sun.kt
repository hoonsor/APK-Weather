package tw.hoonsor.weather.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 日出日落（NOAA 簡化公式，誤差約 1～2 分鐘；台灣 UTC+8、不用日光節約時間） */
object Sun {
    fun times(date: LocalDate, lat: Double, lon: Double): Pair<LocalTime, LocalTime>? {
        val n = date.dayOfYear
        val g = 2 * Math.PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (cosHa !in -1.0..1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        fun at(sign: Int): LocalTime {
            val minutesUtc = 720 - 4 * (lon + sign * ha) - eqTime
            val local = ((minutesUtc + 8 * 60) % 1440 + 1440) % 1440
            return LocalTime.of((local / 60).toInt(), (local % 60).toInt())
        }
        return at(1) to at(-1)
    }

}
