package com.damagdpixl.svita.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.engine.EngineGarment
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.WearLogEntry
import com.damagdpixl.svita.core.weather.targetTempC
import com.damagdpixl.svita.data.OutfitPrefs
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.outfits.DressMeLook
import com.damagdpixl.svita.ui.outfits.EngineGarmentMapper
import com.damagdpixl.svita.ui.outfits.todayIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Behind the Calendar tab (P2 T6): the month grid with planned/logged dots and
 * the day sheet (logged entries with edit/delete; «Запланувати» on future
 * dates via a saved outfit or a dress-me run for that date's forecast).
 *
 * Plan contract: a planned outfit is a wear_log entry with `note='plan'`;
 * together with `date >= today` it renders as [DayMark.PLANNED] (see
 * [classifyDay]). Past-date plans read as history. Editing an entry = delete +
 * re-add with the new item set, preserving date/outfit/temp/note — the
 * repository has no in-place update by design (v1 schema).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val graph: SvitaGraph.Graph,
    private val today: () -> LocalDate = { todayIn() },
) : ViewModel() {

    private val repos = graph.repos

    /** Today as the model pins it — the single date source for grid badges,
     * the future/past split and plan classification. */
    val todayValue: LocalDate get() = today()

    /** Rendered month (the grid always shows whole weeks around it). */
    val month = MutableStateFlow(YearMonth.of(today()))

    /** The day whose sheet is open, or null. */
    val selected = MutableStateFlow<LocalDate?>(null)

    /** Last action feedback for the sheet (null = silent). */
    val message = MutableStateFlow<Int?>(null)

    /** Saved outfits for the «Запланувати → збережений образ» picker. */
    val outfits: StateFlow<List<Outfit>> = repos.outfits.observeOutfits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active wardrobe items for the manual «Додати носіння» picker. */
    val items: StateFlow<List<Item>> = repos.wardrobe
        .observeItems(ItemFilter())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val monthEntries: StateFlow<Map<LocalDate, List<WearLogEntry>>> = month
        .flatMapLatest { m ->
            // Query the RENDERED grid range (whole weeks, leading/trailing
            // padding included) so out-month cells get honest dots too —
            // matching the CalendarMonth.markCells contract.
            val grid = CalendarMonth.cells(m.year, m.month)
            repos.wearLog.observeByRange(grid.first().date, grid.last().date)
        }.map { entries -> entries.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val cells: StateFlow<List<CalendarCell>> = combine(month, monthEntries) { m, entries ->
        CalendarMonth.markCells(CalendarMonth.cells(m.year, m.month), entries, today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedEntries: StateFlow<List<WearLogEntry>> = selected
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList()) else repos.wearLog.observeByDate(date)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything the day sheet renders, keyed off [selected]. */
    val sheet: StateFlow<DaySheetState?> = combine(
        selected,
        selectedEntries,
        outfits,
    ) { date, entries, savedOutfits ->
        date?.let { DaySheetState(it, entries, it > today(), savedOutfits) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate?) {
        selected.value = date
        message.value = null
    }

    fun shiftMonth(direction: Int) {
        month.value = month.value.plusMonths(direction)
    }

    fun setMessage(res: Int?) {
        message.value = res
    }

    /** Manual «Додати носіння» on today or a past date. */
    fun logWear(date: LocalDate, itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            repos.wearLog.addEntry(date = date, outfitId = null, itemIds = itemIds)
            selected.value = date
        }
    }

    /** «Запланувати → збережений образ» on a future date. */
    fun planOutfit(date: LocalDate, outfitId: Long) {
        viewModelScope.launch {
            val aggregate = repos.outfits.getOutfit(outfitId) ?: return@launch
            repos.wearLog.addEntry(
                date = date,
                outfitId = outfitId,
                itemIds = aggregate.entries.map { it.itemId },
                note = OutfitPrefs.PLAN_NOTE,
            )
            selected.value = date
        }
    }

    /**
     * «Запланувати → зібрати образ»: runs dress-me for the DATE'S forecast
     * temperature (comfort slider value from settings) and saves the top look
     * as a planned entry.
     */
    suspend fun planDressMe(date: LocalDate): DressMeLook? = withContext(Dispatchers.IO) {
        val comfort = repos.settings.getDouble(OutfitPrefs.COMFORT, 0.0)
        val lat = OutfitPrefs.DRESS_ME_LAT
        val lon = OutfitPrefs.DRESS_ME_LON
        val weather = graph.weather.forDate(lat, lon, date)
        val offline = graph.weatherCache.get(lat, lon, date) == null
        val target = targetTempC(weather, comfort)

        val aggregates = repos.wardrobe.observeItems(ItemFilter()).first()
            .mapNotNull { repos.wardrobe.getItem(it.id) }
        val colorDefinitionIds = repos.attributes.definitions()
            .filter { it.type == AttributeType.COLOR }
            .map { it.id }
            .toSet()
        val mapper = EngineGarmentMapper(repos.taxonomy.observeColors().first())
        val styleKeys = repos.taxonomy.observeStyleTags().first().map { it.key }.toSet()
        val garments = aggregates.map { it.toEngineGarment(mapper, colorDefinitionIds, styleKeys) }

        val look = graph.engine.looks(garments, target).looks.firstOrNull() ?: return@withContext null
        val byId = aggregates.associateBy { it.item.id }
        val result = DressMeLook(
            lookId = look.id,
            items = look.garmentIds.mapNotNull { byId[it] },
            styles = look.styles,
            targetTempC = target,
            weatherMinC = weather.tempMinC,
            weatherMaxC = weather.tempMaxC,
            condition = weather.condition,
            offline = offline,
        )
        repos.wearLog.addEntry(
            date = date,
            outfitId = null,
            itemIds = result.items.map { it.item.id },
            tempC = target,
            note = OutfitPrefs.PLAN_NOTE,
        )
        selected.value = date
        result
    }

    /** Delete any day-sheet entry. */
    fun deleteEntry(entry: WearLogEntry) {
        viewModelScope.launch { repos.wearLog.deleteEntry(entry.id) }
    }

    /**
     * Edit = delete + re-add with the new item set; date/outfit/temp/note are
     * preserved (a plan stays a plan, wear stays wear).
     */
    fun updateEntryItems(entry: WearLogEntry, newItemIds: List<Long>) {
        if (newItemIds.isEmpty()) return
        viewModelScope.launch {
            repos.wearLog.deleteEntry(entry.id)
            repos.wearLog.addEntry(
                date = entry.date,
                outfitId = entry.outfitId,
                itemIds = newItemIds,
                tempC = entry.tempC,
                note = entry.note,
            )
        }
    }

    private fun com.damagdpixl.svita.core.data.ItemAggregate.toEngineGarment(
        mapper: EngineGarmentMapper,
        colorDefinitionIds: Set<Long>,
        styleKeys: Set<String>,
    ): EngineGarment = mapper.garment(this, colorDefinitionIds, styleKeys)

    /** Year + month pair of the rendered grid (kotlinx has no YearMonth here). */
    data class YearMonth(val year: Int, val month: Month) {

        val firstDay: LocalDate get() = LocalDate(year, month, 1)

        val lastDay: LocalDate
            get() = firstDay.plus(
                DatePeriod(days = java.time.YearMonth.of(year, month.ordinal + 1).lengthOfMonth() - 1),
            )

        fun plusMonths(delta: Int): YearMonth {
            val total = year * 12 + month.ordinal + delta
            return YearMonth(total / 12, Month.entries[total.mod(12)])
        }

        companion object {
            fun of(date: LocalDate): YearMonth = YearMonth(date.year, date.month)
        }
    }

    /** Everything the day sheet needs. [future] = «Запланувати» mode. */
    data class DaySheetState(
        val date: LocalDate,
        val entries: List<WearLogEntry>,
        /** True when the date is strictly after today (planning UI). */
        val future: Boolean,
        val savedOutfits: List<Outfit>,
    )
}
