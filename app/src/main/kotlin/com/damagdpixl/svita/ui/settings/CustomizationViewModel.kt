package com.damagdpixl.svita.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.CategoryDeleteBlock
import com.damagdpixl.svita.core.data.CategoryDeleteResult
import com.damagdpixl.svita.core.data.ResetTaxonomyResult
import com.damagdpixl.svita.core.data.SortMove
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.encodeNumberConfig
import com.damagdpixl.svita.ui.wardrobe.encodeOptionsConfig
import com.damagdpixl.svita.ui.wardrobe.parsePrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot outcome surfaced as a Ukrainian dialog/banner by the screen. */
sealed interface CustomizationEvent {
    data class CategoryDeleteBlocked(val reason: CategoryDeleteBlock) : CustomizationEvent
    data object ResetBlocked : CustomizationEvent
}

data class CustomizationState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    /** categoryId -> item count; categories without items are absent. */
    val itemCounts: Map<Long, Long> = emptyMap(),
    val definitions: List<AttributeDefinition> = emptyList(),
    val event: CustomizationEvent? = null,
)

/**
 * Behind the Кастомізація screen: category editing (add / rename / reorder /
 * delete / reset to defaults) and custom-field (attribute definition) CRUD.
 * Reads are reactive repository flows; writes go through TaxonomyEditor and
 * Attributes repositories, so the item editor sees every change immediately.
 */
class CustomizationViewModel(private val graph: SvitaGraph.Graph) : ViewModel() {

    private val repos = graph.repos

    private val _state = MutableStateFlow(CustomizationState())
    val state: StateFlow<CustomizationState> = _state

    private val counts = MutableStateFlow<Map<Long, Long>>(emptyMap())

    val categories: StateFlow<List<Category>> = repos.taxonomy.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val definitions: StateFlow<List<AttributeDefinition>> = repos.attributes.observeDefinitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Recompute the displayed list whenever any of the three sources
            // changes; item counts refresh after each local write (refreshCounts).
            combine(
                categories,
                definitions,
                counts,
            ) { cats, defs, itemCounts ->
                CustomizationState(
                    loading = false,
                    categories = cats,
                    itemCounts = itemCounts,
                    definitions = defs,
                    event = _state.value.event,
                )
            }.collect { _state.value = it }
        }
        refreshCounts()
    }

    fun consumeEvent() {
        _state.value = _state.value.copy(event = null)
    }

    fun createCategory(section: Section, nameEn: String, nameUk: String, icon: String?) {
        viewModelScope.launch {
            repos.taxonomyEditor.createCategory(section, nameEn, nameUk, icon)
            refreshCounts()
        }
    }

    fun updateCategory(id: Long, nameEn: String, nameUk: String, icon: String?) {
        viewModelScope.launch {
            repos.taxonomyEditor.updateCategory(id, nameEn, nameUk, icon)
        }
    }

    fun updateCustomCategory(
        id: Long,
        section: Section,
        nameEn: String,
        nameUk: String,
        icon: String?,
        sortOrder: Int,
    ) {
        viewModelScope.launch {
            repos.taxonomyEditor.updateCustomCategory(id, section, nameEn, nameUk, icon, sortOrder)
        }
    }

    fun moveCategory(id: Long, direction: SortMove) {
        viewModelScope.launch {
            runCatching { repos.taxonomyEditor.moveCustomCategory(id, direction) }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            when (val result = repos.taxonomyEditor.deleteCategory(id)) {
                is CategoryDeleteResult.Deleted -> refreshCounts()
                is CategoryDeleteResult.Blocked -> _state.value = _state.value.copy(
                    event = CustomizationEvent.CategoryDeleteBlocked(result.reason),
                )
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            when (repos.taxonomyEditor.resetToDefaults()) {
                ResetTaxonomyResult.Reset -> refreshCounts()
                ResetTaxonomyResult.BlockedByItems -> _state.value = _state.value.copy(
                    event = CustomizationEvent.ResetBlocked,
                )
            }
        }
    }

    /**
     * Creates a definition. Config JSON matches the internal
     * :core:data AttributeValidator shapes exactly (mirrored encoders live in
     * the wardrobe Common.kt). A duplicate key+scope is refused client-side —
     * [onResult] reports false, the caller keeps the dialog open with the
     * inline `field_duplicate` error; true is reported only after the write.
     */
    fun createDefinition(
        name: String,
        type: AttributeType,
        options: List<String>,
        minText: String,
        maxText: String,
        categoryId: Long?,
        onResult: (Boolean) -> Unit,
    ) {
        val key = definitionKey(name)
        if (key.isEmpty()) {
            onResult(false)
            return
        }
        if (_state.value.definitions.any { it.key == key && it.categoryId == categoryId }) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = runCatching {
                repos.attributes.createDefinition(
                    categoryId = categoryId,
                    key = key,
                    type = type,
                    config = buildConfig(type, options, minText, maxText),
                    sortOrder = 0,
                )
            }.isSuccess
            onResult(success)
        }
    }

    fun updateDefinition(
        id: Long,
        name: String,
        type: AttributeType,
        options: List<String>,
        minText: String,
        maxText: String,
        categoryId: Long?,
        onResult: (Boolean) -> Unit,
    ) {
        val key = definitionKey(name)
        if (key.isEmpty()) {
            onResult(false)
            return
        }
        if (_state.value.definitions.any { it.key == key && it.categoryId == categoryId && it.id != id }) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = runCatching {
                repos.attributes.updateDefinition(
                    id = id,
                    categoryId = categoryId,
                    key = key,
                    type = type,
                    config = buildConfig(type, options, minText, maxText),
                    sortOrder = 0,
                )
            }.isSuccess
            onResult(success)
        }
    }

    fun deleteDefinition(id: Long) {
        viewModelScope.launch {
            repos.attributes.deleteDefinition(id)
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            counts.value = repos.taxonomyEditor.itemCountsByCategory()
        }
    }

    private fun buildConfig(
        type: AttributeType,
        options: List<String>,
        minText: String,
        maxText: String,
    ): String? = when (type) {
        AttributeType.ENUM, AttributeType.MULTI ->
            options.filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.let(::encodeOptionsConfig)

        AttributeType.NUMBER -> encodeNumberConfig(parsePrice(minText), parsePrice(maxText))

        else -> null
    }

    companion object {
        /** Display name -> storage key (lowercase, spaces to underscores). */
        fun definitionKey(name: String): String = name.trim().lowercase().replace(' ', '_')
    }
}
