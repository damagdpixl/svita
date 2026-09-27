package com.damagdpixl.svita.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.CreamBarChart
import com.damagdpixl.svita.core.designsystem.CreamBarRow
import com.damagdpixl.svita.core.designsystem.ManifestoTiles
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.formatPrice

/**
 * Settings -> «Статистика» (P2 T7): honest numbers only — cost-per-wear
 * (best value first), most/least worn top-5 and the wardrobe composition by
 * section, rendered as editorial cream bars on charcoal panels.
 *
 * Empty state: the manifesto wallpaper with a serif line — statistics appear
 * after the first wears, nothing fake is shown before that.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory { initializer { StatsViewModel(SvitaGraph.get()) } },
    ),
) {
    val stats by viewModel.stats.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_stats"),
    ) {
        if (!stats.loading && stats.isEmpty) {
            ManifestoTiles(
                text = stringResource(R.string.manifesto_line),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stats_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (!stats.loading && stats.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag("stats_empty"),
                    )
                }
            } else {
                // ---- Cost per wear ------------------------------------------
                SectionTitle(
                    text = stringResource(R.string.stats_cpw_title),
                    tag = "stats_cpw_title",
                )
                CharcoalPanel(modifier = Modifier.testTag("stats_cpw_panel")) {
                    val cpw = stats.costPerWear
                    if (cpw.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_cpw_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("stats_cpw_empty"),
                        )
                    } else {
                        val max = cpw.maxOf { it.costPerWear }.coerceAtLeast(0.0001)
                        CreamBarChart(
                            rows = cpw.map { row ->
                                CreamBarRow(
                                    id = row.item.id.toString(),
                                    label = row.item.name,
                                    fraction = (row.costPerWear / max).toFloat(),
                                    valueText = stringResource(
                                        R.string.stats_cpw_value,
                                        formatPrice(row.costPerWear),
                                    ),
                                )
                            },
                            chartTag = "stats_cpw_chart",
                        )
                    }
                }

                // ---- Most worn ----------------------------------------------
                SectionTitle(
                    text = stringResource(R.string.stats_most_title),
                    tag = "stats_most_title",
                )
                CharcoalPanel(modifier = Modifier.testTag("stats_most_panel")) {
                    val rows = stats.mostWorn
                    if (rows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_cpw_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("stats_most_empty"),
                        )
                    } else {
                        val max = rows.maxOf { it.wearCount }.coerceAtLeast(1)
                        CreamBarChart(
                            rows = rows.map { row ->
                                CreamBarRow(
                                    id = row.item.id.toString(),
                                    label = row.item.name,
                                    fraction = row.wearCount.toFloat() / max,
                                    valueText = wearsText(row.wearCount),
                                )
                            },
                            chartTag = "stats_most_chart",
                        )
                    }
                }

                // ---- Least worn ---------------------------------------------
                SectionTitle(
                    text = stringResource(R.string.stats_least_title),
                    tag = "stats_least_title",
                )
                CharcoalPanel(modifier = Modifier.testTag("stats_least_panel")) {
                    val rows = stats.leastWorn
                    if (rows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_cpw_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("stats_least_empty"),
                        )
                    } else {
                        val max = rows.maxOf { it.wearCount }.coerceAtLeast(1)
                        CreamBarChart(
                            rows = rows.map { row ->
                                CreamBarRow(
                                    id = row.item.id.toString(),
                                    label = row.item.name,
                                    fraction = row.wearCount.toFloat() / max,
                                    valueText = wearsText(row.wearCount),
                                )
                            },
                            chartTag = "stats_least_chart",
                        )
                    }
                }

                // ---- Composition --------------------------------------------
                SectionTitle(
                    text = stringResource(R.string.stats_composition_title),
                    tag = "stats_composition_title",
                )
                CharcoalPanel(modifier = Modifier.testTag("stats_composition_panel")) {
                    val composition = stats.composition
                    if (composition.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_cpw_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream.copy(alpha = 0.75f),
                            modifier = Modifier.testTag("stats_composition_empty"),
                        )
                    } else {
                        val max = composition.maxOf { it.count }.coerceAtLeast(1)
                        CreamBarChart(
                            rows = composition.map { row ->
                                CreamBarRow(
                                    id = row.section.db,
                                    label = sectionLabel(row.section),
                                    fraction = row.count.toFloat() / max,
                                    valueText = pluralStringResource(
                                        R.plurals.stats_items_count,
                                        row.count,
                                        row.count,
                                    ),
                                )
                            },
                            chartTag = "stats_composition_chart",
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .testTag(tag),
    )
}

@Composable
private fun wearsText(count: Long): String =
    pluralStringResource(R.plurals.stats_wears_count, count.toInt(), count.toInt())

@Composable
private fun sectionLabel(section: Section): String = stringResource(
    when (section) {
        Section.BODY -> R.string.section_body
        Section.LEGS -> R.string.section_legs
        Section.FEET -> R.string.section_feet
        Section.DRESS -> R.string.section_dress
        Section.OUTER -> R.string.section_outer
        Section.ACCESSORY -> R.string.section_accessory
        Section.HAT -> R.string.section_hat
        Section.SCARF -> R.string.section_scarf
        Section.BAG -> R.string.section_bag
    },
)
