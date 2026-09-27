package com.damagdpixl.svita.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.data.PackingListAggregate
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PackingEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One checklist row: the packing entry plus the item it references. */
data class ChecklistRow(
    val entry: PackingEntry,
    val item: Item?,
)

/** Everything the trip detail screen renders. */
data class TripUi(
    val list: PackingList? = null,
    val rows: List<ChecklistRow> = emptyList(),
) {
    val packedCount: Int get() = rows.count { it.entry.packed }
    val totalCount: Int get() = rows.size
}

/**
 * Behind the trip detail route (P2 T7): the checklist with `packed` toggles,
 * manual adds from the wardrobe, item removal and list deletion.
 *
 * The repository exposes no reactive entries query (v1 schema contract), so
 * the raw aggregate is re-read after every mutation — each operation is a
 * single local transaction followed by one read.
 *
 * Name resolution (fix round 1): checklist rows are RE-DERIVED whenever the
 * wardrobe index emits, not snapshot at reload time. The wardrobe flow starts
 * empty and its first real emission can land after the aggregate's — deriving
 * inside [combine] means a freshly opened trip resolves item names on its own
 * render without any user mutation. Both flows stay hot for the VM's lifetime
 * ([SharingStarted.Eagerly]) so the derivation cannot stall between mutations.
 */
class TripDetailViewModel(
    graph: SvitaGraph.Graph,
    private val listId: Long,
) : ViewModel() {

    private val repos = graph.repos

    /** Wardrobe index for names (archived included: packed lists outlive archives). */
    val items: StateFlow<List<Item>> = repos.wardrobe
        .observeItems(ItemFilter(includeArchived = true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Raw aggregate; null until the first read completes (screen shows nothing). */
    private val aggregate = MutableStateFlow<PackingListAggregate?>(null)

    /** Derived UI state: null = aggregate not loaded yet. */
    val trip: StateFlow<TripUi?> = combine(aggregate, items) { aggregate, items ->
        aggregate?.let { agg ->
            val byId = items.associateBy { it.id }
            TripUi(
                list = agg.list,
                rows = agg.entries.map { entry ->
                    ChecklistRow(entry = entry, item = byId[entry.itemId])
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            aggregate.value = repos.packing.getPackingList(listId)
        }
    }

    fun setPacked(itemId: Long, packed: Boolean) {
        viewModelScope.launch {
            repos.packing.setPacked(listId, itemId, packed)
            reload()
        }
    }

    fun addItems(itemIds: Collection<Long>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            itemIds.forEach { repos.packing.addPackingItem(listId, it) }
            reload()
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            repos.packing.removePackingItem(listId, itemId)
            reload()
        }
    }

    fun deleteList(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repos.packing.deletePackingList(listId)
            onDeleted()
        }
    }
}
