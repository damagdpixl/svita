package com.damagdpixl.svita.ui.packing

import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Packing suggestion union gate (P2 T7): inclusive range bounds, dedupe
 * across outfits, and the DOCUMENTED ASYMMETRY — planned entries (note =
 * 'plan') are included in packing suggestions, unlike the wear statistics
 * that exclude them in :core:data.
 */
class PackingSuggestionsTest {

    private fun entry(id: Long, date: String, itemIds: List<Long>, note: String? = null) =
        WearLogEntry(
            id = id,
            date = LocalDate.parse(date),
            outfitId = null,
            itemIds = itemIds,
            tempC = null,
            note = note,
        )

    @Test
    fun `об'єднання речей з різних образів без дублікатів`() {
        val entries = listOf(
            entry(1, "2026-09-10", listOf(1, 2, 3)),
            entry(2, "2026-09-12", listOf(3, 4)),
            entry(3, "2026-09-14", listOf(1, 4, 5)),
        )
        // Перша поява задає порядок: 1,2,3,4,5 — без повторів.
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), suggestedItemIds(entries, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)))
    }

    @Test
    fun `межі діапазону включні`() {
        val entries = listOf(
            entry(1, "2026-09-05", listOf(1)), // перед from — геть
            entry(2, "2026-09-06", listOf(2)), // рівно from — включно
            entry(3, "2026-09-07", listOf(3)), // рівно to — включно
            entry(4, "2026-09-08", listOf(4)), // після to — геть
        )
        assertEquals(
            listOf(2L, 3L),
            suggestedItemIds(entries, LocalDate(2026, 9, 6), LocalDate(2026, 9, 7)),
        )
    }

    @Test
    fun `заплановані записи теж потрапляють у валізу - на відміну від статистики`() {
        val entries = listOf(
            entry(1, "2026-09-10", listOf(1, 2)), // реальне носіння
            entry(2, "2026-09-12", listOf(3), note = "plan"), // ЗАПЛАНОВАНО
            entry(3, "2026-09-13", listOf(4), note = "plan"), // ЗАПЛАНОВАНО
        )
        // Packing includes plans: items 3 and 4 of planned outfits are needed
        // on the trip even though they were never worn.
        assertEquals(listOf(1L, 2L, 3L, 4L), suggestedItemIds(entries, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)))
    }

    @Test
    fun `записи поза діапазоном ігноруються`() {
        val entries = listOf(
            entry(1, "2026-08-31", listOf(1)),
            entry(2, "2026-09-01", listOf(2)),
            entry(3, "2026-10-01", listOf(3)),
        )
        assertEquals(
            listOf(2L),
            suggestedItemIds(entries, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
        )
    }

    @Test
    fun `порожній діапазон - порожня пропозиція`() {
        val entries = listOf(entry(1, "2026-09-10", listOf(1, 1, 2)))
        assertEquals(emptyList<Long>(), suggestedItemIds(entries, LocalDate(2026, 10, 1), LocalDate(2026, 10, 2)))
    }
}
