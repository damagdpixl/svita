package com.damagdpixl.svita.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PackingEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * the aggregate is re-read after every mutation — each operation is a single
 * local transaction followed by one read; the list header stays reactive
 * through `observePackingLists` so a delete from elsewhere still unblocks the
 * screen.
 */
class TripDetailViewModel(
    graph: SvitaGraph.Graph,
    private val listId: Long,
) : ViewModel() {

    private val repos = graph.repos

    /** Wardrobe index for names (archived included: packed lists outlive archives). */
    val items: StateFlow<List<Item>> = repos.wardrobe
        .observeItems(ItemFilter(includeArchived = true))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trip = MutableStateFlow<TripUi?>(null)

    init {
        reload()
        // Keep the header honest if the list disappears under us (deleted).
        viewModelScope.launch {
            repos.packing.observePackingLists().collect { lists ->
                val current = trip.value ?: TripUi()
                if (current.list?.id != null && lists.none { it.id == listId }) {
                    trip.value = current.copy(list = null)
                } else if (lists.any { it.id == listId } && current.list == null) {
                    reload()
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            val aggregate = repos.packing.getPackingList(listId) ?: run {
                trip.value = TripUi()
                return@launch
            }
            val byId = items.value.associateBy { it.id }
            trip.value = TripUi(
                list = aggregate.list,
                rows = aggregate.entries.map { entry ->
                    ChecklistRow(entry = entry, item = byId[entry.itemId])
                },
            )
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
