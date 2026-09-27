package com.damagdpixl.svita.ui.avatar

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.AvatarBodyType
import com.damagdpixl.svita.core.designsystem.AvatarCanvas
import com.damagdpixl.svita.core.designsystem.AvatarManifest
import com.damagdpixl.svita.core.designsystem.AvatarSkinTone
import com.damagdpixl.svita.core.designsystem.PolaroidCard
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialChip

/**
 * Settings -> Avatar: the paper-doll preview (Polaroid framing) plus the two
 * pickers — body type (3 silhouettes) and skin tone (3 own flat fills).
 * Every choice persists through the settings repository (`avatar.body`,
 * `avatar.tone`) and is reflected by the preview immediately.
 */
@Composable
fun AvatarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AvatarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AvatarViewModel(SvitaGraph.get()) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("screen_avatar"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("avatar_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.avatar_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PolaroidCard(
                caption = stringResource(R.string.avatar_preview_caption),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("avatar_preview_card"),
            ) {
                if (state.loading) {
                    // Held until the persisted body/tone are loaded, so the
                    // preview never flashes the defaults before the real
                    // configuration appears.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.55f)
                            .testTag("avatar_preview_loading"),
                    )
                } else {
                    AvatarCanvas(
                        manifest = AvatarManifest(
                            bodyType = state.body,
                            skinTone = state.tone,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.55f),
                        testTag = "avatar_preview",
                    )
                }
            }

            Text(
                text = stringResource(R.string.avatar_body_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 6.dp)
                    .fillMaxWidth()
                    .testTag("avatar_body_label"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarBodyType.entries.forEach { bodyType ->
                    EditorialChip(
                        text = bodyTypeName(bodyType),
                        selected = state.body == bodyType,
                        onClick = { viewModel.setBody(bodyType) },
                        modifier = Modifier.testTag("avatar_body_${bodyType.id}"),
                    )
                }
            }

            Text(
                text = stringResource(R.string.avatar_tone_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 6.dp)
                    .fillMaxWidth()
                    .testTag("avatar_tone_label"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarSkinTone.entries.forEach { tone ->
                    EditorialChip(
                        text = toneName(tone),
                        selected = state.tone == tone,
                        onClick = { viewModel.setTone(tone) },
                        modifier = Modifier.testTag("avatar_tone_${tone.id}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun bodyTypeName(bodyType: AvatarBodyType): String = when (bodyType) {
    AvatarBodyType.SLIM -> stringResource(R.string.avatar_body_slim)
    AvatarBodyType.REGULAR -> stringResource(R.string.avatar_body_regular)
    AvatarBodyType.CURVY -> stringResource(R.string.avatar_body_curvy)
}

@Composable
private fun toneName(tone: AvatarSkinTone): String = when (tone) {
    AvatarSkinTone.LIGHT -> stringResource(R.string.avatar_tone_light)
    AvatarSkinTone.MEDIUM -> stringResource(R.string.avatar_tone_medium)
    AvatarSkinTone.DEEP -> stringResource(R.string.avatar_tone_deep)
}
