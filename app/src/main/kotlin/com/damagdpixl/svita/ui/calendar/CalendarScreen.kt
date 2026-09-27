package com.damagdpixl.svita.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Charcoal
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.StickerBadge
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.WearLogEntry
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The Calendar tab (P2 T6): Editorial month grid with planned/logged dots and
 * a day sheet — logged entries with edit/delete; future dates get
 * «Запланувати» (saved outfit or dress-me for that date's forecast); past
 * dates and today get manual «Додати носіння».
 */
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(SvitaGraph.get()) } },
    ),
) {
    val cells by viewModel.cells.collectAsState()
    val month by viewModel.month.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()

    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        java.util.Locale(configuration.locales[0].toLanguageTag())
    }

    // Item picker state: open for a new wear entry (entry = null) or for
    // editing an existing one. Resets when the sheet's date changes.
    val pickerEntry = remember { mutableStateOf<WearLogEntry?>(null) }
    val pickerOpen = remember { mutableStateOf(false) }
    val pickerInitial = remember { mutableStateOf(emptySet<Long>()) }

    val today = viewModel.todayValue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("screen_calendar"),
    ) {
        Text(
            text = stringResource(R.string.calendar_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        // Month navigation.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.shiftMonth(-1) }
                    .testTag("calendar_prev"),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = CalendarFormat.monthTitle(month.year, month.month, locale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .testTag("calendar_month_title"),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.shiftMonth(1) }
                    .testTag("calendar_next"),
                contentAlignment = Alignment.Center,
            ) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        // Weekday strip (Monday-first, localized narrow letters).
        Row(modifier = Modifier.padding(top = 8.dp)) {
            CalendarFormat.weekdayLetters(locale).forEach { letter ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // Day grid: whole weeks, in-month days prominent, activity dots below.
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isToday = cell.date == today,
                        onClick = { viewModel.select(cell.date) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calendar_day_${cell.date}"),
                    )
                }
            }
        }

        // Legend.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            MarkDot(mark = DayMark.LOGGED)
            Text(
                text = stringResource(R.string.calendar_legend_logged),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.testTag("calendar_legend_logged"),
            )
            MarkDot(mark = DayMark.PLANNED)
            Text(
                text = stringResource(R.string.calendar_legend_planned),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.testTag("calendar_legend_planned"),
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // ---- Day sheet -------------------------------------------------------
    val currentSheet = sheet
    if (currentSheet != null) {
        val itemsById = remember(items) { items.associateBy { it.id } }
        val scope = rememberCoroutineScope()
        var planningBusy by remember(currentSheet.date) { mutableStateOf(false) }
        var showOutfitList by remember(currentSheet.date) { mutableStateOf(false) }

        EditorialDialog(
            scrimTag = "day_sheet_scrim",
            onDismiss = {
                viewModel.select(null)
                viewModel.setMessage(null)
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("day_sheet"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = CalendarFormat.dayTitle(currentSheet.date, locale),
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream,
                        modifier = Modifier.testTag("day_sheet_title"),
                    )
                    if (currentSheet.date == today) {
                        Spacer(Modifier.size(8.dp))
                        StickerBadge(text = stringResource(R.string.calendar_today_label))
                    }
                }

                val messageRes = message
                if (messageRes != null) {
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = Marigold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("sheet_message"),
                    )
                }

                // Future date: planning UI.
                if (currentSheet.future) {
                    Text(
                        text = stringResource(R.string.calendar_plan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cream.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        PillButton(
                            text = stringResource(R.string.calendar_plan_outfit),
                            onClick = { showOutfitList = !showOutfitList },
                            modifier = Modifier.testTag("plan_pick_outfit"),
                        )
                        GreenCta(
                            text = stringResource(R.string.calendar_plan_dressme),
                            onClick = {
                                planningBusy = true
                                scope.launch {
                                    val look = viewModel.planDressMe(currentSheet.date)
                                    planningBusy = false
                                    if (look == null) {
                                        viewModel.setMessage(R.string.outfit_no_look)
                                    }
                                }
                            },
                            enabled = !planningBusy,
                            modifier = Modifier.testTag("plan_dressme"),
                        )
                    }
                    if (showOutfitList) {
                        if (currentSheet.savedOutfits.isEmpty()) {
                            Text(
                                text = stringResource(R.string.calendar_no_outfits),
                                style = MaterialTheme.typography.bodySmall,
                                color = Cream.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag("plan_no_outfits"),
                            )
                        } else {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                currentSheet.savedOutfits.forEach { outfit ->
                                    OutfitRow(
                                        outfit = outfit,
                                        onClick = {
                                            viewModel.planOutfit(currentSheet.date, outfit.id)
                                            showOutfitList = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Entries.
                Text(
                    text = if (currentSheet.entries.isEmpty()) {
                        stringResource(R.string.calendar_entry_none)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Cream.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("sheet_empty"),
                )
                currentSheet.entries.forEach { entry ->
                    SheetEntryRow(
                        entry = entry,
                        today = today,
                        namesOf = { ids ->
                            ids.map { itemsById[it]?.name ?: "#${it}" }
                        },
                        outfitName = entry.outfitId?.let { id ->
                            currentSheet.savedOutfits.firstOrNull { it.id == id }?.name
                        },
                        onEdit = {
                            pickerEntry.value = entry
                            pickerInitial.value = entry.itemIds.toSet()
                            pickerOpen.value = true
                        },
                        onDelete = { viewModel.deleteEntry(entry) },
                    )
                }

                // Today / past date: manual wear logging.
                if (!currentSheet.future) {
                    PillButton(
                        text = stringResource(R.string.calendar_add_wear),
                        onClick = {
                            pickerEntry.value = null
                            pickerInitial.value = emptySet()
                            pickerOpen.value = true
                        },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("sheet_add_wear"),
                    )
                }
            }
        }
    }

    // ---- Item picker -----------------------------------------------------
    if (pickerOpen.value) {
        sheet?.date?.let { sheetDate ->
            ItemPickerDialog(
                title = stringResource(R.string.calendar_pick_items),
                items = items,
                initial = pickerInitial.value,
                onApply = { picked ->
                    val entry = pickerEntry.value
                    if (entry == null) {
                        viewModel.logWear(sheetDate, picked.toList())
                    } else {
                        viewModel.updateEntryItems(entry, picked.toList())
                    }
                    pickerOpen.value = false
                },
                onDismiss = { pickerOpen.value = false },
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarCell,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayAlpha = if (cell.inMonth) 1f else 0.35f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = 4.dp)
            .alpha(dayAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isToday) Marigold.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                .border(
                    border = BorderStroke(
                        width = if (isToday) 1.5.dp else 0.dp,
                        color = Marigold,
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MarkDot(mark = cell.mark)
    }
}

@Composable
private fun MarkDot(mark: DayMark, modifier: Modifier = Modifier) {
    val color = when (mark) {
        DayMark.LOGGED -> Charcoal
        DayMark.PLANNED -> Marigold
        DayMark.NONE -> return
    }
    Box(
        modifier = modifier
            .padding(top = 2.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
            .testTag("calendar_dot_${mark.name.lowercase()}"),
    )
}

@Composable
private fun OutfitRow(outfit: Outfit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp)
            .testTag("plan_outfit_${outfit.id}"),
    ) {
        Text(
            text = outfit.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
        )
    }
}

@Composable
private fun SheetEntryRow(
    entry: WearLogEntry,
    today: kotlinx.datetime.LocalDate,
    namesOf: (List<Long>) -> List<String>,
    outfitName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag("sheet_entry_${entry.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = namesOf(entry.itemIds).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream,
                modifier = Modifier
                    .weight(1f)
                    .testTag("sheet_entry_items_${entry.id}"),
            )
            if (entry.isPlan(today)) {
                Spacer(Modifier.size(6.dp))
                StickerBadge(text = stringResource(R.string.calendar_entry_planned_tag))
            }
        }
        if (outfitName != null) {
            Text(
                text = stringResource(R.string.calendar_entry_outfit, outfitName),
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.6f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.action_edit),
                style = MaterialTheme.typography.labelMedium,
                color = Marigold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(vertical = 4.dp)
                    .testTag("sheet_edit_${entry.id}"),
            )
            Text(
                text = stringResource(R.string.action_delete),
                style = MaterialTheme.typography.labelMedium,
                color = Marigold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    )
                    .padding(vertical = 4.dp)
                    .testTag("sheet_delete_${entry.id}"),
            )
        }
    }
}

@Composable
private fun ItemPickerDialog(
    title: String,
    items: List<com.damagdpixl.svita.core.model.Item>,
    initial: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    EditorialDialog(
        scrimTag = "item_picker_scrim",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("item_picker"),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                modifier = Modifier.testTag("item_picker_title"),
            )
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    val selected = item.id in picked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                picked = if (selected) picked - item.id else picked + item.id
                            }
                            .padding(vertical = 6.dp)
                            .testTag("pick_item_${item.id}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (selected) Marigold else androidx.compose.ui.graphics.Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = Cream.copy(alpha = 0.7f),
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                GreenCta(
                    text = stringResource(R.string.action_apply),
                    onClick = { onApply(picked) },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.testTag("picker_apply"),
                )
                PillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.testTag("picker_cancel"),
                )
            }
        }
    }
}
