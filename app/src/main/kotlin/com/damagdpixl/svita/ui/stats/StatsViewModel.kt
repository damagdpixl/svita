package com.damagdpixl.svita.ui.stats

import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.data.SvitaRepositories
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Section
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** Everything the «Статистика» screen renders. */
data class StatsUi(
    val loading: Boolean = true,
    val costPerWear: List<CostPerWearRow> = emptyList(),
    val mostWorn: List<WornRow> = emptyList(),
    val leastWorn: List<WornRow> = emptyList(),
    val composition: List<SectionCount> = emptyList(),
) {
    /** True when no wear exists at all — the «з'явиться після перших носінь» state. */
    val isEmpty: Boolean get() = mostWorn.isEmpty() && leastWorn.isEmpty() && costPerWear.isEmpty()
}

/**
 * Behind Settings -> «Статистика» (P2 T7).
 *
 * Data discipline (P2 contract): wear counters come from ONE `wearCounts()`
 * call per recomputation — there are NO per-item queries; the call excludes
 * plan entries inside :core:data, so planned-but-not-yet-worn outfits never
 * inflate the statistics.
 *
 * Reactivity: the wardrobe items flow plus a bare wear-log tick (an entry was
 * added/removed anywhere) re-run the aggregation; both are cheap local SQLite
 * reads. The taxonomy walk (subtype -> category -> section) is one batch of
 * per-category queries, not per-item.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    graph: SvitaGraph.Graph,
) : ViewModel() {

    private val repos: SvitaRepositories = graph.repos

    val stats: StateFlow<StatsUi>

    /** Active (non-archived) items — statistics describe the live wardrobe. */
    private val items: Flow<List<Item>> = repos.wardrobe.observeItems(ItemFilter())

    init {
        val wearTick: Flow<Unit> = repos.wearLog
            // Inclusive far bounds: plain ISO lexicographic comparison.
            .observeByRange(LocalDate(1, 1, 1), LocalDate(9999, 12, 31))
            .map { }
        stats = combine(items, wearTick) { wardrobe, _ -> wardrobe }
            .flatMapLatest { wardrobe ->
                flow { emit(load(wardrobe)) }.flowOn(Dispatchers.IO)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())
    }

    private suspend fun load(items: List<Item>): StatsUi {
        val counts = repos.wearLog.wearCounts()
        val sectionOf = sectionIndex(repos)
        val byId = counts.associate { it.itemId to it.wearCount }
        return StatsUi(
            loading = false,
            costPerWear = costPerWearRows(items, byId),
            mostWorn = topWornRows(items, counts),
            leastWorn = leastWornRows(items, counts),
            composition = sectionComposition(items, sectionOf),
        )
    }

    /** subtype id -> section, built from one categories pass plus per-category subtypes. */
    private suspend fun sectionIndex(repos: SvitaRepositories): (Long) -> Section? {
        val bySubtype = HashMap<Long, Section>()
        repos.taxonomy.categories().forEach { category ->
            repos.taxonomy.subtypesByCategory(category.id).forEach { subtype ->
                bySubtype[subtype.id] = category.section
            }
        }
        return { subtypeId -> bySubtype[subtypeId] }
    }
}
