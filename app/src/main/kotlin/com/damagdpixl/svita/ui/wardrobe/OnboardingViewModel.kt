package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.ValueValidation
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.data.WardrobePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The four wizard steps. */
enum class WizardStep {
    PHOTO,
    SUBTYPE,
    NAME,
    ATTRIBUTES,
}

data class OnboardingState(
    val step: WizardStep = WizardStep.PHOTO,
    val groups: List<SubtypeGroup> = emptyList(),
    val pickedUris: List<Uri> = emptyList(),
    val subtype: Subtype? = null,
    val name: String = "",
    val attributeInputs: List<AttributeInput> = emptyList(),
    /** definitionId -> error message resource. */
    val attributeErrors: Map<Long, Int> = emptyMap(),
    val saving: Boolean = false,
    val finished: Boolean = false,
)

/**
 * First-run manual-add wizard: photo (optional) -> subtype -> name ->
 * attributes (optional). Skippable at every step; finishing stores the first
 * item through the repositories and flips the onboarding flag.
 */
class OnboardingViewModel(
    private val graph: SvitaGraph.Graph,
    localeTag: String = "uk",
) : ViewModel() {

    private val repos = graph.repos
    private val ukrainian = localeTag.lowercase().startsWith("uk")

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    val palette: StateFlow<List<com.damagdpixl.svita.core.model.PaletteColor>> =
        repos.taxonomy.observeColors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Load FIRST, then re-read the state for the copy: a copy whose
            // receiver is evaluated before a suspending argument would write a
            // stale snapshot (step reset back to PHOTO) over any user input
            // that landed while the taxonomy was loading.
            val groups = loadSubtypeGroups(repos)
            _state.value = _state.value.copy(groups = groups)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        _state.value = _state.value.copy(pickedUris = _state.value.pickedUris + uris)
    }

    fun removePhoto(uri: Uri) {
        _state.value = _state.value.copy(pickedUris = _state.value.pickedUris - uri)
    }

    fun selectSubtype(subtype: Subtype) {
        viewModelScope.launch {
            val definitions = repos.attributes.observeDefinitions(subtype.categoryId).first()
            _state.value = _state.value.copy(
                subtype = subtype,
                name = _state.value.name.ifBlank { localize(subtype) },
                attributeInputs = definitions.map { AttributeInput(it, "") },
                attributeErrors = emptyMap(),
            )
        }
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
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

    fun next() {
        _state.value = _state.value.copy(step = advance(_state.value.step, +1))
    }

    fun back() {
        _state.value = _state.value.copy(step = advance(_state.value.step, -1))
    }

    /** Skips the wizard entirely: no item, onboarding marked done. */
    fun skip() {
        viewModelScope.launch {
            repos.settings.putBoolean(WardrobePrefs.ONBOARDING_DONE, true)
            _state.value = _state.value.copy(finished = true)
        }
    }

    /**
     * Completes the wizard: validates every filled attribute through
     * [com.damagdpixl.svita.core.data.AttributesRepository.validate], imports
     * photos and creates the first item. Requires a subtype — without one the
     * wizard jumps back to the subtype step instead.
     */
    fun finish() {
        val current = _state.value
        if (current.saving) return
        val subtype = current.subtype
        if (subtype == null) {
            _state.value = current.copy(step = WizardStep.SUBTYPE)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
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
            val name = current.name.trim().ifEmpty { localize(subtype) }
            val photos = graph.photoStore.import(current.pickedUris)
            repos.wardrobe.createItem(
                draft = ItemDraft(
                    subtypeId = subtype.id,
                    name = name,
                    seasons = Season.entries.toSet(),
                ),
                photos = photos,
                attributeValues = values,
            )
            repos.settings.putBoolean(WardrobePrefs.ONBOARDING_DONE, true)
            _state.value = _state.value.copy(saving = false, finished = true)
        }
    }

    fun localize(subtype: Subtype): String =
        if (ukrainian) subtype.nameUk else subtype.nameEn
}

private fun advance(current: WizardStep, delta: Int): WizardStep {
    val steps = WizardStep.entries
    val index = (current.ordinal + delta).coerceIn(0, steps.lastIndex)
    return steps[index]
}
