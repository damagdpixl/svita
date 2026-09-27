package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.ManifestoTiles
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.PolaroidCard
import com.damagdpixl.svita.core.designsystem.StickerBadge
import com.damagdpixl.svita.core.designsystem.StickerVariant
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.SvitaGraph

/**
 * The Wardrobe tab: gallery grid of PolaroidCards grouped by category,
 * debounced name search, the filter panel (seasons, sex, tags, color), the
 * archive toggle, the first-run wizard trigger and the bulk-import hint.
 */
@Composable
fun WardrobeScreen(
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val viewModel: WardrobeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WardrobeViewModel(SvitaGraph.get(), localeTag) }
        },
    )
    val gallery by viewModel.gallery.collectAsState()
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val hintDismissed by viewModel.importHintDismissed.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    val showHint = onboardingDone == true && !hintDismissed

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_wardrobe"),
    ) {
        if ((gallery?.totalCount ?: 0) == 0) {
            ManifestoTiles(
                text = stringResource(R.string.manifesto_line),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onboardingDone == false) {
            // First run: the wizard owns the tab (in place, no navigation
            // state to race); completing or skipping flips the settings flag
            // and the grid takes over. While the flag is unread (null) the
            // screen renders neither branch — no grid→wizard flip mid-click.
            OnboardingWizardScreen(onFinished = {})
        } else if (onboardingDone == true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.wardrobe_headline),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    // The empty state carries its own centered CTA; show the header
                    // one only when the grid is populated (single ADD ITEM node).
                    if ((gallery?.totalCount ?: 0) > 0) {
                        GreenCta(
                            text = stringResource(R.string.wardrobe_add_item),
                            onClick = onAddItem,
                            modifier = Modifier.testTag("wardrobe_add"),
                        )
                    }
                }

                if (showHint) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StickerBadge(
                            text = stringResource(R.string.wardrobe_import_hint),
                            variant = StickerVariant.Bubble,
                            modifier = Modifier
                                .testTag("import_hint")
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onOpenImport,
                                ),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier
                                .testTag("import_hint_dismiss")
                                .clickable(onClick = viewModel::dismissImportHint)
                                .padding(6.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SearchAndControlsRow(
                    viewModel = viewModel,
                    onShowFilters = { showFilters = true },
                )

                Spacer(modifier = Modifier.height(12.dp))
                val state = gallery
                when {
                    state == null -> Unit
                    state.totalCount == 0 -> EmptyWardrobe(onAddItem = onAddItem)
                    else -> GalleryGrid(state = state, onOpenItem = onOpenItem)
                }
            }

            if (showFilters) {
                FilterPanelHost(
                    viewModel = viewModel,
                    onClose = { showFilters = false },
                )
            }
        }
    }
}

@Composable
private fun SearchAndControlsRow(
    viewModel: WardrobeViewModel,
    onShowFilters: () -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedInput(
                value = query,
                onValueChange = viewModel::setQuery,
                label = stringResource(R.string.wardrobe_search_hint),
                singleLine = true,
                testTagValue = "wardrobe_search",
            )
        }
        PillButton(
            text = stringResource(R.string.wardrobe_filters),
            onClick = onShowFilters,
            modifier = Modifier.testTag("wardrobe_filters_button"),
        )
        EditorialChip(
            text = stringResource(R.string.wardrobe_archive_toggle),
            selected = filter.includeArchived,
            onClick = { viewModel.setArchivedVisible(!filter.includeArchived) },
            modifier = Modifier.testTag("wardrobe_archive_toggle"),
        )
    }
}

@Composable
private fun EmptyWardrobe(onAddItem: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wardrobe_empty_intro),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.wardrobe_empty_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        GreenCta(
            text = stringResource(R.string.wardrobe_add_item),
            onClick = onAddItem,
            modifier = Modifier.testTag("wardrobe_add_empty"),
        )
    }
}

@Composable
private fun GalleryGrid(
    state: GalleryState,
    onOpenItem: (Long) -> Unit,
) {
    if (state.sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.wardrobe_nothing_found),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("wardrobe_grid"),
    ) {
        state.sections.forEach { section ->
            item(
                key = "header_${section.categoryId}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = section.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("section_header_${section.categoryId}"),
                )
            }
            items(
                items = section.items,
                key = { galleryItem -> "item_${galleryItem.item.id}" },
            ) { galleryItem ->
                PolaroidCardItem(
                    galleryItem = galleryItem,
                    onClick = { onOpenItem(galleryItem.item.id) },
                )
            }
        }
    }
}

