package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.PhotoHash
import com.damagdpixl.svita.data.SvitaGraph
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One photo in the pending import batch. */
data class ImportPhoto(
    val uri: Uri,
    /**
     * Stable identity for test tags and lookups — synthetic and unique per
     * entry on purpose: the SAME URI can legitimately be picked twice (kept
     * via «все одно додати»), so the URI itself is not a key.
     */
    val key: String,
    /** Item name default (file name without extension), editable per photo. */
    val name: String,
    /** pHash-lite word, or null when the source could not be hashed. */
    val hash: Long?,
    /** Per-photo subtype override; null = use the batch-shared subtype. */
    val subtypeOverrideId: Long? = null,
)

/**
 * A queued "possible duplicate" decision: the photo was picked, but its hash is
 * within [PhotoHash.MAX_DISTANCE_BITS] of an existing wardrobe photo (then
 * [existingItemName] names the closest item) or of another photo already in
 * this batch (then it is null).
 */
data class ImportDuplicateWarning(
    val photo: ImportPhoto,
    val existingItemName: String?,
)

/** Why one batch entry failed. Deliberately string-free (UI maps to resources). */
enum class ImportFailureKind {

    /** PhotoStore could not read/decode/copy the picked image. */
    UNREADABLE_PHOTO,

    /** The photo was stored but the repository write failed (item NOT written). */
    WRITE_FAILED,
}

data class ImportFailure(
    val photo: ImportPhoto,
    val kind: ImportFailureKind,
)

/** Import pass phase: editing the batch, or one sequential create pass running. */
sealed interface ImportPhase {
    data object Editing : ImportPhase

    /**
     * A pass is running. [created] counts only successfully written items —
     * failures do not advance it («Додано X з Y» stays honest).
     */
    data class Importing(val created: Int, val total: Int) : ImportPhase
}

data class ImportUiState(
    val photos: List<ImportPhoto> = emptyList(),
    val groups: List<SubtypeGroup> = emptyList(),
    /** Subtype applied to every photo without an override. */
    val sharedSubtypeId: Long? = null,
    val subtypeMissing: Boolean = false,
    val seasons: Set<Season> = Season.entries.toSet(),
    val sex: Sex? = null,
    val selectedTagIds: Set<Long> = emptySet(),
    val tagInput: String = "",
    /** True while freshly picked photos are being hashed. */
    val analyzing: Boolean = false,
    /** Queued duplicate decisions, resolved one dialog at a time. */
    val pendingWarnings: List<ImportDuplicateWarning> = emptyList(),
    /** How many existing photos were hashed for dedupe (0 = still computing). */
    val existingHashCount: Int = 0,
    /** Keys of photos whose edited name is blank (blocks the import). */
    val blankNameKeys: Set<String> = emptySet(),
    val phase: ImportPhase = ImportPhase.Editing,
    /** Failures of the last pass; empty until a pass ran. */
    val lastFailures: List<ImportFailure> = emptyList(),
    /** Items created by this screen in total (accumulates over retries). */
    val createdCount: Int = 0,
    /** Set once by [ImportViewModel.finish]; the screen navigates back on it. */
    val navigateBack: Boolean = false,
)

/**
 * Bulk gallery import: picked photos become one item each through
 * [com.damagdpixl.svita.data.PhotoStore] (private copy + downscale) and
 * `createItem` in ONE sequential progress pass; failures stay in the batch for
 * retry while successes are removed.
 *
 * Dedupe (pHash-lite): when the screen opens, hashes of existing wardrobe
 * photos are computed (first [EXISTING_ITEM_CAP] items — a deliberate cap so
 * huge wardrobes open fast; hashes of older items are simply not warned on).
 * Each picked photo is hashed the same way; a hit within
 * [PhotoHash.MAX_DISTANCE_BITS] queues a warning dialog («все одно додати» /
 * cancel). Hashes are recomputed per screen open — no schema change, honest
 * cost: one tiny sampled decode per existing photo, off the main thread.
 *
 * [displayNameOf] and [hashOf] are injected because the graph does not own a
 * Context: the screen wires them to the activity ContentResolver, tests to
 * fakes.
 */
