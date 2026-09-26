package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Visual variant of a [StickerBadge]. */
enum class StickerVariant {
    /** Simple sticker tag with asymmetric rounded corners. */
    Rounded,

    /** Speech-bubble tag with a tail, for «New»-style highlights. */
    Bubble,
}

/**
 * Small sticker-style tag in marigold with charcoal mono uppercase text.
 * The [StickerVariant.Bubble] variant adds a speech-bubble tail.
 */
@Composable
fun StickerBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: StickerVariant = StickerVariant.Rounded,
    containerColor: Color = Marigold,
    contentColor: Color = Charcoal,
) {
    val shape: Shape = when (variant) {
        StickerVariant.Rounded -> RoundedStickerShape
        StickerVariant.Bubble -> SpeechBubbleShape
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text.monoUpper(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private val RoundedStickerShape: Shape =
    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 2.dp)

/** Rounded rect whose bottom-left corner is cut into a speech-bubble tail. */
private object SpeechBubbleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = Path().apply {
            with(density) {
                val radius = 12.dp.toPx()
                val tailDepth = 10.dp.toPx().coerceAtMost(size.height * 0.45f)
                val tailX = 14.dp.toPx().coerceAtMost(size.width * 0.5f)

                moveTo(radius, 0f)
                lineTo(size.width - radius, 0f)
                quadraticBezierTo(size.width, 0f, size.width, radius)
                lineTo(size.width, size.height - radius)
                quadraticBezierTo(size.width, size.height, size.width - radius, size.height)
                lineTo(tailX, size.height)
                // Tail: diagonal cut from the bottom edge up to the left edge.
                lineTo(0f, size.height - tailDepth)
                lineTo(0f, radius)
                quadraticBezierTo(0f, 0f, radius, 0f)
                close()
            }
        }
        return Outline.Generic(outline)
    }
}
