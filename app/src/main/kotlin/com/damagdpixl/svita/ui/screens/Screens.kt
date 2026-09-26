package com.damagdpixl.svita.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.ManifestoTiles

/**
 * Wardrobe empty state: the manifesto typewriter wallpaper, a serif headline
 * and the green «Add item» CTA.
 */
@Composable
fun WardrobeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_wardrobe"),
    ) {
        ManifestoTiles(
            text = stringResource(R.string.manifesto_line),
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.wardrobe_headline),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            GreenCta(
                text = stringResource(R.string.wardrobe_add_item),
                onClick = { /* real flow lands with the data milestone */ },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Shared placeholder in the design language: serif headline + charcoal panel hint. */
@Composable
private fun PlaceholderScreen(
    headlineText: String,
    hintText: String,
    screenTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag(screenTag),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = headlineText,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        CharcoalPanel {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.headlineSmall,
                color = Cream,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.70f),
            )
        }
    }
}

@Composable
fun OutfitsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        headlineText = stringResource(R.string.outfits_headline),
        hintText = stringResource(R.string.outfits_hint),
        screenTag = "screen_outfits",
        modifier = modifier,
    )
}

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        headlineText = stringResource(R.string.calendar_headline),
        hintText = stringResource(R.string.calendar_hint),
        screenTag = "screen_calendar",
        modifier = modifier,
    )
}

@Composable
fun PackingScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        headlineText = stringResource(R.string.packing_headline),
        hintText = stringResource(R.string.packing_hint),
        screenTag = "screen_packing",
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        headlineText = stringResource(R.string.settings_headline),
        hintText = stringResource(R.string.settings_hint),
        screenTag = "screen_settings",
        modifier = modifier,
    )
}
