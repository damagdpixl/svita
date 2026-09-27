package com.damagdpixl.svita.ui.calendar

import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Calendar month-grid math and the plan/log day classification (P2 T6):
 * Monday-first whole weeks, leap-year February, and the
 * `note='plan'` + `date >= today` planned-entry contract.
 */
class CalendarMonthTest {

    private val today = LocalDate(2026, Month.SEPTEMBER, 15)

    @Test
    fun `september 2026 grid starts on monday aug 31 and covers whole weeks`() {
        val cells = CalendarMonth.cells(2026, Month.SEPTEMBER)
        // Sept 1 2026 is a Tuesday: one leading day, 30 in-month, padded to 5 rows.
        assertEquals(35, cells.size)
        assertEquals(LocalDate(2026, Month.AUGUST, 31), cells.first().date)
        assertFalse(cells.first().inMonth)
        assertTrue(cells.first { it.date == LocalDate(2026, Month.SEPTEMBER, 1) }.inMonth)
        assertEquals(LocalDate(2026, Month.OCTOBER, 4), cells.last().date)
        assertEquals(30, cells.count { it.inMonth })
    }

    @Test
    fun `leap february spans five weeks`() {
        val cells = CalendarMonth.cells(2028, Month.FEBRUARY)
        assertEquals(35, cells.size)
        assertEquals(29, cells.count { it.inMonth })
        // Feb 1 2028 is a Tuesday: grid starts on Jan 31.
        assertEquals(LocalDate(2028, Month.JANUARY, 31), cells.first().date)
    }

    @Test
    fun `non-leap century february has 28 days`() {
        val cells = CalendarMonth.cells(2100, Month.FEBRUARY)
        assertEquals(28, cells.count { it.inMonth })
    }

    @Test
    fun `february 2024 pads three leading days before the 29-day month`() {
        // Feb 1 2024 is a Thursday: leading from Mon Jan 29.
        val cells = CalendarMonth.cells(2024, Month.FEBRUARY)
        assertEquals(LocalDate(2024, Month.JANUARY, 29), cells.first().date)
        assertEquals(29, cells.count { it.inMonth })
    }

    @Test
    fun `markCells classifies days from the wear log`() {
        val cells = CalendarMonth.cells(2026, Month.SEPTEMBER)
        val entries = listOf(
            entry(1, LocalDate(2026, Month.SEPTEMBER, 20), note = "plan"),
            entry(2, LocalDate(2026, Month.SEPTEMBER, 10), note = null),
        )
        val marked = CalendarMonth.markCells(cells, entries.groupBy { it.date }, today)
        val planned = marked.first { it.date == LocalDate(2026, Month.SEPTEMBER, 20) }
        val logged = marked.first { it.date == LocalDate(2026, Month.SEPTEMBER, 10) }
        val empty = marked.first { it.date == LocalDate(2026, Month.SEPTEMBER, 25) }
        assertEquals(DayMark.PLANNED, planned.mark)
        assertEquals(DayMark.LOGGED, logged.mark)
        assertEquals(DayMark.NONE, empty.mark)
    }

    @Test
    fun `plan classification follows the date-greater-equals-today contract`() {
        val future = listOf(entry(1, LocalDate(2026, Month.SEPTEMBER, 16), note = "plan"))
        val todayPlan = listOf(entry(2, today, note = "plan"))
        val pastPlan = listOf(entry(3, LocalDate(2026, Month.SEPTEMBER, 1), note = "plan"))
        assertEquals(DayMark.PLANNED, classifyDay(future, today))
        assertEquals(DayMark.PLANNED, classifyDay(todayPlan, today))
        // A plan on a past date reads as history.
        assertEquals(DayMark.LOGGED, classifyDay(pastPlan, today))
    }

    @Test
    fun `mixed day counts as logged - real wear dominates`() {
        val mixed = listOf(
            entry(1, today, note = null),
            entry(2, today, note = "plan"),
        )
        assertEquals(DayMark.LOGGED, classifyDay(mixed, today))
    }

    @Test
    fun `formatting helpers localize through java time`() {
        assertEquals(
            "September 2026",
            CalendarFormat.monthTitle(2026, Month.SEPTEMBER, Locale.ENGLISH),
        )
        assertEquals(
            listOf("M", "T", "W", "T", "F", "S", "S"),
            CalendarFormat.weekdayLetters(Locale.ENGLISH),
        )
        assertEquals(
            listOf("П", "В", "С", "Ч", "П", "С", "Н"),
            CalendarFormat.weekdayLetters(Locale("uk")),
        )
        assertEquals(
            "15 September",
            CalendarFormat.dayTitle(LocalDate(2026, Month.SEPTEMBER, 15), Locale.ENGLISH),
        )
    }

    private fun entry(id: Long, date: LocalDate, note: String?): WearLogEntry = WearLogEntry(
        id = id,
        date = date,
        outfitId = null,
        itemIds = listOf(7L),
        tempC = null,
        note = note,
    )
}
