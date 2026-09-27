package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat-lay outfit collage (P2 T5): the photo-first fallback visualization of
 * an outfit. Photos render as square tiles in a 2x2 (<= 4 tiles) or 3x3
 * (5..9 tiles) composition; wardrobe items without a photo contribute a
 * tinted garment-silhouette tile drawn by the same [VectorAvatarLayerSource]
 * the paper doll uses — so the collage stays renderable for ANY outfit with
 * zero external assets.
 */
public sealed interface CollageTile {

    /** An item photo, already resolved to a painter by the caller. */
    public data class Photo(
        val painter: Painter,
        val contentDescription: String? = null,
    ) : CollageTile

    /** A tinted garment silhouette standing in for a missing photo. */
    public data class Silhouette(
        val shape: AvatarGarmentShape,
        val tintHex: String? = null,
    ) : CollageTile
}

/** Column count of the composition: 2 while everything fits a 2x2 grid. */
public fun collageColumnCount(tileCount: Int): Int = if (tileCount <= 4) 2 else 3

/**
 * The collage grid. Empty [tiles] renders nothing (callers show their own
 * empty state); more than 9 tiles are truncated to 9 — the composition is a
 * look, not a wardrobe list. Silhouette tiles are drawn through
 * [layerSource], so the asset-pack seam covers collage thumbnails too.
 */
@Composable
public fun OutfitCollage(
    tiles: List<CollageTile>,
    modifier: Modifier = Modifier,
    layerSource: AvatarLayerSource = VectorAvatarLayerSource,
    spacing: Dp = 6.dp,
    tileCorner: Dp = 4.dp,
    silhouetteBackground: Color = Cream,
    testTag: String? = null,
    contentDescription: String? = null,
) {
    if (tiles.isEmpty()) {
        return
    }
    val visible = tiles.take(MAX_TILES)
    val columns = collageColumnCount(visible.size)
    var gridModifier = modifier
    if (testTag != null) {
        gridModifier = gridModifier.testTag(testTag)
    }
    if (contentDescription != null) {
        gridModifier = gridModifier.semantics { this.contentDescription = contentDescription }
    }
    Column(modifier = gridModifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        visible.chunked(columns).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowTiles.forEach { tile ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(tileCorner))
                            .background(White),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (tile) {
                            is CollageTile.Photo -> Image(
                                painter = tile.painter,
                                contentDescription = tile.contentDescription,
                                modifier = Modifier.fillMaxSize(),
                            )

                            is CollageTile.Silhouette -> Canvas(modifier = Modifier.fillMaxSize()) {
                                drawSilhouette(tile, silhouetteBackground, layerSource)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_TILES = 9

/** Draws one garment silhouette fitted into the tile, over a cream well. */
private fun DrawScope.drawSilhouette(
    tile: CollageTile.Silhouette,
    background: Color,
    layerSource: AvatarLayerSource,
) {
    drawRect(color = background)
    val tint = AvatarTint.normalized(tile.tintHex)
    val layer = AvatarLayer.Garment(
        garmentId = 0L,
        slot = AvatarSlot.BODY,
        shape = tile.shape,
        tintHex = tint,
        outlineHex = AvatarTint.outlineFor(tint),
    )
    val bounds = layerSource.shapeBounds(tile.shape)
    val pad = minOf(size.width, size.height) * 0.08f
    val availableW = (size.width - 2f * pad).coerceAtLeast(1f)
    val availableH = (size.height - 2f * pad).coerceAtLeast(1f)
    val scale = minOf(availableW / bounds.width, availableH / bounds.height)
    val originX = (size.width - bounds.width * scale) / 2f
    val originY = (size.height - bounds.height * scale) / 2f
    withTransform({
        translate(originX - bounds.left * scale, originY - bounds.top * scale)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        layerSource.drawGarment(this, layer)
    }
}
