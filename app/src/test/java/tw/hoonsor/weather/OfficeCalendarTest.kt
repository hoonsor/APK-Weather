package tw.hoonsor.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.weather.domain.OfficeCalendar
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.PlacePicker
import java.time.LocalDate

class OfficeCalendarTest {
    private val csv = listOf(
        "﻿西元日期,星期,是否放假,備註",
        "20260101,四,2,開國紀念日",
        "20260102,五,0,",
        "20260103,六,2,",
        "\"20260214\",\"六\",\"0\",\"補行上班\"",
        "bad,row",
        "2026/02/16,一,2,春節",
    ).joinToString("\n")

    @Test fun parsesHolidayMakeupAndBom() {
        val c = OfficeCalendar.parse(csv)
        assertFalse(c.isWorkday(LocalDate.of(2026, 1, 1)))
        assertTrue(c.isWorkday(LocalDate.of(2026, 1, 2)))
        assertTrue(c.isWorkday(LocalDate.of(2026, 2, 14)))   // 週六補班
        assertFalse(c.isWorkday(LocalDate.of(2026, 2, 16)))  // 週一春節
        assertEquals("開國紀念日", c.note(LocalDate.of(2026, 1, 1)))
        assertEquals(setOf(2026), c.years)
    }

    @Test fun fallsBackToWeekdays() {
        val c = OfficeCalendar.parse(csv)
        assertTrue(c.isWorkday(LocalDate.of(2027, 3, 3)))    // 週三
        assertFalse(c.isWorkday(LocalDate.of(2027, 3, 6)))   // 週六
        assertFalse(c.covers(LocalDate.of(2027, 3, 3)))
    }

    @Test fun overridesWin() {
        val c = OfficeCalendar.parse(csv).withOverrides(mapOf(LocalDate.of(2026, 1, 2) to false))
        assertFalse(c.isWorkday(LocalDate.of(2026, 1, 2)))
        assertEquals("開國紀念日", c.note(LocalDate.of(2026, 1, 1)))
    }

    @Test fun autoPlace() {
        assertEquals(PlaceKind.SCHOOL, PlacePicker.auto(true, hasHome = true, hasSchool = true))
        assertEquals(PlaceKind.HOME, PlacePicker.auto(false, hasHome = true, hasSchool = true))
        assertEquals(PlaceKind.HOME, PlacePicker.auto(true, hasHome = true, hasSchool = false))
        assertEquals(PlaceKind.CURRENT, PlacePicker.auto(false, hasHome = false, hasSchool = true))
    }
}
