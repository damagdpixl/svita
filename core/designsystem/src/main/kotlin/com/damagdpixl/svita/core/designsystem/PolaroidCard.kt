package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Photo card with the polaroid feel: cream frame, white photo well, mono
 * caption, and a slight rotation. [content] is the "photo".
 */
@Composable
fun PolaroidCard(
    modifier: Modifier = Modifier,
    rotationDegrees: Float = -2f,
    caption: String? = null,
    frameColor: Color = Cream,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.rotate(rotationDegrees),
        color = frameColor,
        contentColor = Charcoal,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.background(White)) {
                content()
            }
            if (caption != null) {
                Text(
                    text = caption.monoUpper(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 6.dp),
                )
            }
        }
    }
}
