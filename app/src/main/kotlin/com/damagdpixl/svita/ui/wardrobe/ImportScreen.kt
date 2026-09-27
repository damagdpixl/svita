package com.damagdpixl.svita.ui.wardrobe

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.PhotoHash
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bulk gallery import: one system Photo Picker multi-select, shared per-batch
 * fields (subtype, seasons, sex, style tags), per-photo name defaults and
 * optional subtype overrides, one sequential progress pass, pHash-lite
 * duplicate warnings («все одно додати») and a failure list with retry.
 */
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val resolver = LocalContext.current.contentResolver
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ImportViewModel(
                    graph = SvitaGraph.get(),
                    localeTag = localeTag,
                    displayNameOf = { uri -> queryDisplayName(resolver, uri) },
                    hashOf = { uri -> PhotoHash.ofPickedImage(resolver, uri) },
                )
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val tags by viewModel.tags.collectAsState()
    var showSharedPicker by remember { mutableStateOf(false) }
    var overridePickerKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.navigateBack) {
        if (state.navigateBack) onBack()
    }

    // null maxItems = the platform maximum (see rememberPhotoPicker) — the bulk
    // import is exactly the case the multi-select was built for.
    val pickPhotos = rememberPhotoPicker(onPicked = viewModel::addPhotos, maxItems = null)

    val importing = state.phase is ImportPhase.Importing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("import_root"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                enabled = !importing,
                modifier = Modifier.testTag("import_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when (val phase = state.phase) {
            is ImportPhase.Importing -> ImportingBody(phase)
            ImportPhase.Editing -> EditingBody(
                state = state,
                tags = tags,
                viewModel = viewModel,
                onPick = pickPhotos,
                onShowSharedPicker = { showSharedPicker = true },
                onShowOverridePicker = { overridePickerKey = it },
            )
        }
    }

    if (showSharedPicker) {
        SubtypePickerDialog(
            groups = state.groups,
            selectedId = state.sharedSubtypeId,
            localize = viewModel::localizeSubtype,
            onSelect = {
                viewModel.selectSharedSubtype(it)
                showSharedPicker = false
            },
            onDismiss = { showSharedPicker = false },
            optionTagPrefix = "import_subtype",
        )
    }
    overridePickerKey?.let { key ->
        SubtypePickerDialog(
            groups = state.groups,
            selectedId = state.photos
                .firstOrNull { it.key == key }
                ?.subtypeOverrideId
                ?: state.sharedSubtypeId,
            localize = viewModel::localizeSubtype,
            onSelect = { id ->
                viewModel.setPhotoSubtype(key, id)
                overridePickerKey = null
            },
            onDismiss = { overridePickerKey = null },
            optionTagPrefix = "import_subtype",
        )
    }

    state.pendingWarnings.firstOrNull()?.let { warning ->
        DuplicateWarningDialog(
            warning = warning,
            onKeep = viewModel::keepDuplicate,
            onDiscard = viewModel::discardDuplicate,
        )
    }
}

