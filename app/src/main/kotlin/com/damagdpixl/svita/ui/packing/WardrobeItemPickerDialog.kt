package com.damagdpixl.svita.ui.packing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import com.damagdpixl.svita.ui.wardrobe.OutlinedInput

/**
 * Searchable wardrobe picker shared by the packing wizard (manual adds) and
 * the trip checklist. Unicode-aware case-insensitive substring search over
 * item names, applied in Kotlin (SQLite LIKE folds ASCII only).
 *
 * Pre-checked [initial] ids survive search edits — the selection is id-based,
 * the visible list is only filtered.
 */
@Composable
fun WardrobeItemPickerDialog(
    title: String,
    items: List<Item>,
    initial: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(initial) }

    EditorialDialog(scrimTag = "picker_scrim", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().testTag("packing_picker")) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                modifier = Modifier.testTag("packing_picker_title"),
            )
            OutlinedInput(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.wardrobe_search_hint),
                singleLine = true,
                onDark = true,
                modifier = Modifier.padding(top = 8.dp),
                testTagValue = "packing_picker_search",
            )
            val visible = remember(query, items) { filterByName(items, query) }
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (visible.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_nothing_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cream.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .testTag("packing_picker_empty"),
                    )
                }
                visible.forEach { item ->
                    PickerRow(
                        item = item,
                        selected = item.id in picked,
                        onToggle = {
                            picked = if (item.id in picked) picked - item.id else picked + item.id
                        },
                    )
                }
            }
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                GreenCta(
                    text = stringResource(R.string.action_apply),
                    onClick = { onApply(picked) },
                    modifier = Modifier.testTag("packing_picker_apply"),
                )
                PillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.testTag("packing_picker_cancel"),
                )
            }
        }
    }
}

@Composable
private fun PickerRow(item: Item, selected: Boolean, onToggle: () -> Unit) {
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
            .testTag("picker_item_${item.id}"),
    ) {
        androidx.compose.foundation.layout.Box(
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

/** Unicode-aware case-insensitive name substring filter (empty query = all). */
internal fun filterByName(items: List<Item>, query: String): List<Item> {
    val q = query.trim()
    if (q.isEmpty()) return items
    return items.filter { it.name.contains(q, ignoreCase = true) }
}
