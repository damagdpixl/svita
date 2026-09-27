package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.core.model.Photo
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import kotlinx.datetime.LocalDate

/**
 * Write model for [WardrobeRepository.createItem]/[WardrobeRepository.updateItem].
 * Child rows (photos, tags, attribute values) are passed separately so the
 * repository can write them in the same transaction as the item row.
 */
data class ItemDraft(
    val subtypeId: Long,
    val name: String,
    val notes: String? = null,
    val price: Double? = null,
    val purchaseDate: LocalDate? = null,
    val seasons: Set<Season> = Season.entries.toSet(),
    val sex: Sex? = null,
    val rating: Int? = null,
    val archived: Boolean = false,
)

/**
 * Filter for [WardrobeRepository.observeItems]. Semantics:
 * - every unset field is an inactive constraint (all-null filter = whole wardrobe);
 * - [seasons]: intersect semantics — an item matches when it is suitable for at
 *   least one of the requested seasons; empty set = no constraint;
 * - [tagIds]: conjunctive semantics — an item must carry ALL of the given tags;
 * - [sex]: strict equality (a FEMALE filter does not pull in UNISEX items);
 * - [colorDefinitionId] + [colorHex] act as a pair: items whose attribute value
 *   for that definition equals the (normalized `#RRGGBB`) hex;
 * - [nameQuery]: Unicode-aware case-insensitive substring applied in Kotlin
 *   (SQLite LIKE folds ASCII only); structural filters stay in SQL;
 * - archived items are hidden unless [includeArchived].
 */
data class ItemFilter(
    val includeArchived: Boolean = false,
    val subtypeId: Long? = null,
    val categoryId: Long? = null,
    val section: Section? = null,
    val seasons: Set<Season> = emptySet(),
    val sex: Sex? = null,
    val tagIds: Set<Long> = emptySet(),
    val colorDefinitionId: Long? = null,
    val colorHex: String? = null,
    val nameQuery: String? = null,
)

/** Full read model of an item: the row plus every child table. */
data class ItemAggregate(
    val item: Item,
    val subtype: Subtype,
    val category: Category?,
    val photos: List<Photo>,
    val tags: List<Tag>,
    val attributes: List<AttributeEntry>,
)

/** Write model for [OutfitRepository.createOutfit]/[OutfitRepository.updateOutfit]. */
data class OutfitDraft(
    val name: String,
    val rating: Int? = null,
    val note: String? = null,
)

/** Full read model of an outfit with its item placements. */
data class OutfitAggregate(
    val outfit: Outfit,
    val entries: List<OutfitEntry>,
)

/** Full read model of a packing list with its items. */
data class PackingListAggregate(
    val list: PackingList,
    val entries: List<PackingEntry>,
)

/** How many times an item was worn (see [WearLogRepository.wearCounts]). */
data class ItemWearCount(
    val itemId: Long,
    val wearCount: Long,
)

/** The most recent wear date of an item (see [WearLogRepository.lastWornDates]). */
data class ItemLastWorn(
    val itemId: Long,
    val lastWorn: LocalDate,
)

/**
 * Why a raw attribute value was rejected. Deliberately string-free: Ukrainian
 * messages are a UI-layer concern (P2), the repository speaks in kinds only.
 */
enum class ValueErrorKind {
    /** TEXT: empty (or whitespace-only) value. */
    EMPTY_TEXT,

    /** TEXT: longer than [TEXT_ATTRIBUTE_MAX_LENGTH]. */
    TEXT_TOO_LONG,

    /** NUMBER: not parseable as a finite decimal number. */
    NOT_A_NUMBER,

    /** NUMBER: parsed but outside the configured min/max bounds. */
    OUT_OF_RANGE,

    /** ENUM/MULTI: the definition has no config JSON at all. */
    MISSING_CONFIG,

    /** The definition's config JSON is present but not parseable/shaped. */
    MALFORMED_CONFIG,

    /** ENUM/MULTI: a value is not one of the configured options. */
    NOT_AN_OPTION,

    /** MULTI: the raw value is not a JSON array of strings. */
    MALFORMED_MULTI,

    /** COLOR: not a `#RRGGBB` hex string. */
    MALFORMED_COLOR,
}

/** Result of validating a raw attribute value; [Valid] carries the stored form. */
sealed interface ValueValidation {
    data class Valid(val normalized: String) : ValueValidation
    data class Invalid(val kind: ValueErrorKind) : ValueValidation
}

/** Result of [AttributesRepository.setValue]. */
sealed interface ValueWriteResult {
    data object Saved : ValueWriteResult

    /** The definition id does not exist. */
    data object UnknownDefinition : ValueWriteResult
    data class Rejected(val kind: ValueErrorKind) : ValueWriteResult
}

/** One branch of the category tree (see [TaxonomyRepository.categoryTree]). */
data class CategoryNode(
    val category: Category,
    val children: List<CategoryNode>,
)

/** Why a category deletion was refused (see TaxonomyEditorRepository.deleteCategory). */
enum class CategoryDeleteBlock {
    /** Items still reference the category's subtypes. */
    HAS_ITEMS,

    /** A system category still carries its seeded subtypes (engine data). */
    HAS_SUBTYPES,
}

/** Result of TaxonomyEditorRepository.deleteCategory. */
sealed interface CategoryDeleteResult {
    data object Deleted : CategoryDeleteResult
    data class Blocked(val reason: CategoryDeleteBlock) : CategoryDeleteResult
}

/** Result of TaxonomyEditorRepository.resetToDefaults. */
sealed interface ResetTaxonomyResult {
    data object Reset : ResetTaxonomyResult

    /** Some custom category still holds items — nothing was touched. */
    data object BlockedByItems : ResetTaxonomyResult
}
