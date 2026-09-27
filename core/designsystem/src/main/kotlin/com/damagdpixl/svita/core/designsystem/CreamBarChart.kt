package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One row of the editorial bar chart: label, 0..1 bar fraction, optional value text. */
data class CreamBarRow(
    /** Stable identity for test tags and recomposition keys. */
    val id: String,
    /** Mono-uppercase label rendered before the bar. */
    val label: String,
    /** Bar fill as a fraction of the track, clamped to 0..1. */
    val fraction: Float,
    /** Right-aligned value caption (e.g. «125,5 за носіння»); null = none. */
    val valueText: String? = null,
)

/**
 * The «Editorial Collage» bar chart (P2 T7 statistics): mono uppercase labels,
 * flat cream bars drawn on a Canvas — no chart library, no gridlines, no
 * animation. Designed to sit inside a [CharcoalPanel]: cream on charcoal.
 *
 * The label and the value caption are real Text composables (accessible and
 * testable); only the bars are Canvas-drawn — a dim full-width track with a
 * rounded cream fill whose width is [CreamBarRow.fraction] of the track.
 */
@Composable
fun CreamBarChart(
    rows: List<CreamBarRow>,
    modifier: Modifier = Modifier,
    labelColor: Color = Cream,
    barColor: Color = Cream,
    chartTag: String = "cream_bar_chart",
) {
    Column(modifier = modifier.testTag(chartTag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${chartTag}_row_${row.id}"),
            ) {
                Text(
                    text = row.label.monoUpper(),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    modifier = Modifier.testTag("${chartTag}_label_${row.id}"),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .testTag("${chartTag}_bar_${row.id}"),
                ) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    val fill = row.fraction.coerceIn(0f, 1f) * size.width
                    if (fill > 0f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(fill, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                }
                if (row.valueText != null) {
                    Text(
                        text = row.valueText,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.75f),
                        modifier = Modifier.testTag("${chartTag}_value_${row.id}"),
                    )
                }
            }
        }
    }
}
