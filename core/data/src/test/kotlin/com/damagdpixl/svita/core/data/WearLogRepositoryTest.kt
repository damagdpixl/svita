package com.damagdpixl.svita.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wear log: counters per item, last-worn dates, the 60-day not-worn query
 * with its exact boundary, range/date observation, entry de-duplication.
 */
class WearLogRepositoryTest {
    private lateinit var f: RepositoriesFixture
    private var a: Long = 0 // 3 рази
    private var b: Long = 0 // 1 раз
    private var c: Long = 0 // ніколи

    @Before
    fun setUp() {
        runBlocking {
            f = RepositoriesFixture()
            a = f.createItem("Футболка", f.subTShirt)
            b = f.createItem("Джинси", f.subJeans)
            c = f.createItem("Куртка", f.subJacket)
            f.wearLog.addEntry(LocalDate(2025, 6, 1), null, listOf(a))
            f.wearLog.addEntry(LocalDate(2025, 6, 5), null, listOf(b, a))
            f.wearLog.addEntry(LocalDate(2025, 6, 10), null, listOf(a))
        }
    }

    @Test
    fun `лічильники — математика по предметах`() = runBlocking {
        assertEquals(
            listOf(ItemWearCount(a, 3), ItemWearCount(b, 1)),
            f.wearLog.wearCounts(),
        )
    }

    @Test
    fun `останнє носіння — по предмету і по всіх`() = runBlocking {
        assertEquals(LocalDate(2025, 6, 10), f.wearLog.lastWorn(a))
        assertEquals(LocalDate(2025, 6, 5), f.wearLog.lastWorn(b))
        assertNull(f.wearLog.lastWorn(c))
        assertNull(f.wearLog.lastWorn(999L))

        assertEquals(
            listOf(
                ItemLastWorn(a, LocalDate(2025, 6, 10)),
                ItemLastWorn(b, LocalDate(2025, 6, 5)),
            ),
            f.wearLog.lastWornDates(),
        )
    }

    @Test
    fun `не носилось 60 днів — граничні дати`() = runBlocking {
        // cutoff = 2025-06-10: entry(a) on exactly the cutoff still counts as worn.
        val at60 = f.wearLog.observeNotWornSince(LocalDate(2025, 8, 9)).first().map { it.id }
        assertFalse(a in at60)
        assertTrue(b in at60) // останній раз 2025-06-05 — старіше cutoff
        assertTrue(c in at60) // ніколи не носилась

        // cutoff = 2025-06-11: тепер і «a» (06-10) формально прострочена.
        val past60 = f.wearLog.observeNotWornSince(LocalDate(2025, 8, 10)).first().map { it.id }
        assertTrue(a in past60)
        assertTrue(b in past60)
        assertTrue(c in past60)

        // Заархівовані речі не беруть участі в запиті.
        f.wardrobe.setArchived(b, true)
        val withArchived = f.wearLog.observeNotWornSince(LocalDate(2025, 8, 9)).first().map { it.id }
        assertFalse(b in withArchived)
    }

    @Test
    fun `менший період простою — ті ж граничні правила`() = runBlocking {
        // 30 днів: cutoff = 2025-06-10 — «a» на межі ще «свіжа», «b» — ні.
        val notWorn30 = f.wearLog.observeNotWornSince(LocalDate(2025, 7, 10), minDaysIdle = 30).first().map { it.id }
        assertFalse(a in notWorn30)
        assertTrue(b in notWorn30)
    }

    @Test
    fun `дублікати ідентифікаторів у записі — дедуплікуються`() = runBlocking {
        val d = f.createItem("Шапка", f.subHat)
        val id = f.wearLog.addEntry(LocalDate(2025, 7, 1), null, listOf(d, d, d))
        val entry = f.wearLog.observeByDate(LocalDate(2025, 7, 1)).first().single()
        assertEquals(id, entry.id)
        assertEquals(listOf(d), entry.itemIds)
        assertEquals(listOf(ItemWearCount(d, 1)), f.wearLog.wearCounts().filter { it.itemId == d })
    }

