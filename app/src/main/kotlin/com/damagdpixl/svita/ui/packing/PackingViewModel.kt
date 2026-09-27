package com.damagdpixl.svita.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Behind the Packing tab list screen (P2 T7): trips, the create wizard's
 * suggestion pipeline and the searchable wardrobe index for manual adds.
 *
 * Wizard suggestion semantics: [suggestionsFor] unions the item ids of every
 * wear_log entry in the trip range — REAL WEARS AND PLANS ALIKE (see
 * [suggestedItemIds] for the documented asymmetry with statistics).
 */
class PackingViewModel(
    graph: SvitaGraph.Graph,
) : ViewModel() {

    private val repos = graph.repos

    val trips: StateFlow<List<PackingList>> = repos.packing
        .observePackingLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active wardrobe items for the searchable picker (manual adds). */
    val items: StateFlow<List<Item>> = repos.wardrobe
        .observeItems(ItemFilter())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Suggested ids for the wizard's current date range; empty until loaded. */
    val suggestions = MutableStateFlow<List<Long>>(emptyList())

    /**
     * Loads the suggestion union for `[from, to]`. Called when the wizard's
     * dates become a valid range; re-called whenever they change.
     */
    fun suggestionsFor(from: LocalDate, to: LocalDate) {
        viewModelScope.launch {
            val entries = repos.wearLog.observeByRange(from, to).first()
            suggestions.value = suggestedItemIds(entries, from, to)
        }
    }

    /** Clears wizard suggestions when the dialog closes. */
    fun resetWizard() {
        suggestions.value = emptyList()
    }

    /**
     * Creates the list and seeds it with [itemIds] (suggestions kept by the
     * user plus manual adds) in one transaction; ids are deduplicated by the
     * repository.
     */
    fun createTrip(title: String, dateFrom: LocalDate?, dateTo: LocalDate?, itemIds: Collection<Long>) {
        viewModelScope.launch {
            repos.packing.createPackingList(
                title = title.trim(),
                dateFrom = dateFrom,
                dateTo = dateTo,
                itemIds = itemIds.toList(),
            )
            resetWizard()
        }
    }
}
