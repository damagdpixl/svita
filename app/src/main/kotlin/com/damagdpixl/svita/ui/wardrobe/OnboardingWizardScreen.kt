package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.StickerBadge
import com.damagdpixl.svita.data.SvitaGraph

/**
 * First-run manual-add wizard: photo (optional) -> subtype -> name ->
 * attributes (optional). Every step is skippable; «skip» exits without saving,
 * «next/finish» walks forward. The final step validates all attributes and
 * writes the first item through the repositories.
 *
 * Rendered in place by [WardrobeScreen] while the onboarding flag is absent;
 * finishing (or skipping) flips the flag and the gallery takes over — no
 * navigation state involved.
 */
@Composable
fun OnboardingWizardScreen(
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OnboardingViewModel(SvitaGraph.get(), localeTag) }
        },
    )
    val state by viewModel.state.collectAsState()
    val palette by viewModel.palette.collectAsState()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("wizard_root"),
    ) {
        Text(
            text = stringResource(R.string.wizard_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.wizard_step_of,
                state.step.ordinal + 1,
                WizardStep.entries.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.testTag("wizard_progress"),
        )
        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (state.step) {
                WizardStep.PHOTO -> WizardPhotoStep(
                    pickedUris = state.pickedUris,
                    onAdd = viewModel::addPhotos,
                    onRemove = viewModel::removePhoto,
                )

                WizardStep.SUBTYPE -> WizardSubtypeStep(
                    groups = state.groups,
                    selectedId = state.subtype?.id,
                    localize = viewModel::localize,
                    onSelect = viewModel::selectSubtype,
                )

                WizardStep.NAME -> WizardNameStep(
                    name = state.name,
                    onNameChange = viewModel::setName,
                )

                WizardStep.ATTRIBUTES -> WizardAttributesStep(
                    inputs = state.attributeInputs,
                    errors = state.attributeErrors,
                    palette = palette,
                    onValueChange = viewModel::setAttributeValue,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != WizardStep.PHOTO) {
                PillButton(
                    text = stringResource(R.string.action_back),
                    onClick = viewModel::back,
                    modifier = Modifier.testTag("wizard_back"),
                )
            }
            PillButton(
                text = stringResource(R.string.action_skip),
                onClick = viewModel::skip,
                modifier = Modifier.testTag("wizard_skip"),
            )
            Spacer(modifier = Modifier.weight(1f))
            if (state.step == WizardStep.ATTRIBUTES) {
                GreenCta(
                    text = stringResource(R.string.action_finish),
                    onClick = viewModel::finish,
                    enabled = !state.saving,
                    modifier = Modifier.testTag("wizard_finish"),
                )
            } else {
                GreenCta(
                    text = stringResource(R.string.action_next),
                    onClick = viewModel::next,
                    modifier = Modifier.testTag("wizard_next"),
                )
            }
        }
    }
}

@Composable
private fun WizardPhotoStep(
    pickedUris: List<Uri>,
    onAdd: (List<Uri>) -> Unit,
    onRemove: (Uri) -> Unit,
) {
    val pickPhotos = rememberPhotoPicker(onPicked = onAdd)
    Text(
        text = stringResource(R.string.wizard_photo_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.wizard_photo_hint),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    )
    Spacer(modifier = Modifier.height(18.dp))
    PillButton(
        text = stringResource(R.string.editor_add_photo),
        onClick = { pickPhotos() },
        modifier = Modifier.testTag("wizard_photo_add"),
    )
    if (pickedUris.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.editor_photos_count, pickedUris.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WizardSubtypeStep(
    groups: List<SubtypeGroup>,
    selectedId: Long?,
    localize: (com.damagdpixl.svita.core.model.Subtype) -> String,
    onSelect: (com.damagdpixl.svita.core.model.Subtype) -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_subtype_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(14.dp))
    // Plain Column under the screen's own vertical scroll: the seeded taxonomy
    // is small, and a lazy list nested in a scrollable parent virtualizes to
    // zero rows under Robolectric (documented in the P2 report).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_subtype_list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            Text(
                text = if (LocalConfiguration.current.locales[0].language == "uk") {
                    group.category.nameUk
                } else {
                    group.category.nameEn
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 10.dp),
            )
            group.subtypes.forEach { subtype ->
                val selected = subtype.id == selectedId
                Text(
                    text = localize(subtype),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        Marigold
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Marigold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.background)
                        .clickable { onSelect(subtype) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("wizard_subtype_${subtype.key}"),
                )
            }
        }
    }
}

@Composable
private fun WizardNameStep(
    name: String,
    onNameChange: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_name_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(18.dp))
    OutlinedInput(
        value = name,
        onValueChange = onNameChange,
        label = stringResource(R.string.editor_name),
        testTagValue = "wizard_name",
    )
}

@Composable
private fun WizardAttributesStep(
    inputs: List<AttributeInput>,
    errors: Map<Long, Int>,
    palette: List<com.damagdpixl.svita.core.model.PaletteColor>,
    onValueChange: (Long, String) -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_attributes_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (inputs.isEmpty()) {
        Text(
            text = stringResource(R.string.wizard_attributes_empty),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    } else {
        inputs.forEach { input ->
            AttributeField(
                input = input,
                errorRes = errors[input.definition.id],
                palette = palette,
                onValueChange = { value -> onValueChange(input.definition.id, value) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    StickerBadge(text = stringResource(R.string.wizard_attributes_optional))
}
