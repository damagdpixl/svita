package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal class WearLogRepositoryImpl(private val db: AppDatabase) : WearLogRepository {

    override suspend fun addEntry(
        date: LocalDate,
        outfitId: Long?,
        itemIds: List<Long>,
        tempC: Double?,
        note: String?,
    ): Long = db.write {
        db.wear_logQueries.insertWearLogEntry(
            date = date.toString(),
            outfit_id = outfitId,
            // De-duplicated to keep the wear counters honest: json-style ids
            // have no uniqueness enforcement, but a doubled id would inflate
            // per-item statistics.
            item_ids = encodeItemIds(itemIds.distinct()),
            temp_c = tempC,
            note = note,
        )
        db.wear_logQueries.selectLastInsertedWearLogId().executeAsOne()
    }

    override suspend fun deleteEntry(id: Long): Unit = db.write {
        db.wear_logQueries.deleteWearLogEntry(id)
    }

    override fun observeByDate(date: LocalDate): Flow<List<WearLogEntry>> =
        db.wear_logQueries.selectWearLogByDate(date.toString()).observed()
            .map { list -> list.map { it.toDomain() } }

    override fun observeByRange(from: LocalDate, to: LocalDate): Flow<List<WearLogEntry>> =
        db.wear_logQueries.selectWearLogByRange(from.toString(), to.toString()).observed()
            .map { list -> list.map { it.toDomain() } }

    override suspend fun wearCounts(): List<ItemWearCount> = withContext(Dispatchers.IO) {
        wearEventsByItem(db).map { (itemId, dates) -> ItemWearCount(itemId, dates.size.toLong()) }
            .sortedWith(compareByDescending<ItemWearCount> { it.wearCount }.thenBy { it.itemId })
    }

    override suspend fun lastWornDates(): List<ItemLastWorn> = withContext(Dispatchers.IO) {
        wearEventsByItem(db).mapNotNull { (itemId, dates) ->
            dates.maxOrNull()?.let { ItemLastWorn(itemId, it) }
        }.sortedWith(compareByDescending<ItemLastWorn> { it.lastWorn }.thenBy { it.itemId })
    }

    override suspend fun lastWorn(itemId: Long): LocalDate? = withContext(Dispatchers.IO) {
        db.wear_logQueries.selectAllWearLog().executeAsList()
            .asSequence()
            .map { it.toDomain() }
            .filter { it.isWear() }
            .filter { itemId in it.itemIds }
            .mapNotNull { it.date }
            .maxOrNull()
    }

    override fun observeNotWornSince(referenceDate: LocalDate, minDaysIdle: Int): Flow<List<Item>> {
        // ISO dates compare lexicographically, so a plain >= cutoff works.
        val cutoff = referenceDate.minus(DatePeriod(days = minDaysIdle))
        val wornRecently = db.wear_logQueries.selectAllWearLog().observed().map { rows ->
            rows.asSequence()
                .map { it.toDomain() }
                .filter { it.isWear() }
                .filter { it.date >= cutoff }
                .flatMap { it.itemIds }
                .toSet()
        }
        val activeItems = db.itemsQueries.selectActiveItems().observed().map { rows -> rows.map { it.toDomain() } }
        return combine(wornRecently, activeItems) { worn, items -> items.filterNot { it.id in worn } }
    }

    /**
     * A PLANNED entry ([PLAN_NOTE]) is never a wear event: it stays visible
     * through the date/range observations (the calendar) but is invisible to
     * every statistic — counters, last-worn and the not-worn query. The plan
     * marker is the shared [WearLogRepository.PLAN_NOTE] constant so the
     * repository layer and the UI layer cannot drift apart.
     */
    private fun WearLogEntry.isWear(): Boolean = note != WearLogRepository.PLAN_NOTE

    /**
     * One pass over wear_log: item id -> all wear dates mentioned for it.
     * (json_each()/table-valued functions are unavailable in the SQLite 3.18
     * dialect this project compiles against, so aggregation is done here; the
     * DB still answers with a single indexed scan.) Plan entries excluded.
     */
    private fun wearEventsByItem(db: AppDatabase): Map<Long, List<LocalDate>> {
        val byItem = mutableMapOf<Long, MutableList<LocalDate>>()
        db.wear_logQueries.selectAllWearLog().executeAsList().forEach { row ->
            val entry = row.toDomain()
            if (!entry.isWear()) return@forEach
            entry.itemIds.forEach { itemId ->
                byItem.getOrPut(itemId) { mutableListOf() }.add(entry.date)
            }
        }
        return byItem
    }
}
