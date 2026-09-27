package com.damagdpixl.svita.ui.packing

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog

/**
 * Trip checklist (P2 T7): one `packed` toggle per item, manual adds from the
 * searchable wardrobe picker, per-item removal and list deletion (with a
 * confirm dialog).
 */
@Composable
fun TripDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripDetailViewModel = viewModel(
        key = "trip_$listId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(SvitaGraph.get(), listId) }
        },
    ),
) {
    val trip by viewModel.trip.collectAsState()
    val items by viewModel.items.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = trip
    val list = current?.list

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("screen_trip_detail"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("trip_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = list?.title.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("trip_title"),
            )
        }

        if (current != null && list != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.packing_progress,
                        current.packedCount,
                        current.totalCount,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 10.dp)
                        .testTag("trip_progress"),
                )

                CharcoalPanel(modifier = Modifier.testTag("trip_checklist")) {
                    if (current.rows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.packing_suggested_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("trip_empty"),
                        )
                    } else {
                        current.rows.forEach { row ->
                            ChecklistRowView(
                                name = row.item?.name ?: "#${row.entry.itemId}",
                                packed = row.entry.packed,
                                tag = "trip_item_${row.entry.itemId}",
                                onToggle = {
                                    viewModel.setPacked(row.entry.itemId, !row.entry.packed)
                                },
                                onRemove = { viewModel.removeItem(row.entry.itemId) },
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    PillButton(
                        text = stringResource(R.string.packing_add_items),
                        onClick = { pickerOpen = true },
                        modifier = Modifier.testTag("trip_add_items"),
                    )
                    PillButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("trip_delete"),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (pickerOpen) {
        WardrobeItemPickerDialog(
            title = stringResource(R.string.packing_add_items),
            items = items,
            initial = emptySet(),
            onApply = { picked ->
                viewModel.addItems(picked)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    if (confirmDelete && list != null) {
        EditorialDialog(scrimTag = "trip_delete_scrim", onDismiss = { confirmDelete = false }) {
            Text(
                text = stringResource(R.string.packing_delete_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                modifier = Modifier.testTag("trip_delete_title"),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.packing_delete_confirm_text, list.title),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GreenCta(
                    text = stringResource(R.string.action_delete),
                    onClick = { viewModel.deleteList(onDeleted = onBack) },
                    modifier = Modifier.testTag("trip_delete_confirm"),
                )
                PillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag("trip_delete_cancel"),
                )
            }
        }
    }
}

@Composable
private fun ChecklistRowView(
    name: String,
    packed: Boolean,
    tag: String,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (packed) Marigold else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .testTag("${tag}_toggle"),
            contentAlignment = Alignment.Center,
        ) {
            if (packed) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.damagdpixl.svita.core.designsystem.Charcoal,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (packed) Cream.copy(alpha = 0.45f) else Cream,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelLarge,
            color = Marigold,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                )
                .padding(6.dp)
                .testTag("${tag}_remove"),
        )
    }
}
