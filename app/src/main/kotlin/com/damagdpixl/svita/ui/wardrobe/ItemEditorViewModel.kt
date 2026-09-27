package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.ValueValidation
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class ItemEditorState(
    val loading: Boolean = true,
    val groups: List<SubtypeGroup> = emptyList(),
    val subtypeId: Long? = null,
    val subtypeMissing: Boolean = false,
    val name: String = "",
    val nameError: Boolean = false,
    val priceText: String = "",
    val priceError: Boolean = false,
    val dateText: String = "",
    val dateError: Boolean = false,
    val seasons: Set<Season> = Season.entries.toSet(),
    val sex: Sex? = null,
    val notes: String = "",
    /** Already-stored photo paths (edit mode). */
    val storedPhotos: List<String> = emptyList(),
    /** Fresh Photo Picker results, imported into private storage on save. */
    val pendingUris: List<Uri> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val tagInput: String = "",
    val attributeInputs: List<AttributeInput> = emptyList(),
    /** definitionId -> error message resource. */
    val attributeErrors: Map<Long, Int> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** The repository write itself failed (item NOT written); shows a banner. */
    val writeFailed: Boolean = false,
)

/**
 * Create/edit item form. Save validates EVERY filled attribute via
 * [com.damagdpixl.svita.core.data.AttributesRepository.validate] (the P2
 * contract: createItem/updateItem write values as-is), imports picked photos
 * into private storage (downscale + JPEG 85) and writes through the repository
 * in one transaction.
 */
