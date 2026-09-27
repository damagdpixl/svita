package com.damagdpixl.svita.ui.packing

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Charcoal
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.CreamMenuBar
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.ManifestoTiles
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.monoUpper
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import com.damagdpixl.svita.ui.wardrobe.OutlinedInput
import kotlinx.datetime.LocalDate

/**
 * The Packing tab (P2 T7): the trip lists, the create wizard (title + date
 * range -> suggested items from the wear log union + manual adds from the
 * searchable wardrobe picker) and the manifesto empty state. Rows open the
 * trip checklist.
 */
@Composable
fun PackingScreen(
    onOpenTrip: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackingViewModel = viewModel(
        factory = viewModelFactory { initializer { PackingViewModel(SvitaGraph.get()) } },
    ),
) {
    val trips by viewModel.trips.collectAsState()
    val items by viewModel.items.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    var showWizard by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var extraSelection by remember { mutableStateOf(emptySet<Long>()) }
    var excluded by remember { mutableStateOf(emptySet<Long>()) }
    var range by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_packing"),
    ) {
        if (trips.isEmpty()) {
            ManifestoTiles(
                text = stringResource(R.string.manifesto_line),
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.packing_empty_intro),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .testTag("packing_empty_intro"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.packing_empty_hint),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                GreenCta(
                    text = stringResource(R.string.packing_new_trip),
                    onClick = { showWizard = true },
                    modifier = Modifier.testTag("packing_new_empty"),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.packing_headline),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 16.dp, bottom = 12.dp),
                    )
                    GreenCta(
                        text = stringResource(R.string.packing_new_trip),
                        onClick = { showWizard = true },
                        modifier = Modifier.testTag("packing_new"),
                    )
                }
                trips.forEach { trip ->
                    TripRow(trip = trip, onClick = { onOpenTrip(trip.id) })
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Wizard's date range drives the suggestion load; the union re-emits into
    // `suggestions` and the dialog renders whatever arrived.
    LaunchedEffect(range) {
        val (from, to) = range ?: return@LaunchedEffect
        viewModel.suggestionsFor(from, to)
    }

    if (showWizard) {
        TripWizardDialog(
            items = items,
            suggestions = suggestions,
            extra = extraSelection,
            excluded = excluded,
            onToggleSuggested = { id ->
                excluded = if (id in excluded) excluded - id else excluded + id
            },
            onToggleExtra = { id ->
                extraSelection = extraSelection - id
            },
            onRangeChange = { from, to ->
                // Empty end collapses the range to the single start day.
                range = from?.let { start -> start to (to ?: start) }
            },
            onPickExtra = { pickerOpen = true },
            onDismiss = {
                showWizard = false
                extraSelection = emptySet()
                excluded = emptySet()
                range = null
                viewModel.resetWizard()
            },
            onCreate = { title, from, to ->
                val selected = (suggestions.toSet() - excluded) + extraSelection
                viewModel.createTrip(title, from, to, selected)
                showWizard = false
                extraSelection = emptySet()
                excluded = emptySet()
                range = null
            },
        )
    }

    if (pickerOpen) {
        WardrobeItemPickerDialog(
            title = stringResource(R.string.packing_add_from_wardrobe),
            items = items,
            initial = extraSelection,
            onApply = { picked ->
                extraSelection = picked
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun TripRow(trip: PackingList, onClick: () -> Unit) {
    CreamMenuBar(
        label = trip.title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
            .testTag("packing_trip_${trip.id}"),
        trailing = {
            val dates = datesText(trip.dateFrom, trip.dateTo)
            if (dates != null) {
                Text(
                    text = dates,
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal.copy(alpha = 0.45f),
                )
            }
            Text(text = "›", style = MaterialTheme.typography.titleMedium)
        },
    )
}

@Composable
private fun datesText(from: LocalDate?, to: LocalDate?): String? = when {
    from != null && to != null -> stringResource(R.string.packing_dates, from.toString(), to.toString())
    from != null -> from.toString()
    else -> null
}

@Composable
private fun TripWizardDialog(
    items: List<Item>,
    suggestions: List<Long>,
    extra: Set<Long>,
    excluded: Set<Long>,
    onToggleSuggested: (Long) -> Unit,
    onToggleExtra: (Long) -> Unit,
    onRangeChange: (LocalDate?, LocalDate?) -> Unit,
    onPickExtra: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, LocalDate?, LocalDate?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }

    val fromDate = parseDate(fromText)
    val toDate = parseDate(toText)

    // Push the valid range up whenever it changes; empty end = single day.
    LaunchedEffect(fromDate, toDate) {
        if (fromDate != null && (toDate == null || toDate >= fromDate)) {
            onRangeChange(fromDate, toDate)
        }
    }

    val itemsById = remember(items) { items.associateBy { it.id } }

    EditorialDialog(scrimTag = "packing_wizard_scrim", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("packing_wizard"),
        ) {
            Text(
                text = stringResource(R.string.packing_wizard_title),
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                modifier = Modifier.testTag("packing_wizard_title"),
            )
            OutlinedInput(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.packing_trip_title),
                errorRes = if (title.isBlank()) R.string.editor_name_required else null,
                onDark = true,
                modifier = Modifier.padding(top = 8.dp),
                testTagValue = "packing_wizard_name",
            )
            OutlinedInput(
                value = fromText,
                onValueChange = { fromText = it },
                label = stringResource(R.string.packing_date_from),
                errorRes = if (fromText.isNotBlank() && fromDate == null) {
                    R.string.editor_date_invalid
                } else {
                    null
                },
                onDark = true,
                modifier = Modifier.padding(top = 4.dp),
                testTagValue = "packing_wizard_from",
            )
            OutlinedInput(
                value = toText,
                onValueChange = { toText = it },
                label = stringResource(R.string.packing_date_to),
                errorRes = if (toText.isNotBlank() && toDate == null) {
                    R.string.editor_date_invalid
                } else {
                    null
                },
                onDark = true,
                modifier = Modifier.padding(top = 4.dp),
                testTagValue = "packing_wizard_to",
            )

            Text(
                text = stringResource(R.string.packing_suggested_title).monoUpper(),
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .testTag("packing_suggested_title"),
            )
            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.packing_suggested_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cream.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("packing_suggested_empty"),
                )
            } else {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    suggestions.forEach { id ->
                        SuggestionRow(
                            name = itemsById[id]?.name ?: "#$id",
                            checked = id !in excluded,
                            tag = "packing_suggested_$id",
                            onToggle = { onToggleSuggested(id) },
                        )
                    }
                }
            }
            if (extra.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    extra.forEach { id ->
                        SuggestionRow(
                            name = itemsById[id]?.name ?: "#$id",
                            checked = true,
                            tag = "packing_extra_$id",
                            onToggle = { onToggleExtra(id) },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                PillButton(
                    text = stringResource(R.string.packing_add_from_wardrobe),
                    onClick = onPickExtra,
                    modifier = Modifier.testTag("packing_add_from_wardrobe"),
                )
                GreenCta(
                    text = stringResource(R.string.packing_create),
                    onClick = { onCreate(title, fromDate, toDate) },
                    enabled = title.isNotBlank() && fromDate != null,
                    modifier = Modifier.testTag("packing_create"),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(name: String, checked: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 6.dp)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Marigold else androidx.compose.ui.graphics.Color.Transparent)
                .border(width = 1.5.dp, color = Cream.copy(alpha = 0.7f), shape = CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
        )
    }
}

/** Strict ISO parse; blank yields null without an error flag. */
internal fun parseDate(text: String): LocalDate? =
    text.trim().takeIf { it.isNotEmpty() }?.let { raw ->
        runCatching { LocalDate.parse(raw) }.getOrNull()
    }
