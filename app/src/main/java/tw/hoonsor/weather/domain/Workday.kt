package tw.hoonsor.weather.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 上班日（F08）：人事總處「政府行政機關辦公日曆表」（data.gov.tw 14718）。
 * CSV 欄位：西元日期(yyyyMMdd)、星期、是否放假(0 上班／2 放假)、備註。
 * 日曆沒有那天時退回「週一到週五上班」；[overrides] 是倬手動標的（優先）。
 */
class OfficeCalendar(private val days: Map<LocalDate, Boolean>, private val overrides: Map<LocalDate, Boolean> = emptyMap()) {

    fun isWorkday(date: LocalDate): Boolean =
        overrides[date] ?: days[date] ?: (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY)

    fun covers(date: LocalDate) = date in days
    fun note(date: LocalDate): String? = notes[date]
    val years: Set<Int> get() = days.keys.map { it.year }.toSet()

    private var notes: Map<LocalDate, String> = emptyMap()

    fun withOverrides(o: Map<LocalDate, Boolean>) = OfficeCalendar(days, o).also { it.notes = notes }

    companion object {
        private val fmt = DateTimeFormatter.BASIC_ISO_DATE

        /** 解析 CSV；容忍 BOM、表頭、空行、日期帶斜線（2026/01/01）。壞列略過 */
        fun parse(csv: String): OfficeCalendar {
            val days = HashMap<LocalDate, Boolean>()
            val notes = HashMap<LocalDate, String>()
            csv.removePrefix("﻿").lineSequence().forEach { raw ->
                val cols = raw.split(',').map { it.trim().trim('"') }
                if (cols.size < 3) return@forEach
                val date = runCatching { LocalDate.parse(cols[0].replace("/", "").replace("-", ""), fmt) }.getOrNull() ?: return@forEach
                val holiday = cols[2].toIntOrNull() ?: return@forEach
                days[date] = holiday == 0
                cols.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { notes[date] = it }
            }
            return OfficeCalendar(days).also { it.notes = notes }
        }

        fun merge(vararg cals: OfficeCalendar): OfficeCalendar {
            val days = HashMap<LocalDate, Boolean>()
            val notes = HashMap<LocalDate, String>()
            cals.forEach { days.putAll(it.days); notes.putAll(it.notes) }
            return OfficeCalendar(days).also { it.notes = notes }
        }
    }
}

/** 播報地點（F07）：明天上班 → 學校；否則 → 家；沒設定就退回目前位置 */
object PlacePicker {
    fun auto(tomorrowIsWorkday: Boolean, hasHome: Boolean, hasSchool: Boolean): PlaceKind = when {
        tomorrowIsWorkday && hasSchool -> PlaceKind.SCHOOL
        hasHome -> PlaceKind.HOME
        else -> PlaceKind.CURRENT
    }
}
