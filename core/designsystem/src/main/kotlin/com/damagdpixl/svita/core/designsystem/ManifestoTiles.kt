package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen background of tiled, slightly rotated mono text lines — the
 * typewriter-wallpaper manifesto («NO ACCOUNTS · NO CLOUD · …»).
 *
 * The [text] comes from the caller (localized in the app module); the tiles
 * are drawn through a single cached text layout.
 */
@Composable
fun ManifestoTiles(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
    fontSize: TextUnit = 14.sp,
    rotationDegrees: Float = -4f,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            letterSpacing = 1.5.sp,
        )
        val layout = textMeasurer.measure(
            text = AnnotatedString(text.monoUpper()),
            style = style,
            maxLines = 1,
        )
        val tileWidth = layout.size.width.toFloat()
        val tileHeight = layout.size.height.toFloat()
        val stepX = tileWidth + 96.dp.toPx()
        val stepY = tileHeight + 24.dp.toPx()

        var row = 0
        var y = -stepY
        while (y < size.height + stepY) {
            val rowOffset = if (row % 2 == 0) 0f else -stepX * 0.35f
            var x = -stepX + rowOffset
            while (x < size.width + stepX) {
                rotate(
                    degrees = rotationDegrees,
                    pivot = Offset(x + tileWidth / 2f, y + tileHeight / 2f),
                ) {
                    drawText(
                        textLayoutResult = layout,
                        color = color,
                        topLeft = Offset(x, y),
                    )
                }
                x += stepX
            }
            y += stepY
            row++
        }
    }
}
