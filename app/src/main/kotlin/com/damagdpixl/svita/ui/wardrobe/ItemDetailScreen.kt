package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.StickerBadge
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.data.SvitaGraph

/**
 * Item detail: photo pager, full aggregate read-out (attributes, tags, notes,
 * price, seasons, sex), the wear counter from the batch wearCounts() read and
 * edit/archive/delete actions (delete is confirm-guarded).
 */
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ItemDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ItemDetailViewModel(SvitaGraph.get(), itemId) }
        },
    )
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val aggregate = state.aggregate
    val ukrainian = run {
        val locales = LocalConfiguration.current.locales
        !locales.isEmpty && locales[0].language == "uk"
    }
    // Resolved resource strings for enum read-outs (plain lambdas cannot call
    // composables, so the labels are pinned before any joinToString).
    val springLabel = stringResource(seasonLabelRes(Season.SPRING))
    val summerLabel = stringResource(seasonLabelRes(Season.SUMMER))
    val autumnLabel = stringResource(seasonLabelRes(Season.AUTUMN))
    val winterLabel = stringResource(seasonLabelRes(Season.WINTER))
    val maleLabel = stringResource(sexLabelRes(Sex.MALE))
    val femaleLabel = stringResource(sexLabelRes(Sex.FEMALE))
    val unisexLabel = stringResource(sexLabelRes(Sex.UNISEX))
    val noneLabel = stringResource(R.string.detail_none)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("detail_root"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = Modifier.testTag("detail_back"),
                )
                Spacer(modifier = Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.action_edit),
                    onClick = { onEdit(itemId) },
                    modifier = Modifier.testTag("detail_edit"),
                )
                if (aggregate != null) {
                    PillButton(
                        text = stringResource(
                            if (aggregate.item.archived) R.string.action_unarchive else R.string.action_archive,
                        ),
                        onClick = { viewModel.setArchived(!aggregate.item.archived) },
                        modifier = Modifier.testTag("detail_archive"),
                    )
                    PillButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("detail_delete"),
                    )
                }
            }

            if (aggregate == null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.detail_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                if (aggregate.item.archived) {
                    StickerBadge(text = stringResource(R.string.wardrobe_in_archive))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = aggregate.item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtypeCaption(aggregate, ukrainian),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(14.dp))
                DetailPhotoPager(aggregate = aggregate)

                Spacer(modifier = Modifier.height(14.dp))
                CharcoalPanel {
                    Text(
                        text = stringResource(R.string.detail_wear_count, state.wearCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream,
                        modifier = Modifier.testTag("detail_wear_count"),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                aggregate.item.price?.let { price ->
                    KeyValueRow(
                        label = stringResource(R.string.detail_price),
                        value = formatPrice(price),
                    )
                }
                aggregate.item.purchaseDate?.let { date ->
                    KeyValueRow(
                        label = stringResource(R.string.detail_purchase_date),
                        value = date.toString(),
                    )
                }
                KeyValueRow(
                    label = stringResource(R.string.editor_seasons),
                    value = aggregate.item.seasons
                        .joinToString { season ->
                            when (season) {
                                Season.SPRING -> springLabel
                                Season.SUMMER -> summerLabel
                                Season.AUTUMN -> autumnLabel
                                Season.WINTER -> winterLabel
                            }
                        }
                        .ifEmpty { noneLabel },
                )
                aggregate.item.sex?.let { sex ->
                    KeyValueRow(
                        label = stringResource(R.string.editor_sex),
                        value = when (sex) {
                            Sex.MALE -> maleLabel
                            Sex.FEMALE -> femaleLabel
                            Sex.UNISEX -> unisexLabel
                        },
                    )
                }

                if (aggregate.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.detail_tags),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Text(
                        text = aggregate.tags.joinToString { it.name },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                aggregate.item.notes?.let { notes ->
                    if (notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.editor_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                if (aggregate.attributes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.detail_attributes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    aggregate.attributes.forEach { entry ->
                        AttributeValueRow(
                            entry = entry,
                            definition = state.definitionsById[entry.definitionId],
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (confirmDelete && aggregate != null) {
            EditorialDialog(onDismiss = { confirmDelete = false }, scrimTag = "delete_scrim") {
                Text(
                    text = stringResource(R.string.detail_delete_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cream,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_delete_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { confirmDelete = false },
                        modifier = Modifier.testTag("delete_cancel"),
                    )
                    PillButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            confirmDelete = false
                            viewModel.delete()
                        },
                        modifier = Modifier.testTag("delete_confirm"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPhotoPager(aggregate: ItemAggregate) {
    if (aggregate.photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.detail_no_photo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { aggregate.photos.size })
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_photo_pager"),
    ) { page ->
        StoredPhoto(
            path = aggregate.photos[page].path,
            contentDescription = aggregate.item.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(16.dp)),
            targetLongSide = PHOTO_DECODE_LARGE,
            placeholder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_no_photo),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
    }
    if (aggregate.photos.size > 1) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.detail_photo_position,
                pagerState.currentPage + 1,
                aggregate.photos.size,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(130.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AttributeValueRow(entry: AttributeEntry, definition: AttributeDefinition?) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = definition?.key ?: "#${entry.definitionId}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(130.dp),
        )
        if (definition?.type == AttributeType.COLOR) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(parseHexColor(entry.value), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), CircleShape),
            )
            Spacer(modifier = Modifier.size(6.dp))
        }
        Text(
            text = attributeValueText(entry),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun attributeValueText(entry: AttributeEntry): String =
    if (entry.value.trimStart().startsWith("[")) {
        decodeMulti(entry.value).joinToString(", ").ifEmpty { "—" }
    } else {
        entry.value
    }

private fun subtypeCaption(aggregate: ItemAggregate, ukrainian: Boolean): String {
    fun local(nameEn: String, nameUk: String) = if (ukrainian) nameUk else nameEn
    val subtypeName = local(aggregate.subtype.nameEn, aggregate.subtype.nameUk)
    val category = aggregate.category
    return if (category != null) {
        "$subtypeName · ${local(category.nameEn, category.nameUk)}"
    } else {
        subtypeName
    }
}