    @Test
    fun `спостереження — за датою та діапазоном`() = runBlocking {
        assertEquals(1, f.wearLog.observeByDate(LocalDate(2025, 6, 5)).first().size)
        assertEquals(0, f.wearLog.observeByDate(LocalDate(2025, 6, 6)).first().size)
        assertEquals(3, f.wearLog.observeByRange(LocalDate(2025, 6, 1), LocalDate(2025, 6, 30)).first().size)
        assertEquals(1, f.wearLog.observeByRange(LocalDate(2025, 6, 4), LocalDate(2025, 6, 6)).first().size)
    }

    @Test
    fun `запис з образом — outfit_id зберігається і занулюється при видаленні образу`() = runBlocking {
        val outfit = f.outfits.createOutfit(OutfitDraft("Прогулянка"))
        val id = f.wearLog.addEntry(LocalDate(2025, 6, 15), outfit, listOf(a), tempC = 21.5, note = "тепло")
        val entry = f.wearLog.observeByDate(LocalDate(2025, 6, 15)).first().single()
        assertEquals(outfit, entry.outfitId)
        assertEquals(21.5, entry.tempC!!, 0.0)
        assertEquals("тепло", entry.note)

        f.outfits.deleteOutfit(outfit)
        val after = f.wearLog.observeByDate(LocalDate(2025, 6, 15)).first().single()
        assertNull(after.outfitId)
        assertEquals(listOf(a), after.itemIds)

        f.wearLog.deleteEntry(id)
        assertTrue(f.wearLog.observeByDate(LocalDate(2025, 6, 15)).first().isEmpty())
    }

    @Test
    fun `запланований запис — не носіння, лічильники й не-носилось не змінюються`() = runBlocking {
        // План на майбутню дату для «c» (ніколи не носилось).
        f.wearLog.addEntry(
            LocalDate(2025, 9, 1), null, listOf(c), tempC = 10.4,
            note = WearLogRepository.PLAN_NOTE,
        )

        // Лічильники не побачили план.
        assertEquals(
            listOf(ItemWearCount(a, 3), ItemWearCount(b, 1)),
            f.wearLog.wearCounts(),
        )
        // «c» лишається у вікні «не носилось».
        val notWorn = f.wearLog.observeNotWornSince(LocalDate(2025, 8, 9)).first().map { it.id }
        assertTrue(c in notWorn)
        assertFalse(a in notWorn)

        // Але запис видно через спостереження (календар будується на них).
        val planned = f.wearLog.observeByDate(LocalDate(2025, 9, 1)).first().single()
        assertEquals(WearLogRepository.PLAN_NOTE, planned.note)
        assertEquals(listOf(c), planned.itemIds)

        // Останнє носіння також ігнорує план.
        assertNull(f.wearLog.lastWorn(c))
    }

    @Test
    fun `план конвертований у реальне носіння — зараховується`() = runBlocking {
        f.wearLog.addEntry(
            LocalDate(2025, 9, 1), null, listOf(c),
            note = WearLogRepository.PLAN_NOTE,
        )
        assertEquals(
            listOf(ItemWearCount(a, 3), ItemWearCount(b, 1)),
            f.wearLog.wearCounts(),
        )

        // Той самий предмет надягнуто по-справжньому: звичайний запис без плану.
        f.wearLog.addEntry(LocalDate(2025, 8, 20), null, listOf(c))

        val counts = f.wearLog.wearCounts()
        assertEquals(ItemWearCount(c, 1), counts.first { it.itemId == c })
        assertEquals(LocalDate(2025, 8, 20), f.wearLog.lastWorn(c))
        val notWorn = f.wearLog.observeNotWornSince(LocalDate(2025, 8, 9)).first().map { it.id }
        assertFalse(c in notWorn)
    }
}
