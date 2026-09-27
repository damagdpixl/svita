package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.core.model.toBitmask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal fun nowIso(): String = kotlin.time.Clock.System.now().toString()

/** Runs a write block transactionally (all-or-nothing) off the caller thread. */
internal suspend fun <T> AppDatabase.write(block: () -> T): T =
    withContext(Dispatchers.IO) { transactionWithResult { block() } }

/** Runs consistent multi-statement reads off the caller thread. */
internal suspend fun <T> AppDatabase.read(block: () -> T): T =
    withContext(Dispatchers.IO) { transactionWithResult { block() } }

internal fun <T : Any> app.cash.sqldelight.Query<T>.observed(): Flow<List<T>> =
    asFlow().mapToList(Dispatchers.IO)

internal class WardrobeRepositoryImpl(private val db: AppDatabase) : WardrobeRepository {

    override suspend fun createItem(
        draft: ItemDraft,
        photos: List<String>,
        tagIds: Set<Long>,
        attributeValues: Map<Long, String>,
    ): Long = db.write {
        db.itemsQueries.insertItem(
            subtype_id = draft.subtypeId,
            name = draft.name,
            notes = draft.notes,
            price = draft.price,
            purchase_date = draft.purchaseDate?.toString(),
            season_flags = draft.seasons.toBitmask().toLong(),
            sex = draft.sex?.db,
            rating = draft.rating?.toLong(),
            archived = if (draft.archived) 1L else 0L,
            created_at = nowIso(),
            updated_at = nowIso(),
        )
        val id = db.itemsQueries.selectLastInsertedItemId().executeAsOne()
        replaceChildren(id, photos, tagIds, attributeValues)
        id
    }

