package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The typed persistence API the P2 UI consumes.
 *
 * The interfaces live in `:core:data` (not `:core:model`) on purpose: they need
 * kotlinx-coroutines `Flow` and data-shaped types (filters, drafts, validation
 * outcomes) that have no meaning in the pure domain module. `:core:model` stays
 * a zero-dependency Kotlin module; the app wires implementations through
 * [SvitaRepositories], so screens still depend on these abstractions only.
 *
 * Conventions:
 * - reads are cold [Flow]s that re-emit on any relevant DB change;
 * - writes are `suspend` and transactional; an SQLite error (e.g. a broken FK)
 *   propagates and rolls back the whole operation, leaving no partial rows;
 * - ids of missing rows fail fast with [NoSuchElementException] on updates,
 *   except where documented as no-ops.
 */
public interface WardrobeRepository {
    /**
     * Creates the item row plus its photos/tags/attribute values in ONE
     * transaction. Photo positions are the list order; attribute values are
     * written as given — validate them with [AttributesRepository.validate]
     * first (single validation path).
     */
    public suspend fun createItem(
        draft: ItemDraft,
        photos: List<String> = emptyList(),
        tagIds: Set<Long> = emptySet(),
        attributeValues: Map<Long, String> = emptyMap(),
    ): Long

    /**
     * Updates the item row; `null` children arguments leave that child table
     * untouched, non-null arguments REPLACE its whole content for this item.
     * Throws [NoSuchElementException] when the item does not exist.
     */
    public suspend fun updateItem(
        id: Long,
        draft: ItemDraft,
        photos: List<String>? = null,
        tagIds: Set<Long>? = null,
        attributeValues: Map<Long, String>? = null,
    )

    public suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * Deletes the item. FK cascades remove its photos, attribute values, tag
     * links, outfit placements and packing-list entries; wear-log JSON is
     * scrubbed of the deleted id in the same transaction (that column is
     * invisible to foreign keys).
     */
    public suspend fun deleteItem(id: Long)

    /** Flow-first filtered wardrobe list; see [ItemFilter] for semantics. */
    public fun observeItems(filter: ItemFilter = ItemFilter()): Flow<List<Item>>

    /** Full aggregate or null when the id is unknown. */
    public suspend fun getItem(id: Long): ItemAggregate?

    public fun observeTags(): Flow<List<Tag>>

    /** Creates a user tag; a duplicate name raises the UNIQUE constraint. */
    public suspend fun createTag(name: String): Long

    /** Resolves an existing tag by exact name, if any. */
    public suspend fun tagByName(name: String): Tag?

    /** Deletes the tag; its item links cascade away. */
    public suspend fun deleteTag(id: Long)

    /**
     * Renames the tag in place: `item_tags` rows reference the id, so every
     * item link survives. A duplicate [newName] raises the UNIQUE constraint
     * (surface it in the UI instead of crashing the caller's scope).
     * Throws [NoSuchElementException] when the tag does not exist.
     */
    public suspend fun renameTag(id: Long, newName: String)
}

/** Read-only access to the seeded taxonomy (categories, subtypes, colors, style tags). */
public interface TaxonomyRepository {
    public fun observeCategories(): Flow<List<Category>>
    public suspend fun categories(): List<Category>
    public suspend fun category(id: Long): Category?

    /** Full tree built from `parent_id`; orphaned parents surface as roots. */
    public suspend fun categoryTree(): List<CategoryNode>

    public suspend fun subtypesByCategory(categoryId: Long): List<Subtype>
    public suspend fun subtypesBySection(section: Section): List<Subtype>
    public suspend fun subtype(id: Long): Subtype?

    /** Lookup by the seeded functional key, e.g. `body.t-shirt`. */
    public suspend fun subtypeByKey(key: String): Subtype?

    public fun observeColors(): Flow<List<PaletteColor>>
    public suspend fun colorByKey(key: String): PaletteColor?

    public fun observeStyleTags(): Flow<List<StyleTag>>
    public suspend fun styleTagByKey(key: String): StyleTag?
}

