package com.damagdpixl.svita.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ItemDetailState(
    val aggregate: ItemAggregate? = null,
    /** All attribute definitions keyed by id, for label/type-aware rendering. */
    val definitionsById: Map<Long, AttributeDefinition> = emptyMap(),
    /** Wear counter via the batch [WearLogRepository.wearCounts] read. */
    val wearCount: Long = 0,
    val deleted: Boolean = false,
)

/**
 * Item detail: full aggregate + wear counter + archive/delete actions. Wear
 * statistics come from the single batch call `wearCounts()` (P2 contract: no
 * per-item lastWorn calls).
 */
class ItemDetailViewModel(
    private val graph: SvitaGraph.Graph,
    private val itemId: Long,
) : ViewModel() {

    private val repos = graph.repos

    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val aggregate = repos.wardrobe.getItem(itemId)
            val wearCount = repos.wearLog.wearCounts()
                .firstOrNull { count -> count.itemId == itemId }?.wearCount ?: 0L
            val definitions = repos.attributes.definitions().associateBy { it.id }
            _state.value = ItemDetailState(
                aggregate = aggregate,
                definitionsById = definitions,
                wearCount = wearCount,
            )
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            repos.wardrobe.setArchived(itemId, archived)
            reload()
        }
    }

    fun delete() {
        viewModelScope.launch {
            val aggregate = _state.value.aggregate
            repos.wardrobe.deleteItem(itemId)
            // The row is gone; drop the photo files it owned.
            aggregate?.photos?.forEach { photo ->
                graph.photoStore.deleteIfOwned(photo.path)
            }
            _state.value = _state.value.copy(deleted = true)
        }
    }
}
