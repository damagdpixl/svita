package com.damagdpixl.svita.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.data.WardrobePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Gallery grid state: items grouped by category, ready to render. */
data class GalleryItem(
    val item: Item,
    val coverPath: String?,
    val categoryName: String,
    val categorySort: Int,
)

data class GallerySection(
    val categoryId: Long,
    val categoryName: String,
    val items: List<GalleryItem>,
)

data class GalleryState(
    val sections: List<GallerySection>,
    /** Items matching the CURRENT filter. */
    val visibleCount: Int,
    /** All items including archived, ignoring the filter (empty-state decision). */
    val totalCount: Int,
    /** True while a name search or filter is active and nothing matched. */
    val emptyResult: Boolean,
)

/** Active wardrobe filters, mapped 1:1 onto [ItemFilter] fields. */
data class FilterState(
    val seasons: Set<Season> = emptySet(),
    val sex: Sex? = null,
    val tagIds: Set<Long> = emptySet(),
    val colorDefinitionId: Long? = null,
    val colorHex: String? = null,
    val includeArchived: Boolean = false,
) {
    val hasConstraints: Boolean
        get() = seasons.isNotEmpty() || sex != null || tagIds.isNotEmpty() ||
            colorDefinitionId != null || colorHex != null
}

/**
 * Wardrobe gallery: filtered item feed + reactive cover-photo index + the
 * onboarding/import-hint flags. Search is debounced (300 ms) — the SQL filter
 * is rebuilt per keystroke otherwise. [localeTag] localizes seeded taxonomy
 * names («uk» shows Ukrainian, anything else English).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WardrobeViewModel(
    private val graph: SvitaGraph.Graph,
    private val localeTag: String = "uk",
) : ViewModel() {

    private val repos = graph.repos
    private val ukrainian: Boolean = localeTag.lowercase().startsWith("uk")

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(FilterState())

    val tags: StateFlow<List<Tag>> = repos.wardrobe.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val palette: StateFlow<List<PaletteColor>> = repos.taxonomy.observeColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val colorDefinitions: StateFlow<List<AttributeDefinition>> = repos.attributes
        .observeDefinitions(null)
        .map { definitions -> definitions.filter { it.type == AttributeType.COLOR } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * First-run detection. Tri-state on purpose: `null` = flag not read yet
     * (render nothing so the screen never flips grid→wizard mid-composition —
     * a discarded mid-flip composition is what made the wizard race in
     * Robolectric); `false` = show the wizard; `true` = show the gallery.
     */
    val onboardingDone: StateFlow<Boolean?> = repos.settings
        .observe(WardrobePrefs.ONBOARDING_DONE)
        .map { it == "true" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val importHintDismissed: StateFlow<Boolean> = repos.settings
        .observe(WardrobePrefs.IMPORT_HINT_DISMISSED)
        .map { it == "true" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** subtypeId -> owning category, loaded once when the VM is created. */
    private val taxonomyIndex = MutableStateFlow<Map<Long, Category>>(emptyMap())

    private val totalCount: StateFlow<Int> = repos.wardrobe
        .observeItems(ItemFilter(includeArchived = true))
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            taxonomyIndex.value = buildTaxonomyIndex()
        }
    }

    val gallery: StateFlow<GalleryState?> = combine(
        filter,
        query.debounce(SEARCH_DEBOUNCE_MS).onStart { emit(query.value) },
        graph.coverPhotos,
        taxonomyIndex,
        totalCount,
    ) { filterState, queryText, covers, categories, total ->
        Combined(filterState, queryText.takeIf { it.isNotBlank() }, covers, categories, total)
    }.flatMapLatest { combined ->
        repos.wardrobe.observeItems(combined.toItemFilter()).map { items ->
            combined.toState(items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        query.value = value
    }

    fun updateFilter(transform: (FilterState) -> FilterState) {
        filter.value = transform(filter.value)
    }

    fun resetFilters() {
        filter.value = filter.value.copy(
            seasons = emptySet(),
            sex = null,
            tagIds = emptySet(),
            colorDefinitionId = null,
            colorHex = null,
        )
    }

    fun setArchivedVisible(visible: Boolean) {
        filter.value = filter.value.copy(includeArchived = visible)
    }

    fun dismissImportHint() {
        viewModelScope.launch {
            repos.settings.putBoolean(WardrobePrefs.IMPORT_HINT_DISMISSED, true)
        }
    }

    fun localized(category: Category): String =
        if (ukrainian) category.nameUk else category.nameEn

    private data class Combined(
        val filterState: FilterState,
        val nameQuery: String?,
        val covers: Map<Long, String>,
        val categories: Map<Long, Category>,
        val total: Int,
    )

    private fun Combined.toItemFilter() = ItemFilter(
        includeArchived = filterState.includeArchived,
        seasons = filterState.seasons,
        sex = filterState.sex,
        tagIds = filterState.tagIds,
        colorDefinitionId = filterState.colorDefinitionId,
        colorHex = filterState.colorHex,
        nameQuery = nameQuery,
    )

    private fun Combined.toState(items: List<Item>): GalleryState {
        val localizedItems = items.map { item ->
            val category = categories[item.subtypeId]
            GalleryItem(
                item = item,
                coverPath = covers[item.id],
                categoryName = category?.let { localized(it) }.orEmpty(),
                categorySort = category?.sortOrder ?: Int.MAX_VALUE,
            )
        }
        val sections = localizedItems
            .groupBy { it.categorySort to it.categoryName }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (_, galleryItems) ->
                GallerySection(
                    categoryId = categories[galleryItems.first().item.subtypeId]?.id ?: -1L,
                    categoryName = galleryItems.first().categoryName,
                    items = galleryItems.sortedBy { it.item.id },
                )
            }
        return GalleryState(
            sections = sections,
            visibleCount = items.size,
            totalCount = total,
            emptyResult = items.isEmpty() && (nameQuery != null || filterState.hasConstraints),
        )
    }

    private suspend fun buildTaxonomyIndex(): Map<Long, Category> {
        val index = LinkedHashMap<Long, Category>()
        for (category in repos.taxonomy.categories()) {
            for (subtype: Subtype in repos.taxonomy.subtypesByCategory(category.id)) {
                index[subtype.id] = category
            }
        }
        return index
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** Public subtype view for pickers (grouped by category). */
data class SubtypeGroup(
    val category: Category,
    val subtypes: List<Subtype>,
)