/**
 * The write side of the taxonomy — the customization USP on top of the read-only
 * [TaxonomyRepository].
 *
 * Product rules (P2 T4):
 * - SYSTEM (seeded) categories are editable in display names and icon only;
 *   their section and sort order are engine/seed data and stay locked;
 * - a CUSTOM category is created with one auto subtype (`key = custom.<id>`,
 *   display names follow the category, thermal class `N` = engine-neutral) so
 *   it shows up in the subtype pickers like any seeded category;
 * - deleting any category is refused while items reference it; a SYSTEM
 *   category is additionally refused while its seeded subtypes remain (they
 *   carry engine thermal data — removing them would strand the restored row
 *   without a single subtype after a reset);
 * - `resetToDefaults` deletes every custom category (with its auto subtypes and
 *   attribute definitions scoped to it) and restores all system rows from
 *   [TaxonomyDefaults] — updating modified rows and re-inserting deleted ones.
 */
public interface TaxonomyEditorRepository {
    /**
     * Creates a custom category with one auto subtype. Sort order defaults to
     * the current maximum + 10. Returns the new category id.
     */
    public suspend fun createCategory(
        section: Section,
        nameEn: String,
        nameUk: String,
        icon: String?,
    ): Long

    /** Edits display names and icon; allowed for system and custom categories. */
    public suspend fun updateCategory(id: Long, nameEn: String, nameUk: String, icon: String?)

    /**
     * Full custom-category edit (names, icon, section, sort order). Renaming
     * also renames the auto subtype's display names. Throws
     * [NoSuchElementException] when the category does not exist and
     * [IllegalArgumentException] when it is a system category.
     */
    public suspend fun updateCustomCategory(
        id: Long,
        section: Section,
        nameEn: String,
        nameUk: String,
        icon: String?,
        sortOrder: Int,
    )

    /**
     * Moves a custom category by swapping its sort order with the adjacent row
     * in [direction]. Returns the category's new sort order. Only custom rows
     * move; a system neighbour stays put and the swap happens with the nearest
     * custom row beyond it instead (system rows keep their seed spacing, the
     * custom row may cross them).
     */
    public suspend fun moveCustomCategory(id: Long, direction: SortMove): Int

    /** categoryId -> item count (items joined through subtypes), categories without items absent. */
    public suspend fun itemCountsByCategory(): Map<Long, Long>

    /**
     * Deletes the category and everything hanging off it (subtypes, attribute
     * definitions scoped to it) in one transaction — unless blocked, see
     * [CategoryDeleteResult.Blocked].
     */
    public suspend fun deleteCategory(id: Long): CategoryDeleteResult

    /**
     * Restores the factory taxonomy: custom categories (and definitions scoped
     * to them) are deleted, every system row is reset to its seed values.
     * Refused as a whole while any custom category still holds items.
     */
    public suspend fun resetToDefaults(): ResetTaxonomyResult
}

/** Direction of a [TaxonomyEditorRepository.moveCustomCategory] step. */
public enum class SortMove { UP, DOWN }

/** CRUD for attribute definitions and validated read/write of their values. */
public interface AttributesRepository {
    /**
     * Definitions visible for a category context: `null` returns everything,
     * a category id returns the global definitions plus the ones scoped to it.
     */
    public fun observeDefinitions(categoryId: Long? = null): Flow<List<AttributeDefinition>>

    public suspend fun definitions(): List<AttributeDefinition>
    public suspend fun definition(id: Long): AttributeDefinition?

    /** Throws [NoSuchElementException] when the definition does not exist. */
    public suspend fun validate(definitionId: Long, rawValue: String): ValueValidation

    public suspend fun createDefinition(
        categoryId: Long?,
        key: String,
        type: AttributeType,
        config: String?,
        sortOrder: Int,
    ): Long

    /** Throws [NoSuchElementException] when the definition does not exist. */
    public suspend fun updateDefinition(
        id: Long,
        categoryId: Long?,
        key: String,
        type: AttributeType,
        config: String?,
        sortOrder: Int,
    )

    /** Deletes the definition; its stored values cascade away. */
    public suspend fun deleteDefinition(id: Long)

    /**
     * Validates and stores (or clears when [rawValue] is null) the value of one
     * attribute on one item. Invalid values are never written — the item's
     * previous value for that definition survives a [ValueWriteResult.Rejected].
     */
    public suspend fun setValue(itemId: Long, definitionId: Long, rawValue: String?): ValueWriteResult

    public suspend fun valuesForItem(itemId: Long): List<AttributeEntry>
}

public interface OutfitRepository {
    public fun observeOutfits(): Flow<List<Outfit>>

    /** Full aggregate or null when the id is unknown. */
    public suspend fun getOutfit(id: Long): OutfitAggregate?

    /**
     * Creates the outfit and its entries in one transaction. Item ids must be
     * unique within the list (the outfit_items PK is (outfit, item)).
     */
    public suspend fun createOutfit(draft: OutfitDraft, entries: List<OutfitEntry> = emptyList()): Long

