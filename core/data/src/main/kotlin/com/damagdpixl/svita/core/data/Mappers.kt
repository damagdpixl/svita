package com.damagdpixl.svita.core.data

import app.svita.core.data.db.App_settings
import app.svita.core.data.db.Attribute_definitions
import app.svita.core.data.db.Attribute_values
import app.svita.core.data.db.Categories
import app.svita.core.data.db.Colors
import app.svita.core.data.db.Item_tags
import app.svita.core.data.db.Items
import app.svita.core.data.db.Outfit_items
import app.svita.core.data.db.Outfits
import app.svita.core.data.db.Packing_items
import app.svita.core.data.db.Packing_lists
import app.svita.core.data.db.Photos
import app.svita.core.data.db.Style_tags
import app.svita.core.data.db.Subtypes
import app.svita.core.data.db.Tags
import app.svita.core.data.db.Wear_log
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Photo
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.core.model.WearLogEntry
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// DB rows -> domain models. Enum/text encodings are trusted internal invariants:
// an unknown value is a bug, so mappers fail fast instead of smuggling nulls.

internal fun unknownEncoding(value: String, row: String): Nothing =
    error("Unknown encoding '$value' in $row")

fun Categories.toDomain(): Category = Category(
    id = id,
    parentId = parent_id,
    nameEn = name_en,
    nameUk = name_uk,
    icon = icon,
    sortOrder = sort_order.toInt(),
    isSystem = system_flag != 0L,
    section = Section.fromDb(section) ?: unknownEncoding(section, "categories.id=$id"),
)

fun Subtypes.toDomain(): Subtype = Subtype(
    id = id,
    categoryId = category_id,
    key = key,
    nameEn = name_en,
    nameUk = name_uk,
    thermalClass = thermal_class,
)

fun Items.toDomain(): Item = Item(
    id = id,
    subtypeId = subtype_id,
    name = name,
    notes = notes,
    price = price,
    purchaseDate = purchase_date?.let(LocalDate::parse),
    seasons = Season.fromBitmask(season_flags.toInt()),
    sex = sex?.let { Sex.fromDb(it) ?: unknownEncoding(it, "items.id=$id") },
    rating = rating?.toInt(),
    archived = archived != 0L,
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
)

fun Attribute_definitions.toDomain(): AttributeDefinition = AttributeDefinition(
    id = id,
    categoryId = category_id,
    key = key,
    type = AttributeType.fromDb(type) ?: unknownEncoding(type, "attribute_definitions.id=$id"),
    config = config,
    sortOrder = sort_order.toInt(),
)

fun Attribute_values.toDomain(): AttributeEntry = AttributeEntry(
    itemId = item_id,
    definitionId = definition_id,
    value = value_,
)

fun Tags.toDomain(): Tag = Tag(
    id = id,
    name = name,
)

fun Outfits.toDomain(): Outfit = Outfit(
    id = id,
    name = name,
    rating = rating?.toInt(),
    createdAt = Instant.parse(created_at),
    note = note,
)

fun Outfit_items.toDomain(): OutfitEntry = OutfitEntry(
    outfitId = outfit_id,
    itemId = item_id,
    slot = Section.fromDb(slot) ?: unknownEncoding(slot, "outfit_items"),
)

fun Wear_log.toDomain(): WearLogEntry = WearLogEntry(
    id = id,
    date = LocalDate.parse(date),
    outfitId = outfit_id,
    itemIds = decodeItemIds(item_ids),
    tempC = temp_c,
    note = note,
)

fun Packing_lists.toDomain(): PackingList = PackingList(
    id = id,
    title = title,
    dateFrom = date_from?.let(LocalDate::parse),
    dateTo = date_to?.let(LocalDate::parse),
    createdAt = Instant.parse(created_at),
)

fun Photos.toDomain(): Photo = Photo(
    id = id,
    itemId = item_id,
    path = path,
    position = position.toInt(),
)

fun Packing_items.toDomain(): PackingEntry = PackingEntry(
    listId = packing_list_id,
    itemId = item_id,
    packed = packed != 0L,
)

fun Colors.toDomain(): PaletteColor = PaletteColor(
    id = id,
    key = key,
    nameUk = name_uk,
    hex = hex,
)

fun Style_tags.toDomain(): StyleTag = StyleTag(
    id = id,
    key = key,
    nameEn = name_en,
    nameUk = name_uk,
    sortOrder = sort_order.toInt(),
)

// app_settings / item_tags rows are plain key-value or link rows without
// dedicated domain types; tests assert on the generated rows.
fun App_settings.toPair(): Pair<String, String> = key to value_

fun Packing_items.isPacked(): Boolean = packed != 0L

fun Item_tags.tagIdOf(): Long = tag_id

// item_ids is a JSON array of ids, e.g. [1,2,3]. Both ends are our code, so a
// minimal encoder/decoder keeps the module free of a JSON dependency.
internal fun encodeItemIds(itemIds: List<Long>): String =
    itemIds.joinToString(separator = ",", prefix = "[", postfix = "]")

internal fun decodeItemIds(json: String): List<Long> =
    json.removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(String::toLong)
