package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.data.SvitaGraph

/**
 * Add/edit item form: photos (system Photo Picker + private storage), subtype
 * picker grouped by category, name (required), optional price (comma-tolerant)
 * and purchase date, season/sex chips, notes, create-on-type tags and custom
 * attributes — every value validated through AttributesRepository before save.
 */
@Composable
fun ItemEditorScreen(
    itemId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val viewModel: ItemEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ItemEditorViewModel(SvitaGraph.get(), localeTag, itemId) }
        },
    )
    val state by viewModel.state.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val tags by viewModel.tags.collectAsState()
    var showSubtypePicker by remember { mutableStateOf(false) }
    var showAttributeCreator by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val pickPhotos = rememberPhotoPicker(onPicked = viewModel::addPhotos, maxItems = 8)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("editor_root"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(
                    if (itemId == null) R.string.editor_title_new else R.string.editor_title_edit,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.editor_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.editor_photos))
            PhotoStrip(
                state = state,
                onPick = { pickPhotos() },
                onRemoveStored = viewModel::removeStoredPhoto,
                onRemovePending = viewModel::removePendingPhoto,
            )

            SectionLabel(stringResource(R.string.editor_subtype))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.groups
                        .flatMap { group -> group.subtypes }
                        .firstOrNull { it.id == state.subtypeId }
                        ?.let(viewModel::localizeSubtype)
                        ?: stringResource(R.string.editor_subtype_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.editor_subtype_pick),
                    onClick = { showSubtypePicker = true },
                    modifier = Modifier.testTag("editor_subtype_pick"),
                )
            }
            if (state.subtypeMissing) {
                Text(
                    text = stringResource(R.string.editor_subtype_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedInput(
                value = state.name,
                onValueChange = viewModel::setName,
                label = stringResource(R.string.editor_name),
                errorRes = if (state.nameError) R.string.editor_name_required else null,
                testTagValue = "editor_name",
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedInput(
                value = state.priceText,
                onValueChange = viewModel::setPrice,
                label = stringResource(R.string.editor_price),
                numberKeyboard = true,
                errorRes = if (state.priceError) R.string.editor_price_invalid else null,
                testTagValue = "editor_price",
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedInput(
                value = state.dateText,
                onValueChange = viewModel::setPurchaseDate,
                label = stringResource(R.string.editor_purchase_date),
                errorRes = if (state.dateError) R.string.editor_date_invalid else null,
                testTagValue = "editor_date",
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.editor_seasons))
            ChipFlowRow {
                com.damagdpixl.svita.core.model.Season.entries.forEach { season ->
                    EditorialChip(
                        text = stringResource(seasonLabelRes(season)),
                        selected = season in state.seasons,
                        onClick = { viewModel.toggleSeason(season) },
                        modifier = Modifier.testTag("editor_season_${season.name}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.editor_sex))
            ChipFlowRow {
                com.damagdpixl.svita.core.model.Sex.entries.forEach { sex ->
                    EditorialChip(
                        text = stringResource(sexLabelRes(sex)),
                        selected = state.sex == sex,
                        onClick = { viewModel.setSex(sex) },
                        modifier = Modifier.testTag("editor_sex_${sex.name}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedInput(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = stringResource(R.string.editor_notes),
                singleLine = false,
                testTagValue = "editor_notes",
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.editor_tags))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = state.tagInput,
                        onValueChange = viewModel::setTagInput,
                        label = stringResource(R.string.editor_tags_hint),
                        singleLine = true,
                        testTagValue = "editor_tags_input",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                PillButton(
                    text = stringResource(R.string.action_add),
                    onClick = viewModel::commitTagInput,
                    modifier = Modifier.testTag("editor_tag_commit"),
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                ChipFlowRow {
                    tags.forEach { tag ->
                        EditorialChip(
                            text = tag.name,
                            selected = tag.id in state.selectedTagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            modifier = Modifier.testTag("editor_tag_${tag.name}"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.editor_attributes))
            if (state.attributeInputs.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_attributes_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                state.attributeInputs.forEach { input ->
                    AttributeField(
                        input = input,
                        errorRes = state.attributeErrors[input.definition.id],
                        palette = palette,
                        onValueChange = { value ->
                            viewModel.setAttributeValue(input.definition.id, value)
                        },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            PillButton(
                text = stringResource(R.string.editor_add_attribute),
                onClick = { showAttributeCreator = true },
                modifier = Modifier.testTag("editor_add_attribute"),
            )

            Spacer(modifier = Modifier.height(20.dp))
            if (state.writeFailed) {
                Text(
                    text = stringResource(R.string.save_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("editor_save_failed"),
                )
            }
            GreenCta(
                text = stringResource(R.string.action_save),
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_save"),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSubtypePicker) {
        SubtypePickerDialog(
            groups = state.groups,
            selectedId = state.subtypeId,
            localize = viewModel::localizeSubtype,
            onSelect = {
                viewModel.selectSubtype(it)
                showSubtypePicker = false
            },
            onDismiss = { showSubtypePicker = false },
        )
    }
    if (showAttributeCreator) {
        AttributeCreatorDialog(
            onCreate = { name, type, options, min, max ->
                viewModel.addAttributeDefinition(name, type, options, min, max)
                showAttributeCreator = false
            },
            onDismiss = { showAttributeCreator = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun PhotoStrip(
    state: ItemEditorState,
    onPick: () -> Unit,
    onRemoveStored: (String) -> Unit,
    onRemovePending: (Uri) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.storedPhotos.forEach { path ->
            PhotoThumb(
                path = path,
                onRemove = { onRemoveStored(path) },
            )
        }
        state.pendingUris.forEach { uri ->
            PhotoThumb(
                path = null,
                uri = uri,
                onRemove = { onRemovePending(uri) },
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPick)
                .testTag("editor_photo_add"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PhotoThumb(
    path: String?,
    onRemove: () -> Unit,
    uri: Uri? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StoredPhoto(
            path = path,
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
            targetLongSide = PHOTO_DECODE_THUMB,
            placeholder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.editor_photo_pending),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .testTag(if (uri != null) "photo_remove_pending" else "photo_remove")
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
    }
}

@Composable
private fun AttributeCreatorDialog(
    onCreate: (String, AttributeType, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AttributeType.TEXT) }
    var options by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }

    EditorialDialog(onDismiss = onDismiss, scrimTag = "attribute_scrim") {
        Text(
            text = stringResource(R.string.editor_add_attribute),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedInput(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.editor_attribute_name),
            testTagValue = "attribute_name",
            onDark = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.editor_attribute_type),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipFlowRow {
            AttributeType.entries.forEach { candidate ->
                EditorialChip(
                    text = stringResource(attributeTypeLabelRes(candidate)),
                    selected = type == candidate,
                    onClick = { type = candidate },
                    modifier = Modifier.testTag("attribute_type_${candidate.name}"),
                    onDark = true,
                )
            }
        }
        if (type == AttributeType.ENUM || type == AttributeType.MULTI) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedInput(
                value = options,
                onValueChange = { options = it },
                label = stringResource(R.string.editor_attribute_options),
                testTagValue = "attribute_options",
                onDark = true,
            )
        }
        if (type == AttributeType.NUMBER) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = min,
                        onValueChange = { min = it },
                        label = stringResource(R.string.editor_attribute_min),
                        numberKeyboard = true,
                        testTagValue = "attribute_min",
                        onDark = true,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = max,
                        onValueChange = { max = it },
                        label = stringResource(R.string.editor_attribute_max),
                        numberKeyboard = true,
                        testTagValue = "attribute_max",
                        onDark = true,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("attribute_cancel"),
            )
            GreenCta(
                text = stringResource(R.string.action_add),
                onClick = { onCreate(name, type, options, min, max) },
                modifier = Modifier.testTag("attribute_create"),
            )
        }
    }
}