    override suspend fun updateItem(
        id: Long,
        draft: ItemDraft,
        photos: List<String>?,
        tagIds: Set<Long>?,
        attributeValues: Map<Long, String>?,
    ): Unit = db.write {
        requireExisting(id)
        db.itemsQueries.updateItem(
            subtype_id = draft.subtypeId,
            name = draft.name,
            notes = draft.notes,
            price = draft.price,
            purchase_date = draft.purchaseDate?.toString(),
            season_flags = draft.seasons.toBitmask().toLong(),
            sex = draft.sex?.db,
            rating = draft.rating?.toLong(),
            archived = if (draft.archived) 1L else 0L,
            updated_at = nowIso(),
            id = id,
        )
        if (photos != null) {
            db.photosQueries.deletePhotosByItem(id)
            photos.forEachIndexed { index, path -> db.photosQueries.insertPhoto(id, path, index.toLong()) }
        }
        if (tagIds != null) {
            db.tagsQueries.deleteItemTagsByItem(id)
            tagIds.forEach { db.tagsQueries.insertItemTag(id, it) }
        }
        if (attributeValues != null) {
            db.attribute_valuesQueries.deleteAttributeValuesByItem(id)
            attributeValues.forEach { (definitionId, value) ->
                db.attribute_valuesQueries.insertAttributeValue(id, definitionId, value)
            }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Unit = db.write {
        requireExisting(id)
        db.itemsQueries.updateItemArchived(
            archived = if (archived) 1L else 0L,
            updated_at = nowIso(),
            id = id,
        )
    }

    override suspend fun deleteItem(id: Long): Unit = db.write {
        // Scrub the item's id out of every wear-log entry first: item_ids is a
        // JSON column invisible to FK cascades, so this is the only place the
        // invariant "no dangling item ids in wear_log" can be kept.
        db.wear_logQueries.selectAllWearLog().executeAsList()
            .filter { entry -> decodeItemIds(entry.item_ids).contains(id) }
            .forEach { entry ->
                val remaining = decodeItemIds(entry.item_ids).filterNot { it == id }
                db.wear_logQueries.updateWearLogItemIds(itemIds = encodeItemIds(remaining), id = entry.id)
            }
        // FK cascades: photos, attribute_values, item_tags, outfit_items, packing_items.
        db.itemsQueries.deleteItem(id)
    }

    override fun observeItems(filter: ItemFilter): Flow<List<com.damagdpixl.svita.core.model.Item>> {
        val tagIds = filter.tagIds.toList()
        val rows = db.itemsQueries.selectItemsFiltered(
            includeArchived = filter.includeArchived,
            subtypeId = filter.subtypeId,
            categoryId = filter.categoryId,
            section = filter.section?.db,
            seasonMask = if (filter.seasons.isEmpty()) null else filter.seasons.toBitmask().toLong(),
            sex = filter.sex?.db,
            hasTagFilter = if (tagIds.isEmpty()) 0L else 1L,
            tagCount = tagIds.size.toLong(),
            // Sentinel keeps IN () away from the parser when tags are inactive;
            // the hasTagFilter = 0 guard makes the subselect irrelevant then.
            tagIds = if (tagIds.isEmpty()) listOf(-1L) else tagIds,
            colorDefinitionId = filter.colorDefinitionId,
            // Stored hex is normalized to uppercase at write time; normalize the
            // filter side the same way so "#ffffff" finds "#FFFFFF" rows.
            colorHex = filter.colorHex?.trim()?.uppercase() ?: "",
        )
        val flow = rows.observed().map { list -> list.map { it.toDomain() } }
        val nameQuery = filter.nameQuery?.trim()
        return if (nameQuery.isNullOrEmpty()) {
            flow
        } else {
            // Unicode-aware case-insensitive search: SQLite LIKE folds ASCII only.
            flow.map { items -> items.filter { it.name.contains(nameQuery, ignoreCase = true) } }
        }
    }

    override suspend fun getItem(id: Long): ItemAggregate? = db.read {
        val row = db.itemsQueries.selectItemById(id).executeAsOneOrNull()?.toDomain() ?: return@read null
        val subtype = db.subtypesQueries.selectSubtypeById(row.subtypeId).executeAsOne().toDomain()
        val category = db.categoriesQueries.selectCategoryById(subtype.categoryId)
            .executeAsOneOrNull()?.toDomain()
        ItemAggregate(
            item = row,
            subtype = subtype,
            category = category,
            photos = db.photosQueries.selectPhotosByItem(id).executeAsList().map { it.toDomain() },
            tags = db.tagsQueries.selectTagsByItem(id).executeAsList().map { it.toDomain() },
            attributes = db.attribute_valuesQueries.selectAttributeValuesByItem(id).executeAsList().map { it.toDomain() },
        )
    }

    override fun observeTags(): Flow<List<Tag>> = db.tagsQueries.selectAllTags().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun createTag(name: String): Long = db.write {
        db.tagsQueries.insertTag(name)
        db.tagsQueries.selectLastInsertedTagId().executeAsOne()
    }

    override suspend fun tagByName(name: String): Tag? = withContext(Dispatchers.IO) {
        db.tagsQueries.selectTagByName(name).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun deleteTag(id: Long): Unit = db.write {
        db.tagsQueries.deleteTag(id)
    }

    override suspend fun renameTag(id: Long, newName: String): Unit = db.write {
        if (db.tagsQueries.selectTagById(id).executeAsOneOrNull() == null) {
            throw NoSuchElementException("tag $id does not exist")
        }
        // A duplicate newName raises the tags.name UNIQUE constraint here; the
        // item_tags links survive either way — the update is in place by id.
        db.tagsQueries.updateTagName(name = newName, id = id)
    }

    private fun replaceChildren(id: Long, photos: List<String>, tagIds: Set<Long>, attributeValues: Map<Long, String>) {
        photos.forEachIndexed { index, path -> db.photosQueries.insertPhoto(id, path, index.toLong()) }
        tagIds.forEach { db.tagsQueries.insertItemTag(id, it) }
        attributeValues.forEach { (definitionId, value) ->
            db.attribute_valuesQueries.insertAttributeValue(id, definitionId, value)
        }
    }

    private fun requireExisting(id: Long) {
        if (db.itemsQueries.selectItemById(id).executeAsOneOrNull() == null) {
            throw NoSuchElementException("item $id does not exist")
        }
    }
}
