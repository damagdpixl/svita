package com.damagdpixl.svita.ui.outfits

import android.os.Looper
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.data.OutfitPrefs
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The Outfits model over the real in-memory database (P2 T6): the fixed date
 * pins the September climate norms (min 10.4 °C), so the engine pick, the
 * offline flag, the wear entry and the comfort persistence are all
 * deterministic — the network transport is disabled in this graph mode.
 *
 * [SharingStarted.WhileSubscribed] upstreams only run with a subscriber, so
 * every test subscribes to the flows it asserts on (see [subscribe]).
 */
@RunWith(RobolectricTestRunner::class)
class OutfitsViewModelTest {

    private val today = LocalDate(2026, Month.SEPTEMBER, 15)

    private lateinit var graph: SvitaGraph.Graph
    private val subscriptionScope = CoroutineScope(Dispatchers.Unconfined)
    private val subscriptions = mutableListOf<Job>()

    /** Starts a silent collector so the WhileSubscribed upstream runs. */
    private fun subscribe(flow: StateFlow<*>) {
        subscriptions += subscriptionScope.launch { flow.collect {} }
    }

    @Before
    fun setUp() {
        graph = SvitaGraph.init(RuntimeEnvironment.getApplication(), databaseName = null)
    }

    @After
    fun tearDown() {
        subscriptions.forEach { it.cancel() }
        subscriptionScope.coroutineContext.cancelChildren()
        SvitaGraph.resetForTests()
    }

    /** Мінімальна шафа, з якої вересневі норми дають рівно один образ. */
    private fun seedWardrobe() = runBlocking {
        val ids = listOf(
            "body.sweater" to "Вовняний светр",
            "legs.jeans" to "Сині джинси",
            "feet.classic shoes.brogues" to "Брогі",
            "body.jacket.classic" to "Класична куртка",
        ).map { (subtypeKey, name) ->
            val subtype = graph.repos.taxonomy.subtypeByKey(subtypeKey)
                ?: error("seed subtype $subtypeKey must exist")
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(
                    subtypeId = subtype.id,
                    name = name,
                    seasons = Season.entries.toSet(),
                ),
            )
        }
        ids
    }

    private fun await(what: String, timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) error("Перевищено таймаут очікування: $what")
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()
            Thread.sleep(50)
        }
    }

    @Test
    fun `today pick assembles a look from the seeded wardrobe`() {
        seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        await("look зібрано") { vm.dressMe.value.look != null }

        val look = vm.dressMe.value.look!!
        assertEquals(4, look.items.size)
        // Offline graph mode -> norms answered -> the badge flag is up.
        assertTrue(look.offline)
        assertEquals(10.4, look.targetTempC, 1e-9)
        assertEquals(10.4, look.weatherMinC, 1e-9)
        assertEquals(19.8, look.weatherMaxC, 1e-9)
        assertEquals("partly_cloudy", look.condition)
    }

    @Test
    fun `wear today writes a wear_log entry with the look's items`() {
        seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        await("look зібрано") { vm.dressMe.value.look != null }
        val look = vm.dressMe.value.look!!

        vm.wearToday()
        await("запис у wear_log") {
            runBlocking { graph.repos.wearLog.observeByDate(today).first().isNotEmpty() }
        }
        await("підтвердження") { vm.wearSaved.value }

        val entry = runBlocking { graph.repos.wearLog.observeByDate(today).first().single() }
        assertEquals(look.items.map { it.item.id }, entry.itemIds)
        assertEquals(today, entry.date)
        assertEquals(10.4, entry.tempC!!, 1e-9)
        assertEquals(null, entry.note)
    }

    @Test
    fun `empty wardrobe reports no look`() {
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        await("порожня шафа") { vm.dressMe.value.loading.not() }
        assertTrue(vm.dressMe.value.noLook)
        assertEquals(0, vm.dressMe.value.wardrobeCount)
    }

    @Test
    fun `comfort slider persists to settings`() {
        seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        await("look зібрано") { vm.dressMe.value.look != null }

        vm.setComfort(0.5)
        await("outfit.comfort = 0.5") {
            runBlocking {
                graph.repos.settings.getDouble(OutfitPrefs.COMFORT, 99.0) == 0.5
            }
        }

        // A fresh model restores the persisted comfort.
        val reloaded = OutfitsViewModel(graph, today = { today })
        subscribe(reloaded.comfort)
        await("комфорт відновлено") { reloaded.comfort.value == 0.5 }
    }

    @Test
    fun `refresh keeps a look on the screen (dead-end recovery)`() {
        seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        await("look зібрано") { vm.dressMe.value.look != null }
        val firstLook = vm.dressMe.value.look!!

        vm.refresh()
        await("оновлений рендер") {
            vm.dressMe.value.loading.not() && vm.dressMe.value.look != null
        }
        // The tiny wardrobe starves after excluding the shown garments, so the
        // documented recovery clears the session exclusions and re-runs —
        // deterministic engine => the same look returns instead of a dead end.
        val refreshed = vm.dressMe.value.look
        assertNotNull(refreshed)
        assertEquals(firstLook.items.map { it.item.id }, refreshed!!.items.map { it.item.id })
    }

    @Test
    fun `not worn query feeds the 60-day section`() {
        val ids = seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        subscribe(vm.dressMe)
        subscribe(vm.notWorn)
        await("не носилось: уся шафа") { vm.notWorn.value.size == 4 }

        // Wearing one item today removes exactly it from the window section.
        runBlocking {
            graph.repos.wearLog.addEntry(
                date = today,
                outfitId = null,
                itemIds = listOf(ids.first()),
            )
        }
        await("не носилось: мінус одна") { vm.notWorn.value.size == 3 }
    }
}
