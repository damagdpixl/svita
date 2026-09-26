package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class OutfitRepositoryImpl(private val db: AppDatabase) : OutfitRepository {

    override fun observeOutfits(): Flow<List<Outfit>> =
        db.outfitsQueries.selectAllOutfits().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun getOutfit(id: Long): OutfitAggregate? = db.read {
        val outfit = db.outfitsQueries.selectOutfitById(id).executeAsOneOrNull()?.toDomain()
            ?: return@read null
        OutfitAggregate(
            outfit = outfit,
            entries = db.outfitsQueries.selectOutfitEntriesByOutfit(id).executeAsList().map { it.toDomain() },
        )
    }

    override suspend fun createOutfit(draft: OutfitDraft, entries: List<OutfitEntry>): Long = db.write {
        db.outfitsQueries.insertOutfit(draft.name, draft.rating?.toLong(), nowIso(), draft.note)
        val id = db.outfitsQueries.selectLastInsertedOutfitId().executeAsOne()
        entries.forEach { entry ->
            db.outfitsQueries.insertOutfitItem(id, entry.itemId, entry.slot.db)
        }
        id
    }

    override suspend fun updateOutfit(id: Long, draft: OutfitDraft, entries: List<OutfitEntry>): Unit = db.write {
        if (db.outfitsQueries.selectOutfitById(id).executeAsOneOrNull() == null) {
            throw NoSuchElementException("outfit $id does not exist")
        }
        db.outfitsQueries.updateOutfit(draft.name, draft.rating?.toLong(), draft.note, id)
        db.outfitsQueries.deleteOutfitItemsByOutfit(id)
        entries.forEach { entry ->
            db.outfitsQueries.insertOutfitItem(id, entry.itemId, entry.slot.db)
        }
    }

    override suspend fun deleteOutfit(id: Long): Unit = db.write {
        // outfit_items cascade; wear_log.outfit_id is SET NULLed by the FK.
        db.outfitsQueries.deleteOutfit(id)
    }
}

internal class PackingRepositoryImpl(private val db: AppDatabase) : PackingRepository {

    override fun observePackingLists(): Flow<List<PackingList>> =
        db.packingQueries.selectAllPackingLists().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun getPackingList(id: Long): PackingListAggregate? = db.read {
        val list = db.packingQueries.selectPackingListById(id).executeAsOneOrNull()?.toDomain()
            ?: return@read null
        PackingListAggregate(
            list = list,
            entries = db.packingQueries.selectPackingItemsByList(id).executeAsList().map { it.toDomain() },
        )
    }

    override suspend fun createPackingList(
        title: String,
        dateFrom: kotlinx.datetime.LocalDate?,
        dateTo: kotlinx.datetime.LocalDate?,
        itemIds: List<Long>,
    ): Long = db.write {
        db.packingQueries.insertPackingList(title, dateFrom?.toString(), dateTo?.toString(), nowIso())
        val id = db.packingQueries.selectLastInsertedPackingListId().executeAsOne()
        itemIds.distinct().forEach { itemId -> db.packingQueries.insertPackingItem(id, itemId, 0L) }
        id
    }

    override suspend fun updatePackingList(
        id: Long,
        title: String,
        dateFrom: kotlinx.datetime.LocalDate?,
        dateTo: kotlinx.datetime.LocalDate?,
    ): Unit = db.write {
        if (db.packingQueries.selectPackingListById(id).executeAsOneOrNull() == null) {
            throw NoSuchElementException("packing list $id does not exist")
        }
        db.packingQueries.updatePackingList(title, dateFrom?.toString(), dateTo?.toString(), id)
    }

    override suspend fun deletePackingList(id: Long): Unit = db.write {
        // packing_items cascade away with the list.
        db.packingQueries.deletePackingList(id)
    }

    override suspend fun addPackingItem(listId: Long, itemId: Long): Unit = db.write {
        val alreadyOnList = db.packingQueries.selectPackingItemsByList(listId).executeAsList()
            .any { it.item_id == itemId }
        if (!alreadyOnList) {
            db.packingQueries.insertPackingItem(listId, itemId, 0L)
        }
    }

    override suspend fun removePackingItem(listId: Long, itemId: Long): Unit = db.write {
        db.packingQueries.deletePackingItem(listId, itemId)
    }

    override suspend fun setPacked(listId: Long, itemId: Long, packed: Boolean): Unit = db.write {
        db.packingQueries.updatePacked(if (packed) 1L else 0L, listId, itemId)
    }
}
