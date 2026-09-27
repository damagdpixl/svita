package com.damagdpixl.svita.ui.outfits

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.AvatarCanvas
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.CollageTile
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.OutfitCollage
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.PolaroidCard
import com.damagdpixl.svita.core.designsystem.StickerBadge
import com.damagdpixl.svita.core.designsystem.monoUpper
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.avatar.AvatarMapper
import com.damagdpixl.svita.ui.wardrobe.ChipFlowRow
import com.damagdpixl.svita.ui.wardrobe.EditorialChip
import java.util.Locale

/**
 * The Outfits tab (P2 T6): «Сьогодні» dress-me pick over the live wardrobe
 * (engine + weather + persisted comfort slider), style chips, «Носити» /
 * «Оновити» actions, and the «не носилось останні 60 днів» section.
 *
 * The look renders twice on purpose: the paper doll ([AvatarCanvas]) shows how
 * the look wears, the flat-lay collage ([OutfitCollage]) shows the pieces.
 */
@Composable
fun OutfitsScreen(
    modifier: Modifier = Modifier,
    viewModel: OutfitsViewModel = viewModel(
        factory = viewModelFactory { initializer { OutfitsViewModel(SvitaGraph.get()) } },
    ),
) {
    val dressMe by viewModel.dressMe.collectAsState()
    val comfort by viewModel.comfort.collectAsState()
    val styleTags by viewModel.styleTags.collectAsState()
    val selectedStyles by viewModel.selectedStyles.collectAsState()
    val notWorn by viewModel.notWorn.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val definitions by viewModel.definitions.collectAsState()
    val avatarBody by viewModel.avatarBody.collectAsState()
    val avatarTone by viewModel.avatarTone.collectAsState()
    val wearSaved by viewModel.wearSaved.collectAsState()

    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        Locale(configuration.locales[0].toLanguageTag())
    }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("screen_outfits"),
    ) {
        Text(
            text = stringResource(R.string.outfits_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        // ---- «Сьогодні» ---------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.outfit_today_title).monoUpper(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.testTag("outfit_today_title"),
            )
            if (dressMe.look?.offline == true) {
                Spacer(Modifier.size(8.dp))
                StickerBadge(
                    text = stringResource(R.string.outfit_offline_badge),
                    modifier = Modifier.testTag("outfit_offline_badge"),
                )
            }
        }

        CharcoalPanel(
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("outfit_today_panel"),
        ) {
            val look = dressMe.look
            if (look != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.outfit_weather_range,
                            formatTemp(look.weatherMinC, locale),
                            formatTemp(look.weatherMaxC, locale),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream,
                        modifier = Modifier.testTag("outfit_weather"),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(conditionRes(look.condition)),
                        style = MaterialTheme.typography.labelMedium,
                        color = Cream.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("outfit_weather_condition"),
                    )
                }
            }

            // Comfort slider: −1 (coldest target) .. +1 (warmest), persisted.
            Text(
                text = stringResource(R.string.outfit_comfort_label).monoUpper(),
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = if (look != null) 16.dp else 4.dp)
                    .testTag("outfit_comfort_label"),
            )
            Slider(
                value = comfort.toFloat(),
                onValueChange = { viewModel.setComfort(it.toDouble()) },
                valueRange = -1f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outfit_comfort_slider"),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.outfit_comfort_cooler),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cream.copy(alpha = 0.55f),
                    modifier = Modifier.testTag("outfit_comfort_cooler"),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.outfit_comfort_warmer),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cream.copy(alpha = 0.55f),
                    modifier = Modifier.testTag("outfit_comfort_warmer"),
                )
            }

            // Style chips from the seeded style tags.
            Text(
                text = stringResource(R.string.outfit_style_label).monoUpper(),
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag("outfit_style_label"),
            )
            ChipFlowRow(modifier = Modifier.padding(top = 4.dp)) {
                val ukrainianChips = locale.language.lowercase().startsWith("uk")
                styleTags.forEach { tag ->
                    EditorialChip(
                        text = if (ukrainianChips) tag.nameUk else tag.nameEn,
                        selected = tag.key in selectedStyles,
                        onClick = { viewModel.toggleStyle(tag.key) },
                        onDark = true,
                        modifier = Modifier.testTag("outfit_style_${tag.key}"),
                    )
                }
            }

            // The look itself.
            when {
                dressMe.loading -> Text(
                    text = stringResource(R.string.outfit_dressing_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .testTag("outfit_loading"),
                )

                dressMe.look != null -> {
                    val current = dressMe.look!!
                    val paletteHexByKey = remember(palette) {
                        palette.associate { it.key.lowercase() to it.hex }
                    }
                    val colorDefinitionIds = remember(definitions) {
                        definitions.filter { it.type == AttributeType.COLOR }.map { it.id }.toSet()
                    }
                    val manifest = remember(
                        current,
                        paletteHexByKey,
                        colorDefinitionIds,
                        avatarBody,
                        avatarTone,
                    ) {
                        AvatarMapper.manifestFor(
                            aggregates = current.items,
                            definitions = definitions,
                            paletteHexByKey = paletteHexByKey,
                            bodyType = avatarBody,
                            skinTone = avatarTone,
                        )
                    }
                    PolaroidCard(
                        caption = stringResource(R.string.outfit_today_title),
                        rotationDegrees = 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .testTag("outfit_look_card"),
                    ) {
                        AvatarCanvas(
                            manifest = manifest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.55f),
                            testTag = "outfit_look_avatar",
                        )
                    }
                    // Flat-lay: one tile per piece (missing photos fall back to
                    // tinted silhouettes drawn by the same vector source).
                    val tiles = remember(current, paletteHexByKey, colorDefinitionIds) {
                        current.items.map { aggregate ->
                            val garment = AvatarMapper.garmentFor(
                                aggregate,
                                colorDefinitionIds,
                                paletteHexByKey,
                            )
                            CollageTile.Silhouette(shape = garment.shape, tintHex = garment.tintHex)
                        }
                    }
                    OutfitCollage(
                        tiles = tiles,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("outfit_look_collage"),
                    )
                    Text(
                        text = current.styles.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Cream.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("outfit_look_styles"),
                    )
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        current.items.forEach { aggregate ->
                            Text(
                                text = aggregate.item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cream,
                                modifier = Modifier.testTag("look_item_${aggregate.item.id}"),
                            )
                        }
                    }
                }

                dressMe.wardrobeCount == 0 -> Text(
                    text = stringResource(R.string.outfit_empty_wardrobe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .testTag("outfit_empty_wardrobe"),
                )

                else -> Text(
                    text = stringResource(R.string.outfit_no_look),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .testTag("outfit_no_look"),
                )
            }

            // Actions.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                GreenCta(
                    text = stringResource(R.string.outfit_wear_today),
                    onClick = viewModel::wearToday,
                    enabled = dressMe.look != null,
                    modifier = Modifier.testTag("outfit_wear_today"),
                )
                PillButton(
                    text = stringResource(R.string.outfit_refresh),
                    onClick = viewModel::refresh,
                    modifier = Modifier.testTag("outfit_refresh"),
                )
            }
            if (wearSaved) {
                StickerBadge(
                    text = stringResource(R.string.outfit_worn_saved),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("outfit_worn_confirm"),
                )
            }
        }

        // ---- «Не носилось останні 60 днів» --------------------------------
        Text(
            text = stringResource(R.string.outfit_notworn_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(top = 28.dp)
                .testTag("notworn_title"),
        )
        if (notWorn.isEmpty()) {
            Text(
                text = stringResource(R.string.outfit_notworn_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag("notworn_empty"),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                StickerBadge(
                    text = context.resources.getQuantityString(
                        R.plurals.outfit_notworn_count,
                        notWorn.size,
                        notWorn.size,
                    ),
                    modifier = Modifier.testTag("notworn_badge"),
                )
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                notWorn.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("notworn_item_${item.id}"),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@StringRes
private fun conditionRes(condition: String): Int = when (condition) {
    "clear" -> R.string.outfit_condition_clear
    "partly_cloudy" -> R.string.outfit_condition_partly_cloudy
    "overcast" -> R.string.outfit_condition_overcast
    "fog" -> R.string.outfit_condition_fog
    "rain" -> R.string.outfit_condition_rain
    "snow" -> R.string.outfit_condition_snow
    "storm" -> R.string.outfit_condition_storm
    else -> R.string.outfit_condition_unknown
}

/** Locale-independent whole-degree rendering («10», «-3»). */
private fun formatTemp(value: Double, locale: Locale): String =
    String.format(locale, "%.0f", value)