    /** Replaces fields and the whole entry list atomically; throws if missing. */
    public suspend fun updateOutfit(id: Long, draft: OutfitDraft, entries: List<OutfitEntry>)

    /** Deleting an outfit SET NULLs wear_log.outfit_id and cascades its entries. */
    public suspend fun deleteOutfit(id: Long)
}

public interface WearLogRepository {
    /**
     * Adds a wear event. [itemIds] is de-duplicated preserving order; entries
     * reference items by id (JSON column, no FK) — see the scrubbing contract
     * on [WardrobeRepository.deleteItem].
     */
    public suspend fun addEntry(
        date: LocalDate,
        outfitId: Long?,
        itemIds: List<Long>,
        tempC: Double? = null,
        note: String? = null,
    ): Long

    public suspend fun deleteEntry(id: Long)

    public fun observeByDate(date: LocalDate): Flow<List<WearLogEntry>>

    /** Inclusive date range. */
    public fun observeByRange(from: LocalDate, to: LocalDate): Flow<List<WearLogEntry>>

    /** Wear counter per referenced item id, most-worn first. */
    public suspend fun wearCounts(): List<ItemWearCount>

    /** Most recent wear date per referenced item id, most-recent first. */
    public suspend fun lastWornDates(): List<ItemLastWorn>

    /** Most recent wear date of one item, or null when never worn. */
    public suspend fun lastWorn(itemId: Long): LocalDate?

    /**
     * Active (non-archived) items with no wear entry on/after
     * `referenceDate - [minDaysIdle]` days, re-emitted on changes.
     */
    public fun observeNotWornSince(
        referenceDate: LocalDate,
        minDaysIdle: Int = DEFAULT_IDLE_DAYS,
    ): Flow<List<Item>>

    public companion object {
        public const val DEFAULT_IDLE_DAYS: Int = 60
    }
}

public interface PackingRepository {
    public fun observePackingLists(): Flow<List<PackingList>>

    /** Full aggregate or null when the id is unknown. */
    public suspend fun getPackingList(id: Long): PackingListAggregate?

    /** Creates the list and inserts [itemIds] as unpacked entries in one transaction. */
    public suspend fun createPackingList(
        title: String,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
        itemIds: List<Long> = emptyList(),
    ): Long

    public suspend fun updatePackingList(id: Long, title: String, dateFrom: LocalDate?, dateTo: LocalDate?)
    public suspend fun deletePackingList(id: Long)

    /** Idempotent: an item already on the list is left untouched. */
    public suspend fun addPackingItem(listId: Long, itemId: Long)

    /** No-op when the item is not on the list. */
    public suspend fun removePackingItem(listId: Long, itemId: Long)

    public suspend fun setPacked(listId: Long, itemId: Long, packed: Boolean)
}

/** Typed key-value settings persisted in `app_settings`. */
public interface SettingsRepository {
    /** Emits null until the key exists, then tracks its value. */
    public fun observe(key: String): Flow<String?>

    public suspend fun getString(key: String): String?
    public suspend fun putString(key: String, value: String)
    public suspend fun remove(key: String)

    public suspend fun getBoolean(key: String, default: Boolean): Boolean
    public suspend fun putBoolean(key: String, value: Boolean)
    public suspend fun getInt(key: String, default: Int): Int
    public suspend fun putInt(key: String, value: Int)
    public suspend fun getLong(key: String, default: Long): Long
    public suspend fun putLong(key: String, value: Long)
    public suspend fun getDouble(key: String, default: Double): Double
    public suspend fun putDouble(key: String, value: Double)
}

/** The eight repositories wired over one [AppDatabase]; the P2 entry point. */
public class SvitaRepositories(db: AppDatabase) {
    public val wardrobe: WardrobeRepository = WardrobeRepositoryImpl(db)
    public val taxonomy: TaxonomyRepository = TaxonomyRepositoryImpl(db)
    public val taxonomyEditor: TaxonomyEditorRepository = TaxonomyEditorRepositoryImpl(db)
    public val attributes: AttributesRepository = AttributesRepositoryImpl(db)
    public val outfits: OutfitRepository = OutfitRepositoryImpl(db)
    public val wearLog: WearLogRepository = WearLogRepositoryImpl(db)
    public val packing: PackingRepository = PackingRepositoryImpl(db)
    public val settings: SettingsRepository = SettingsRepositoryImpl(db)
}
