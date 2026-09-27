package com.damagdpixl.svita.ui.calendar

import com.damagdpixl.svita.data.OutfitPrefs
import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.time.DayOfWeek

/**
 * Which kind of activity a calendar day carries (P2 T6):
 * - [LOGGED] — at least one entry is real wear (no `plan` note, or a `plan`
 *   note on a PAST date, which by the documented semantics reads as history);
 * - [PLANNED] — the day has entries and ALL of them are plans
 *   (`note == 'plan'`, `date >= today`).
 * A day that mixes wear and plans counts as [LOGGED] — real wear dominates.
 */
enum class DayMark { NONE, LOGGED, PLANNED }

/**
 * A calendar cell: the date plus whether it belongs to the rendered month and
 * the activity mark derived from the wear log.
 */
data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val mark: DayMark = DayMark.NONE,
)

/** One planned entry: a wear-log row marked with the `plan` note. */
fun WearLogEntry.isPlan(today: LocalDate): Boolean =
    note == OutfitPrefs.PLAN_NOTE && date >= today

/**
 * Classification of one day's entries against the plan contract
 * (`note='plan'` + `date >= today`; see [OutfitPrefs.PLAN_NOTE]).
 */
fun classifyDay(entries: List<WearLogEntry>, today: LocalDate): DayMark {
    if (entries.isEmpty()) return DayMark.NONE
    return if (entries.any { !it.isPlan(today) }) DayMark.LOGGED else DayMark.PLANNED
}

/**
 * Month-grid math for the Editorial calendar (P2 T6), pure and timezone-safe.
 *
 * The grid is MONDAY-FIRST (the app's primary locale starts weeks on Monday)
 * and always renders whole weeks: leading cells from the previous month and
 * trailing cells from the next one pad the first/last partial weeks. Marks are
 * applied afterwards by [markCells] from the wear-log range query.
 */
object CalendarMonth {

    /** First weekday shown in the grid. */
    val FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

    /** Whole-week cell list for [year]/[month] (leading/trailing padding incl.). */
    fun cells(year: Int, month: Month): List<CalendarCell> {
        val firstJava = java.time.LocalDate.of(year, month.ordinal + 1, 1)
        val firstOffset = (firstJava.dayOfWeek.value + 7 - FIRST_DAY_OF_WEEK.value) % 7
        val gridStartEpoch = firstJava.toEpochDay() - firstOffset
        val length = actualLength(year, month)
        val total = firstOffset + length
        val rows = (total + 6) / 7
        return List(rows * 7) { index ->
            val date = LocalDate.fromEpochDays(gridStartEpoch + index)
            CalendarCell(
                date = date,
                inMonth = date.month == month && date.year == year,
            )
        }
    }

    /**
     * Applies the wear-log marks: entry dates present in [entriesByDate] get
     * [classifyDay]; out-month cells keep their dots too (the grid stays
     * honest at month borders).
     */
    fun markCells(
        cells: List<CalendarCell>,
        entriesByDate: Map<LocalDate, List<WearLogEntry>>,
        today: LocalDate,
    ): List<CalendarCell> = cells.map { cell ->
        val entries = entriesByDate[cell.date].orEmpty()
        cell.copy(mark = classifyDay(entries, today))
    }

    private fun actualLength(year: Int, month: Month): Int = when (month) {
        Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY, Month.AUGUST,
        Month.OCTOBER, Month.DECEMBER,
        -> 31

        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30

        Month.FEBRUARY -> if (isLeap(year)) 29 else 28
    }

    private fun isLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

/**
 * Localized formatting helpers for the calendar header/weekday strip. java.time
 * formatters localize through the passed locale; date values stay kotlinx
 * [LocalDate] at the module seams.
 */
object CalendarFormat {

    /** «September 2026» / «вересень 2026» per [locale]. */
    fun monthTitle(year: Int, month: Month, locale: java.util.Locale): String =
        java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale)
            .format(java.time.LocalDate.of(year, month.ordinal + 1, 1))

    /** Narrow weekday letters, Monday-first: «П Н С Ч П С Н» / «M T W T F S S». */
    fun weekdayLetters(locale: java.util.Locale): List<String> {
        val formatter = java.time.format.DateTimeFormatterBuilder()
            .appendText(java.time.temporal.ChronoField.DAY_OF_WEEK, java.time.format.TextStyle.NARROW)
            .toFormatter(locale)
        return (0 until 7).map { offset ->
            val day = CalendarMonth.FIRST_DAY_OF_WEEK.plus(offset.toLong())
            formatter.format(day)
        }
    }

    /** «15 вересня» / «15 September» style sheet header per [locale]. */
    fun dayTitle(date: LocalDate, locale: java.util.Locale): String =
        java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale)
            .format(java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.dayOfMonth))
}