/** Editing view: empty pick state, or shared fields + photo grid + actions. */
@Composable
private fun EditingBody(
    state: ImportUiState,
    tags: List<Tag>,
    viewModel: ImportViewModel,
    onPick: () -> Unit,
    onShowSharedPicker: () -> Unit,
    onShowOverridePicker: (String) -> Unit,
) {
    if (state.photos.isEmpty() && state.pendingWarnings.isEmpty() && state.lastFailures.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.import_empty_intro),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.import_empty_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            GreenCta(
                text = stringResource(R.string.import_pick_photos),
                onClick = onPick,
                modifier = Modifier.testTag("import_pick"),
            )
            if (state.analyzing) {
                Spacer(modifier = Modifier.height(12.dp))
                AnalyzingLabel()
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shared fields --------------------------------------------------------------

        item(key = "shared_subtype") {
            SectionLabel(stringResource(R.string.editor_subtype))
            Text(
                text = stringResource(R.string.import_shared_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.groups
                        .flatMap { group -> group.subtypes }
                        .firstOrNull { it.id == state.sharedSubtypeId }
                        ?.let(viewModel::localizeSubtype)
                        ?: stringResource(R.string.editor_subtype_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.editor_subtype_pick),
                    onClick = onShowSharedPicker,
                    modifier = Modifier.testTag("import_subtype_pick"),
                )
            }
            if (state.subtypeMissing) {
                Text(
                    text = stringResource(R.string.editor_subtype_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item(key = "shared_seasons") {
            SectionLabel(stringResource(R.string.editor_seasons))
            ChipFlowRow {
                Season.entries.forEach { season ->
                    EditorialChip(
                        text = stringResource(seasonLabelRes(season)),
                        selected = season in state.seasons,
                        onClick = { viewModel.toggleSeason(season) },
                        modifier = Modifier.testTag("import_season_${season.name}"),
                    )
                }
            }
        }

        item(key = "shared_sex") {
            SectionLabel(stringResource(R.string.editor_sex))
            ChipFlowRow {
                Sex.entries.forEach { sex ->
                    EditorialChip(
                        text = stringResource(sexLabelRes(sex)),
                        selected = state.sex == sex,
                        onClick = { viewModel.setSex(sex) },
                        modifier = Modifier.testTag("import_sex_${sex.name}"),
                    )
                }
            }
        }

        item(key = "shared_tags") {
            SectionLabel(stringResource(R.string.editor_tags))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = state.tagInput,
                        onValueChange = viewModel::setTagInput,
                        label = stringResource(R.string.editor_tags_hint),
                        singleLine = true,
                        testTagValue = "import_tags_input",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                PillButton(
                    text = stringResource(R.string.action_add),
                    onClick = viewModel::commitTagInput,
                    modifier = Modifier.testTag("import_tag_commit"),
                )
            }
            if (tags.isNotEmpty()) {
                ChipFlowRow {
                    tags.forEach { tag ->
                        EditorialChip(
                            text = tag.name,
                            selected = tag.id in state.selectedTagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            modifier = Modifier.testTag("import_tag_${tag.name}"),
                        )
                    }
                }
            }
        }

        // Photos ----------------------------------------------------------------------

        if (state.photos.isNotEmpty()) {
            item(key = "photos_header") {
                SectionLabel(stringResource(R.string.editor_photos_count, state.photos.size))
            }
            items(
                items = state.photos.chunked(PHOTO_COLUMNS),
                key = { chunk -> "photo_${chunk.first().key}" },
            ) { chunk ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    chunk.forEach { photo ->
                        ImportPhotoCard(
                            photo = photo,
                            sharedSubtypeId = state.sharedSubtypeId,
                            groups = state.groups,
                            nameError = photo.key in state.blankNameKeys,
                            viewModel = viewModel,
                            onShowOverridePicker = onShowOverridePicker,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(PHOTO_COLUMNS - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.analyzing) {
            item(key = "analyzing") { AnalyzingLabel() }
        }

        // Failures + actions ------------------------------------------------------------

        if (state.lastFailures.isNotEmpty()) {
            item(key = "failures") {
                FailuresPanel(state = state)
            }
        }

        if (state.photos.isNotEmpty()) {
            item(key = "import_cta") {
                GreenCta(
                    text = stringResource(R.string.import_cta, state.photos.size),
                    onClick = viewModel::startImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_start"),
                )
            }
        }
        if (state.lastFailures.isNotEmpty()) {
            item(key = "done_cta") {
                PillButton(
                    text = stringResource(R.string.action_finish),
                    onClick = viewModel::finish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_done"),
                )
            }
        }
    }
}

/** One batch photo: thumbnail, editable name, per-photo subtype override. */
@Composable
private fun ImportPhotoCard(
    photo: ImportPhoto,
    sharedSubtypeId: Long?,
    groups: List<SubtypeGroup>,
    nameError: Boolean,
    viewModel: ImportViewModel,
    onShowOverridePicker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        rememberPickedPhoto(
            uri = photo.uri,
            targetLongSide = PHOTO_DECODE_THUMB,
            contentDescription = photo.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            placeholder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Cream.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = photo.name.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedInput(
            value = photo.name,
            onValueChange = { viewModel.setPhotoName(photo.key, it) },
            label = stringResource(R.string.editor_name),
            errorRes = if (nameError) R.string.editor_name_required else null,
            testTagValue = "import_name_${photo.key}",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val effectiveName = groups
                .flatMap { group -> group.subtypes }
                .firstOrNull { it.id == (photo.subtypeOverrideId ?: sharedSubtypeId) }
                ?.let(viewModel::localizeSubtype)
                ?: stringResource(R.string.editor_subtype_none)
            Text(
                text = effectiveName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_photo_subtype_${photo.key}"),
            )
            PillButton(
                text = stringResource(R.string.editor_subtype_pick),
                onClick = { onShowOverridePicker(photo.key) },
                modifier = Modifier.testTag("import_photo_subtype_pick_${photo.key}"),
            )
        }
        if (photo.subtypeOverrideId != null) {
            Text(
                text = stringResource(R.string.import_subtype_reset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("import_photo_subtype_reset_${photo.key}"),
            )
        }
    }
}

/** Sequential pass progress: counter plus a thin determinate bar. */
@Composable
private fun ImportingBody(phase: ImportPhase.Importing) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.import_progress, phase.done, phase.total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("import_progress"),
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = {
                    if (phase.total == 0) 0f else phase.done.toFloat() / phase.total
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_progress_bar"),
            )
        }
    }
}

/** End-of-pass report: what was created and which photos failed (with reasons). */
@Composable
private fun FailuresPanel(state: ImportUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("import_failures"),
    ) {
        Text(
            text = stringResource(R.string.import_created_line, state.createdCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.import_failures_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        state.lastFailures.forEach { failure ->
            val reason = when (failure.kind) {
                ImportFailureKind.UNREADABLE_PHOTO -> stringResource(R.string.import_failure_unreadable)
                ImportFailureKind.WRITE_FAILED -> stringResource(R.string.import_failure_write)
            }
            Text(
                text = "«${failure.photo.name.ifBlank { stringResource(R.string.editor_subtype_none) }}» — $reason",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AnalyzingLabel() {
    Text(
        text = stringResource(R.string.import_analyzing),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.testTag("import_analyzing"),
    )
}

/** Per-collision warning: keep the photo anyway or drop it. */
@Composable
private fun DuplicateWarningDialog(
    warning: ImportDuplicateWarning,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
) {
    EditorialDialog(onDismiss = onDiscard, scrimTag = "import_duplicate_scrim") {
        Text(
            text = stringResource(R.string.import_duplicate_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = warning.existingItemName
                ?.let { stringResource(R.string.import_duplicate_text_existing, it) }
                ?: stringResource(R.string.import_duplicate_text_batch),
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDiscard,
                modifier = Modifier.testTag("import_duplicate_discard"),
            )
            GreenCta(
                text = stringResource(R.string.import_duplicate_keep),
                onClick = onKeep,
                modifier = Modifier.testTag("import_keep_duplicate"),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(6.dp))
}

// Photo intake helpers -----------------------------------------------------------

/** DISPLAY_NAME of a picked URI; last path segment as the tolerant fallback. */
private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()

/** Display decode of a picked (not yet stored) image; null when unreadable. */
private suspend fun decodePickedPhoto(
    resolver: ContentResolver,
    uri: Uri,
    targetLongSide: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetLongSide) {
            sample *= 2
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }.getOrNull()
}

@Composable
private fun rememberPickedPhoto(
    uri: Uri,
    targetLongSide: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val bitmap = produceState<Bitmap?>(initialValue = null, uri, targetLongSide) {
        value = decodePickedPhoto(resolver, uri, targetLongSide)
    }.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier) { placeholder() }
    }
}

private const val PHOTO_COLUMNS: Int = 2