class ImportViewModel(
    private val graph: SvitaGraph.Graph,
    localeTag: String = "uk",
    private val displayNameOf: (Uri) -> String,
    private val hashOf: suspend (Uri) -> Long?,
    /** Test seam for cancellation: null = [viewModelScope] (production). */
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val repos = graph.repos
    private val ukrainian = localeTag.lowercase().startsWith("uk")
    private val scope: CoroutineScope get() = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** Monotonic source of synthetic per-entry keys (see [ImportPhoto.key]). */
    private val keyCounter = AtomicInteger()

    val tags: StateFlow<List<Tag>> = repos.wardrobe.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** pHash-lite word -> name of the first existing item that produced it. */
    private var existingByHash: Map<Long, String> = emptyMap()

    init {
        scope.launch {
            val groups = loadSubtypeGroups(repos)
            _state.value = _state.value.copy(groups = groups)
            existingByHash = computeExistingHashes()
            _state.value = _state.value.copy(existingHashCount = existingByHash.size)
        }
    }

    /**
     * Adds picked photos to the batch. Each is hashed and compared against the
     * existing wardrobe hashes AND the photos already queued in this batch;
     * clashes queue warning dialogs, the rest joins the batch directly.
     */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            _state.value = _state.value.copy(analyzing = true)
            val clean = mutableListOf<ImportPhoto>()
            val warnings = mutableListOf<ImportDuplicateWarning>()
            val batchHashes = mutableSetOf<Long>()
            batchHashes.addAll(_state.value.photos.mapNotNull { it.hash })
            for (uri in uris) {
                val hash = hashOf(uri)
                val photo = ImportPhoto(
                    uri = uri,
                    key = newPhotoKey(),
                    name = defaultName(uri),
                    hash = hash,
                )
                val existingName = hash?.let { hash ->
                    existingByHash.entries
                        .firstOrNull { (other, _) -> PhotoHash.isLikelyDuplicate(hash, other) }
                        ?.value
                }
                val clashesWithBatch = hash != null && hash in batchHashes
                if (existingName != null || clashesWithBatch) {
                    warnings += ImportDuplicateWarning(photo, existingName)
                } else {
                    clean += photo
                    hash?.let(batchHashes::add)
                }
            }
            _state.value = _state.value.copy(
                analyzing = false,
                photos = _state.value.photos + clean,
                pendingWarnings = _state.value.pendingWarnings + warnings,
            )
        }
    }

    /** Keeps the warned photo in the batch anyway and moves to the next warning. */
    fun keepDuplicate() {
        val warning = _state.value.pendingWarnings.firstOrNull() ?: return
        _state.value = _state.value.copy(
            pendingWarnings = _state.value.pendingWarnings - warning,
            photos = _state.value.photos + warning.photo,
        )
    }

    /** Drops the warned photo and moves to the next warning. */
    fun discardDuplicate() {
        val warning = _state.value.pendingWarnings.firstOrNull() ?: return
        _state.value = _state.value.copy(pendingWarnings = _state.value.pendingWarnings - warning)
    }

    fun selectSharedSubtype(id: Long) {
        _state.value = _state.value.copy(sharedSubtypeId = id, subtypeMissing = false)
    }

    /** Per-photo subtype override; null resets to the shared subtype. */
    fun setPhotoSubtype(key: String, subtypeId: Long?) {
        _state.value = _state.value.copy(
            photos = _state.value.photos.map { photo ->
                if (photo.key == key) photo.copy(subtypeOverrideId = subtypeId) else photo
            },
        )
    }

    fun setPhotoName(key: String, name: String) {
        _state.value = _state.value.copy(
            photos = _state.value.photos.map { photo ->
                if (photo.key == key) photo.copy(name = name) else photo
            },
            blankNameKeys = if (name.isBlank()) {
                _state.value.blankNameKeys + key
            } else {
                _state.value.blankNameKeys - key
            },
        )
    }

    fun toggleSeason(season: Season) {
        _state.value = _state.value.copy(seasons = _state.value.seasons.toggle(season))
    }

    fun setSex(sex: Sex?) {
        _state.value = _state.value.copy(sex = if (_state.value.sex == sex) null else sex)
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
        scope.launch {
            val existing = repos.wardrobe.tagByName(name)
            val id = existing?.id ?: runCatching { repos.wardrobe.createTag(name) }.getOrNull()
                ?: return@launch
            _state.value = _state.value.copy(selectedTagIds = _state.value.selectedTagIds + id)
        }
    }

    /**
     * Runs one import pass over the whole batch (after a pass with failures the
     * batch IS the failed remainder, so "retry" is just another [startImport]).
     * Photos are processed strictly sequentially so the progress counter and a
     * possible cancel stay meaningful.
     */
    fun startImport() {
        val current = _state.value
        if (current.phase != ImportPhase.Editing || current.photos.isEmpty()) return
        if (current.sharedSubtypeId == null && current.photos.any { it.subtypeOverrideId == null }) {
            _state.value = current.copy(subtypeMissing = true)
            return
        }
        val blank = current.photos.filter { it.name.isBlank() }.map { it.key }
        if (blank.isNotEmpty()) {
            _state.value = current.copy(blankNameKeys = blank.toSet())
            return
        }
        runPass(current.photos)
    }

    /** Reports the summary badge and asks the screen to navigate back to the grid. */
    fun finish() {
        if (_state.value.createdCount > 0) {
            graph.importSummary.value = _state.value.createdCount
        }
        _state.value = _state.value.copy(navigateBack = true)
    }

    fun localizeSubtype(subtype: Subtype): String =
        if (ukrainian) subtype.nameUk else subtype.nameEn

    fun localizeCategory(category: Category): String =
        if (ukrainian) category.nameUk else category.nameEn

    private fun runPass(batch: List<ImportPhoto>) {
        _state.value = _state.value.copy(
            phase = ImportPhase.Importing(created = 0, total = batch.size),
            subtypeMissing = false,
            blankNameKeys = emptySet(),
        )
        scope.launch {
            var createdInPass = 0
            val failures = mutableListOf<ImportFailure>()
            for (photo in batch) {
                // Cancellation (scope cleared, e.g. OS-back mid-pass) must stop
                // the loop — never mark the remaining photos as failed.
                coroutineContext.ensureActive()
                val storedPath = try {
                    graph.photoStore.import(photo.uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                }
                if (storedPath == null) {
                    failures += ImportFailure(photo, ImportFailureKind.UNREADABLE_PHOTO)
                } else {
                    try {
                        val subtypeId = photo.subtypeOverrideId
                            ?: _state.value.sharedSubtypeId
                            ?: error("subtype vanished mid-pass")
                        repos.wardrobe.createItem(
                            draft = ItemDraft(
                                subtypeId = subtypeId,
                                name = photo.name.trim(),
                                seasons = _state.value.seasons,
                                sex = _state.value.sex,
                            ),
                            photos = listOf(storedPath),
                            tagIds = _state.value.selectedTagIds,
                        )
                        createdInPass += 1
                    } catch (e: CancellationException) {
                        // No orphan-file cleanup here on purpose: the item write
                        // may already have committed; propagating cancellation
                        // ends the pass without publishing false failures.
                        throw e
                    } catch (e: Throwable) {
                        // The item row was not written: the just-stored file
                        // would become an orphan — delete it (same policy as
                        // the item editor).
                        graph.photoStore.deleteIfOwned(storedPath)
                        failures += ImportFailure(photo, ImportFailureKind.WRITE_FAILED)
                    }
                }
                _state.value = _state.value.copy(
                    phase = ImportPhase.Importing(
                        created = createdInPass,
                        total = batch.size,
                    ),
                )
            }
            val created = createdInPass
            _state.value = _state.value.copy(
                phase = ImportPhase.Editing,
                photos = failures.map { it.photo },
                lastFailures = failures,
                createdCount = _state.value.createdCount + created,
            )
            if (failures.isEmpty()) finish()
        }
    }

    /**
     * Hashes the stored photo files of the first [EXISTING_ITEM_CAP] items
     * (archived included — a duplicate of an archived item still deserves the
     * warning). One pass, tiny sampled decodes, off the main thread; the first
     * item name per hash wins so the dialog always names a real item.
     */
    private suspend fun computeExistingHashes(): Map<Long, String> = withContext(Dispatchers.IO) {
        val items = repos.wardrobe
            .observeItems(ItemFilter(includeArchived = true))
            .first()
            .take(EXISTING_ITEM_CAP)
        val hashes = LinkedHashMap<Long, String>()
        for (item in items) {
            val aggregate = repos.wardrobe.getItem(item.id) ?: continue
            for (photo in aggregate.photos) {
                val hash = PhotoHash.ofStoredPhoto(photo.path) ?: continue
                hashes.putIfAbsent(hash, item.name)
            }
        }
        hashes
    }

    private fun defaultName(uri: Uri): String =
        displayNameOf(uri).substringBeforeLast('.').trim().ifEmpty { FALLBACK_NAME }

    private fun newPhotoKey(): String = "photo_${keyCounter.incrementAndGet()}"

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    companion object {
        /**
         * Dedupe scope cap: at most the first 500 existing items get hashed per
         * screen open. Keeps the open cost bounded (one sampled decode per
         * photo) without a schema change; documented trade-off, not a bug.
         */
        const val EXISTING_ITEM_CAP: Int = 500

        /** Non-localized name fallback (file-name-like, never user prose). */
        const val FALLBACK_NAME: String = "IMG"
    }
}