@Composable
private fun PolaroidCardItem(
    galleryItem: GalleryItem,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.alpha(if (galleryItem.item.archived) 0.65f else 1f)) {
        PolaroidCard(
            caption = galleryItem.item.name,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("item_card_${galleryItem.item.id}")
                .clickable(onClick = onClick),
        ) {
            StoredPhoto(
                path = galleryItem.coverPath,
                contentDescription = galleryItem.item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                targetLongSide = PHOTO_DECODE_GRID,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Cream.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = galleryItem.item.name.take(1),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                },
            )
        }
        if (galleryItem.item.archived) {
            Spacer(modifier = Modifier.height(4.dp))
            StickerBadge(text = stringResource(R.string.wardrobe_in_archive))
        }
    }
}

@Composable
private fun FilterPanelHost(
    viewModel: WardrobeViewModel,
    onClose: () -> Unit,
) {
    val filter by viewModel.filter.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val colorDefinitions by viewModel.colorDefinitions.collectAsState()

    EditorialDialog(onDismiss = onClose, scrimTag = "filter_scrim") {
        Text(
            text = stringResource(R.string.wardrobe_filters),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilterPanelBody(
            filter = filter,
            tags = tags,
            palette = palette,
            colorDefinitions = colorDefinitions,
            onUpdate = viewModel::updateFilter,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_reset),
                onClick = viewModel::resetFilters,
                modifier = Modifier.testTag("filter_reset"),
            )
            GreenCta(
                text = stringResource(R.string.action_apply),
                onClick = onClose,
                modifier = Modifier.testTag("filter_apply"),
            )
        }
    }
}

@Composable
private fun FilterPanelBody(
    filter: FilterState,
    tags: List<Tag>,
    palette: List<PaletteColor>,
    colorDefinitions: List<AttributeDefinition>,
    onUpdate: ((FilterState) -> FilterState) -> Unit,
) {
    Text(
        text = stringResource(R.string.editor_seasons),
        style = MaterialTheme.typography.labelMedium,
        color = Cream.copy(alpha = 0.7f),
    )
    ChipFlowRow {
        Season.entries.forEach { season ->
            EditorialChip(
                text = stringResource(seasonLabelRes(season)),
                selected = season in filter.seasons,
                onClick = {
                    onUpdate { current ->
                        current.copy(seasons = current.seasons.toggleMember(season))
                    }
                },
                onDark = true,
                modifier = Modifier.testTag("filter_season_${season.name}"),
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.editor_sex),
        style = MaterialTheme.typography.labelMedium,
        color = Cream.copy(alpha = 0.7f),
    )
    ChipFlowRow {
        Sex.entries.forEach { sex ->
            EditorialChip(
                text = stringResource(sexLabelRes(sex)),
                selected = filter.sex == sex,
                onClick = {
                    onUpdate { current ->
                        current.copy(sex = if (current.sex == sex) null else sex)
                    }
                },
                onDark = true,
                modifier = Modifier.testTag("filter_sex_${sex.name}"),
            )
        }
    }
    if (tags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.editor_tags),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipFlowRow {
            tags.forEach { tag ->
                EditorialChip(
                    text = tag.name,
                    selected = tag.id in filter.tagIds,
                    onClick = {
                        onUpdate { current ->
                            current.copy(tagIds = current.tagIds.toggleMember(tag.id))
                        }
                    },
                    onDark = true,
                )
            }
        }
    }
    if (colorDefinitions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.filter_color),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipFlowRow {
            colorDefinitions.forEach { definition ->
                EditorialChip(
                    text = definition.key,
                    selected = filter.colorDefinitionId == definition.id,
                    onClick = {
                        onUpdate { current ->
                            current.copy(
                                colorDefinitionId = if (current.colorDefinitionId == definition.id) {
                                    null
                                } else {
                                    definition.id
                                },
                                colorHex = null,
                            )
                        }
                    },
                    onDark = true,
                )
            }
        }
        if (filter.colorDefinitionId != null) {
            ChipFlowRow {
                palette.forEach { color ->
                    val selected = color.hex.equals(filter.colorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(34.dp)
                            .background(parseHexColor(color.hex), CircleShape)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Cream else Cream.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                            .clickable {
                                onUpdate { current -> current.copy(colorHex = color.hex) }
                            }
                            .testTag("filter_color_${color.key}"),
                    )
                }
            }
        }
    }
}

private fun <T> Set<T>.toggleMember(value: T): Set<T> =
    if (value in this) this - value else this + value
