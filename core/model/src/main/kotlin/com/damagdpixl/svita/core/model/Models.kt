package com.damagdpixl.svita.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** A wardrobe category (v1 table `categories`), maps garments to sections. */
data class Category(
    val id: Long,
    val parentId: Long?,
    val nameEn: String,
    val nameUk: String,
    val icon: String?,
    val sortOrder: Int,
    val isSystem: Boolean,
    val section: Section,
)

/** Functional garment kind (v1 table `subtypes`), e.g. key = "body.t-shirt". */
data class Subtype(
    val id: Long,
    val categoryId: Long,
    val key: String,
    val nameEn: String,
    val nameUk: String,
    val thermalClass: String,
)

/** A wardrobe item (v1 table `items`). */
data class Item(
    val id: Long,
    val subtypeId: Long,
    val name: String,
    val notes: String?,
    val price: Double?,
    val purchaseDate: LocalDate?,
    val seasons: Set<Season>,
    val sex: Sex?,
    val rating: Int?,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Definition of a custom attribute (v1 table `attribute_definitions`). */
data class AttributeDefinition(
    val id: Long,
    val categoryId: Long?,
    val key: String,
    val type: AttributeType,
    val config: String?,
    val sortOrder: Int,
)

/** A custom attribute value attached to an item (v1 table `attribute_values`). */
data class AttributeEntry(
    val itemId: Long,
    val definitionId: Long,
    val value: String,
)

/** A user tag (v1 table `tags`). */
data class Tag(
    val id: Long,
    val name: String,
)

/** A saved outfit (v1 table `outfits`). */
data class Outfit(
    val id: Long,
    val name: String,
    val rating: Int?,
    val createdAt: Instant,
    val note: String?,
)

/** One item placed in an outfit slot (v1 table `outfit_items`). */
data class OutfitEntry(
    val outfitId: Long,
    val itemId: Long,
    val slot: Section,
)

/** One wear event (v1 table `wear_log`). */
data class WearLogEntry(
    val id: Long,
    val date: LocalDate,
    val outfitId: Long?,
    val itemIds: List<Long>,
    val tempC: Double?,
    val note: String?,
)

/** A packing list (v1 table `packing_lists`). */
data class PackingList(
    val id: Long,
    val title: String,
    val dateFrom: LocalDate?,
    val dateTo: LocalDate?,
    val createdAt: Instant,
)