class ItemEditorViewModel(
    private val graph: SvitaGraph.Graph,
    localeTag: String = "uk",
    private val itemId: Long?,
) : ViewModel() {

    private val repos = graph.repos
    private val ukrainian = localeTag.lowercase().startsWith("uk")

    /** Originals of the loaded item — removed ones get their files deleted. */
    private var originalPhotos: List<String> = emptyList()

    private val _state = MutableStateFlow(ItemEditorState())
    val state: StateFlow<ItemEditorState> = _state

    val palette: StateFlow<List<com.damagdpixl.svita.core.model.PaletteColor>> =
        repos.taxonomy.observeColors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = repos.wardrobe.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val groups = loadSubtypeGroups(repos)
            val aggregate = itemId?.let { repos.wardrobe.getItem(it) }
            if (itemId != null && aggregate == null) {
                // Unknown id: leave an empty form (the screen navigates back).
                _state.value = _state.value.copy(loading = false, groups = groups)
                return@launch
            }
            if (aggregate == null) {
                _state.value = _state.value.copy(loading = false, groups = groups)
                return@launch
            }
            originalPhotos = aggregate.photos.sortedBy { photo -> photo.position }.map { it.path }
            val definitions = repos.attributes
                .observeDefinitions(aggregate.subtype.categoryId)
                .first()
            val storedValues = aggregate.attributes.associate { entry ->
                entry.definitionId to entry.value
            }
            _state.value = _state.value.copy(
                loading = false,
                groups = groups,
                subtypeId = aggregate.subtype.id,
                name = aggregate.item.name,
                priceText = aggregate.item.price?.let(::formatPrice).orEmpty(),
                dateText = aggregate.item.purchaseDate?.toString().orEmpty(),
                seasons = aggregate.item.seasons,
                sex = aggregate.item.sex,
                notes = aggregate.item.notes.orEmpty(),
                storedPhotos = originalPhotos,
                selectedTagIds = aggregate.tags.map { it.id }.toSet(),
                attributeInputs = definitions.map { definition ->
                    AttributeInput(definition, storedValues[definition.id].orEmpty())
                },
            )
        }
    }

    fun selectSubtype(id: Long) {
        _state.value = _state.value.copy(subtypeId = id, subtypeMissing = false)
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value, nameError = false)
    }

    fun setPrice(value: String) {
        _state.value = _state.value.copy(priceText = value, priceError = false)
    }

    fun setPurchaseDate(value: String) {
        _state.value = _state.value.copy(dateText = value, dateError = false)
    }

    fun toggleSeason(season: Season) {
        _state.value = _state.value.copy(
            seasons = _state.value.seasons.toggle(season),
        )
    }

    fun setSex(sex: Sex?) {
        _state.value = _state.value.copy(sex = if (_state.value.sex == sex) null else sex)
    }

    fun setNotes(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun addPhotos(uris: List<Uri>) {
        _state.value = _state.value.copy(pendingUris = _state.value.pendingUris + uris)
    }

    fun removeStoredPhoto(path: String) {
        _state.value = _state.value.copy(storedPhotos = _state.value.storedPhotos - path)
    }

    fun removePendingPhoto(uri: Uri) {
        _state.value = _state.value.copy(pendingUris = _state.value.pendingUris - uri)
    }

    fun toggleTag(id: Long) {
        _state.value = _state.value.copy(selectedTagIds = _state.value.selectedTagIds.toggle(id))
    }

    fun setTagInput(value: String) {
        _state.value = _state.value.copy(tagInput = value)
    }

    /** Create-on-type: resolves an existing tag by exact name or creates one. */
    fun commitTagInput() {
        val name = _state.value.tagInput.trim()
        if (name.isEmpty()) return
        _state.value = _state.value.copy(tagInput = "")
        viewModelScope.launch {
            val existing = repos.wardrobe.tagByName(name)
            val id = existing?.id ?: repos.wardrobe.createTag(name)
            _state.value = _state.value.copy(selectedTagIds = _state.value.selectedTagIds + id)
        }
    }

    fun setAttributeValue(definitionId: Long, value: String) {
        _state.value = _state.value.copy(
            attributeInputs = _state.value.attributeInputs.map { input ->
                if (input.definition.id == definitionId) {
                    input.copy(value = value)
                } else {
                    input
                }
            },
            attributeErrors = _state.value.attributeErrors - definitionId,
        )
    }

    /** Creates a new attribute definition scoped to the selected subtype's category. */
    fun addAttributeDefinition(
        name: String,
        type: AttributeType,
        optionsText: String,
        minText: String,
        maxText: String,
    ) {
        val key = name.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            val categoryId = _state.value.subtypeId?.let { subtypeId ->
                _state.value.groups
                    .firstOrNull { group -> group.subtypes.any { it.id == subtypeId } }
                    ?.category?.id
            }
            val config: String? = when (type) {
                AttributeType.ENUM, AttributeType.MULTI ->
                    optionsText.split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .takeIf { it.isNotEmpty() }
                        ?.let(::encodeOptionsConfig)

                AttributeType.NUMBER ->
                    encodeNumberConfig(parsePrice(minText), parsePrice(maxText))

                else -> null
            }
            // A duplicate key raises UNIQUE on the database — surface it as a
            // no-op instead of crashing the coroutine scope.
            val definitionId = runCatching {
                repos.attributes.createDefinition(
                    categoryId = categoryId,
                    key = key.lowercase().replace(' ', '_'),
                    type = type,
                    config = config,
                    sortOrder = 0,
                )
            }.getOrNull() ?: return@launch
            val definition = repos.attributes.definition(definitionId) ?: return@launch
            _state.value = _state.value.copy(
                attributeInputs = _state.value.attributeInputs + AttributeInput(definition, ""),
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val subtypeId = current.subtypeId
        val name = current.name.trim()
        _state.value = current.copy(
            nameError = name.isEmpty(),
            subtypeMissing = subtypeId == null,
            writeFailed = false,
        )
        if (name.isEmpty() || subtypeId == null) return

        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val price = if (current.priceText.isBlank()) {
                null
            } else {
                parsePrice(current.priceText) ?: run {
                    _state.value = _state.value.copy(saving = false, priceError = true)
                    return@launch
                }
            }
            val purchaseDate = if (current.dateText.isBlank()) {
                null
            } else {
                runCatching { LocalDate.parse(current.dateText.trim()) }.getOrNull() ?: run {
                    _state.value = _state.value.copy(saving = false, dateError = true)
                    return@launch
                }
            }
            val values = LinkedHashMap<Long, String>()
            val errors = LinkedHashMap<Long, Int>()
            for (input in current.attributeInputs) {
                if (input.value.isBlank()) continue
                when (val outcome = repos.attributes.validate(input.definition.id, input.value)) {
                    is ValueValidation.Valid -> values[input.definition.id] = outcome.normalized
                    is ValueValidation.Invalid -> errors[input.definition.id] = valueErrorRes(outcome.kind)
                }
            }
            if (errors.isNotEmpty()) {
                _state.value = _state.value.copy(saving = false, attributeErrors = errors)
                return@launch
            }
            val imported = graph.photoStore.import(current.pendingUris)
            val photos = current.storedPhotos + imported
            val draft = ItemDraft(
                subtypeId = subtypeId,
                name = name,
                notes = current.notes.trim().takeIf { it.isNotEmpty() },
                price = price,
                purchaseDate = purchaseDate,
                seasons = current.seasons,
                sex = current.sex,
            )
            val written = runCatching {
                if (itemId == null) {
                    repos.wardrobe.createItem(
                        draft = draft,
                        photos = photos,
                        tagIds = current.selectedTagIds,
                        attributeValues = values,
                    )
                } else {
                    repos.wardrobe.updateItem(
                        id = itemId,
                        draft = draft,
                        photos = photos,
                        tagIds = current.selectedTagIds,
                        attributeValues = values,
                    )
                }
            }
            written.fold(
                onSuccess = {
                    // Photo files replaced or dropped in this edit leave
                    // orphans — remove.
                    val finalPaths = photos.toSet()
                    originalPhotos.filter { it !in finalPaths }
                        .forEach(graph.photoStore::deleteIfOwned)
                    originalPhotos = photos
                    _state.value = _state.value.copy(saving = false, saved = true)
                },
                onFailure = {
                    // The row was not written: the just-imported files would
                    // become orphans — delete them.
                    imported.forEach(graph.photoStore::deleteIfOwned)
                    _state.value = _state.value.copy(saving = false, writeFailed = true)
                },
            )
        }
    }

    fun localizeSubtype(subtype: com.damagdpixl.svita.core.model.Subtype): String =
        if (ukrainian) subtype.nameUk else subtype.nameEn

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value
}
