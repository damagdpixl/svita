package com.damagdpixl.svita.ui.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.data.WearLogRepository
import com.damagdpixl.svita.core.designsystem.AvatarBodyType
import com.damagdpixl.svita.core.designsystem.AvatarSkinTone
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.core.weather.targetTempC
import com.damagdpixl.svita.data.AvatarPrefs
import com.damagdpixl.svita.data.OutfitPrefs
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Dress-me section state: loading flag + the resolved look (or its absence). */
data class DressMeState(
    val loading: Boolean = true,
    val look: DressMeLook? = null,
    /**
     * True after a run over the wardrobe produced no look (the style filter
     * excludes everything, or the wardrobe is too thin for the temperature).
     * An empty wardrobe sets this too — the screen tells the two cases apart
     * via [wardrobeCount].
     */
    val noLook: Boolean = false,
    /** Active item count at run time (empty-wardrobe vs filter-failure hint). */
    val wardrobeCount: Int = 0,
)

/**
 * Behind the Outfits tab (P2 T6): the «Сьогодні» dress-me pick, the comfort
 * slider (persisted in settings), the seeded style-tag chips, the wear button
 * and the «не носилось останні 60 днів» section.
 *
 * Dress-me runs REACTIVELY over the wardrobe flow: any item change, comfort
 * change, style-chip change or «Оновити» tap re-runs the engine. The run is
 * deterministic — same wardrobe + same inputs = same look.
 *
 * «Оновити» semantics (P2 T6 contract): the session accumulates the GARMENT
 * ids of every look already shown and passes them as the engine's
 * `excludeRecent` (the engine excludes garments, not look ids — excluding the
 * garments of shown looks is what guarantees a different combination). If the
 * exclusions ever starve the engine, the run clears them and retries once, so
 * refresh can never dead-end on a small wardrobe.
 *
 * Offline badge: the graph's weather cache only ever stores NETWORK samples,
 * so after `forDate` an empty cache for the bucket+date proves the climate
 * norms answered — the «офлайн» badge shows exactly then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutfitsViewModel(
    private val graph: SvitaGraph.Graph,
    private val today: () -> LocalDate = { todayIn() },
) : ViewModel() {

    private val repos = graph.repos

    val palette: StateFlow<List<PaletteColor>> = repos.taxonomy.observeColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val styleTags: StateFlow<List<StyleTag>> = repos.taxonomy.observeStyleTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Attribute definitions — the COLOR ids the renderer/mappers resolve. */
    val definitions: StateFlow<List<com.damagdpixl.svita.core.model.AttributeDefinition>> =
        repos.attributes.observeDefinitions(null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Paper-doll configuration (Settings -> Avatar) for the look rendering. */
    val avatarBody: StateFlow<AvatarBodyType>
    val avatarTone: StateFlow<AvatarSkinTone>

    /** «Не носилось останні 60 днів» — active items idle for the whole window. */
    val notWorn: StateFlow<List<Item>> = repos.wearLog
        .observeNotWornSince(today(), WearLogRepository.DEFAULT_IDLE_DAYS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Comfort slider (−1..+1); persisted to settings on every change. */
    val comfort = MutableStateFlow(0.0)

    /** Selected style chips (seed keys); empty set = no style constraint. */
    val selectedStyles = MutableStateFlow(emptySet<String>())

    /** Garment ids of looks already shown this session (see the class KDoc). */
    private val shownGarments = MutableStateFlow(emptySet<Long>())

    /** Bumped by [refresh] to force a re-run without changing any input. */
    private val refreshTick = MutableStateFlow(0)

    /** True right after «Носити» saved today's entry (button confirmation). */
    val wearSaved = MutableStateFlow(false)

    private val wardrobeItems: StateFlow<List<Item>> = repos.wardrobe
        .observeItems(ItemFilter())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dressMe: StateFlow<DressMeState> = combine(
        wardrobeItems,
        comfort,
        selectedStyles,
        refreshTick,
    ) { items, comfortValue, styles, _ ->
        Params(items, comfortValue, styles)
    }.flatMapLatest { params ->
        flow { emit(runDressMe(params)) }
            .flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DressMeState())

    init {
        viewModelScope.launch {
            comfort.value = repos.settings.getDouble(OutfitPrefs.COMFORT, 0.0)
        }
        val body = MutableStateFlow(AvatarBodyType.REGULAR)
        val tone = MutableStateFlow(AvatarSkinTone.LIGHT)
        avatarBody = body
        avatarTone = tone
        viewModelScope.launch {
            body.value = AvatarBodyType.fromId(repos.settings.getString(AvatarPrefs.AVATAR_BODY))
            tone.value = AvatarSkinTone.fromId(repos.settings.getString(AvatarPrefs.AVATAR_TONE))
        }
    }

    fun setComfort(value: Double) {
        comfort.value = value
        viewModelScope.launch { repos.settings.putDouble(OutfitPrefs.COMFORT, value) }
    }

    fun toggleStyle(key: String) {
        selectedStyles.value = if (key in selectedStyles.value) {
            selectedStyles.value - key
        } else {
            selectedStyles.value + key
        }
    }

    /**
     * «Оновити»: remember the currently shown look's garments so the next run
     * assembles something different, and nudge the pipeline.
     */
    fun refresh() {
        dressMe.value.look?.let { look ->
            shownGarments.value = shownGarments.value + look.items.map { it.item.id }
        }
        wearSaved.value = false
        refreshTick.value += 1
    }

    /** «Носити»: logs TODAY's wear entry from the current look's items. */
    fun wearToday() {
        val look = dressMe.value.look ?: return
        viewModelScope.launch {
            repos.wearLog.addEntry(
                date = today(),
                outfitId = null,
                itemIds = look.items.map { it.item.id },
                tempC = look.targetTempC,
                note = null,
            )
            wearSaved.value = true
        }
    }

    /** Clears the «додано» confirmation (e.g. when the dialog closes). */
    fun consumeWearSaved() {
        wearSaved.value = false
    }

    private data class Params(
        val items: List<Item>,
        val comfort: Double,
        val styles: Set<String>,
    )

    private suspend fun runDressMe(params: Params): DressMeState {
        val date = today()
        val lat = OutfitPrefs.DRESS_ME_LAT
        val lon = OutfitPrefs.DRESS_ME_LON
        return withContext(Dispatchers.IO) {
            val weather = graph.weather.forDate(lat, lon, date)
            // Provenance probe: the cache holds ONLY network samples (the
            // facade writes what it fetched), so a miss after the call means
            // the norms floor answered.
            val offline = graph.weatherCache.get(lat, lon, date) == null
            val target = targetTempC(weather, params.comfort)

            val aggregates = params.items.mapNotNull { repos.wardrobe.getItem(it.id) }
            val definitions = repos.attributes.definitions()
            val colorDefinitionIds = definitions
                .filter { it.type == AttributeType.COLOR }
                .map { it.id }
                .toSet()
            val mapper = EngineGarmentMapper(repos.taxonomy.observeColors().first())
            val knownStyleKeys = repos.taxonomy.observeStyleTags().first()
                .map { it.key }.toSet()
            val garments = aggregates.map { mapper.garment(it, colorDefinitionIds, knownStyleKeys) }

            var result = graph.engine.looks(
                garments = garments,
                targetTempC = target,
                styleTags = params.styles.toList(),
                excludeRecent = shownGarments.value,
            )
            if (result.looks.isEmpty() && shownGarments.value.isNotEmpty()) {
                // Dead-end recovery: the session exclusions starved a small
                // wardrobe — forget them and try once more.
                shownGarments.value = emptySet()
                result = graph.engine.looks(garments, target, params.styles.toList())
            }

            val byId = aggregates.associateBy { it.item.id }
            val look = result.looks.firstOrNull()?.let { engineLook ->
                DressMeLook(
                    lookId = engineLook.id,
                    items = engineLook.garmentIds.mapNotNull { byId[it] },
                    styles = engineLook.styles,
                    targetTempC = target,
                    weatherMinC = weather.tempMinC,
                    weatherMaxC = weather.tempMaxC,
                    condition = weather.condition,
                    offline = offline,
                )
            }
            DressMeState(
                loading = false,
                look = look,
                noLook = look == null,
                wardrobeCount = params.items.size,
            )
        }
    }
}
