package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Vector renderer of the paper-doll avatar.
 *
 * [AvatarLayerSource] is the manifest-consumer seam: the v0.1 implementation
 * ([VectorAvatarLayerSource]) draws stylized geometric silhouettes in a fixed
 * 200x400 virtual coordinate space; a future asset pack (PNG/SVG sheets) can
 * implement the same three members WITHOUT touching [AvatarCanvas], the
 * manifest ordering, or any call site.
 */
public interface AvatarLayerSource {

    /** Draws the doll body (head, neck, torso, arms, legs) for the layer. */
    public fun drawBody(scope: DrawScope, layer: AvatarLayer.Body)

    /** Draws one garment silhouette for the layer. */
    public fun drawGarment(scope: DrawScope, layer: AvatarLayer.Garment)

    /** Virtual-space extent of a garment shape (for standalone thumbnails). */
    public fun shapeBounds(shape: AvatarGarmentShape): Rect
}

/**
 * Logical width/height of the drawing space every source works in. The canvas
 * scales this box to fit while preserving the aspect.
 */
public const val AVATAR_VIRTUAL_WIDTH: Float = 200f
public const val AVATAR_VIRTUAL_HEIGHT: Float = 400f

/** Paper-doll renderer: stacks [manifest] layers through [layerSource]. */
@Composable
public fun AvatarCanvas(
    manifest: AvatarManifest,
    modifier: Modifier = Modifier,
    layerSource: AvatarLayerSource = VectorAvatarLayerSource,
    contentDescription: String? = null,
    testTag: String? = null,
) {
    val layers = remember(manifest) { manifest.resolvedLayers() }
    // Default accessibility description comes from resources (en/uk); the
    // caller may override it with a fully custom string.
    val description = contentDescription ?: stringResource(
        R.string.avatar_canvas_description,
        manifest.bodyType.id,
        manifest.skinTone.id,
    )
    var drawModifier = modifier
        .semantics { this.contentDescription = description }
    if (testTag != null) {
        drawModifier = drawModifier.testTag(testTag)
    }
    Canvas(modifier = drawModifier) {
        val scale = minOf(size.width / AVATAR_VIRTUAL_WIDTH, size.height / AVATAR_VIRTUAL_HEIGHT)
        val originX = (size.width - AVATAR_VIRTUAL_WIDTH * scale) / 2f
        val originY = (size.height - AVATAR_VIRTUAL_HEIGHT * scale) / 2f
        withTransform({
            translate(originX, originY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            for (layer in layers) {
                when (layer) {
                    is AvatarLayer.Body -> layerSource.drawBody(this, layer)
                    is AvatarLayer.Garment -> layerSource.drawGarment(this, layer)
                }
            }
        }
    }
}

/**
 * Internal metrics of the vector doll, per body type. All values are in the
 * 200x400 virtual space; the vertical skeleton (shoulders 84, waist 150,
 * hips 215, feet 372) is shared — body types differ in widths only.
 */
private data class DollMetrics(
    val shoulderHalf: Float,
    val waistHalf: Float,
    val hipHalf: Float,
    val legHalf: Float,
) {
    companion object {
        fun of(bodyType: AvatarBodyType): DollMetrics = when (bodyType) {
            AvatarBodyType.SLIM -> DollMetrics(36f, 26f, 34f, 10f)
            AvatarBodyType.REGULAR -> DollMetrics(40f, 31f, 42f, 12f)
            AvatarBodyType.CURVY -> DollMetrics(41f, 35f, 52f, 14f)
        }

        /** Template metrics for garment silhouettes (body-independent shapes). */
        val REGULAR_TEMPLATE: DollMetrics = of(AvatarBodyType.REGULAR)
    }

    val centerX: Float = 100f
    val shoulderY: Float = 84f
    val waistY: Float = 150f
    val hipY: Float = 215f
    val feetTopY: Float = 352f
    val feetBottomY: Float = 372f
    val armOuterHalf: Float = 16f
    val headCenterY: Float = 42f
    val headRadius: Float = 27f
}

/**
 * The shipped v0.1 source: flat minimal silhouettes built from paths, rounded
 * rectangles and circles — all authored for this product, no external assets.
 */
public object VectorAvatarLayerSource : AvatarLayerSource {

    private const val STROKE_WIDTH = 3f

    private fun outlineStroke() = Stroke(width = STROKE_WIDTH)

    private fun parsedColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

    override fun drawBody(scope: DrawScope, layer: AvatarLayer.Body) {
        val tone = AvatarSkinTone.fromId(layer.tone.id)
        val fill = parsedColor(tone.fillHex)
        val edge = parsedColor(tone.edgeHex)
        val edgeStroke = Stroke(width = 2.5f)
        val m = DollMetrics.of(layer.bodyType)
        val c = m.centerX

        // Legs (two straight columns from hips to feet).
        scope.drawLimb(m, isLeft = true, topY = m.hipY, bottomY = m.feetBottomY, fill = fill, edge = edge, edgeStroke = edgeStroke)
        scope.drawLimb(m, isLeft = false, topY = m.hipY, bottomY = m.feetBottomY, fill = fill, edge = edge, edgeStroke = edgeStroke)

        // Arms along the torso sides.
        for (isLeft in booleanArrayOf(true, false)) {
            val sign = if (isLeft) -1f else 1f
            val topLeft = Offset(c + sign * (m.shoulderHalf + 2f), 88f)
            val size = Size(2f * m.armOuterHalf, 128f)
            scope.drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = CornerRadius(8f, 8f))
            scope.drawRoundRect(
                color = edge,
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(8f, 8f),
                style = edgeStroke,
            )
        }

        // Torso: shoulders -> waist -> hips silhouette.
        scope.drawPath(torsoPath(m), color = fill)
        scope.drawPath(torsoPath(m), color = edge, style = edgeStroke)

        // Neck, then the head on top.
        scope.drawRoundRect(
            color = fill,
            topLeft = Offset(c - 10f, 56f),
            size = Size(20f, 32f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        scope.drawCircle(color = fill, radius = m.headRadius, center = Offset(c, m.headCenterY))
        scope.drawCircle(
            color = edge,
            radius = m.headRadius,
            center = Offset(c, m.headCenterY),
            style = edgeStroke,
        )
    }

    override fun drawGarment(scope: DrawScope, layer: AvatarLayer.Garment) {
        val m = DollMetrics.REGULAR_TEMPLATE
        val fill = parsedColor(layer.tintHex)
        val outline = parsedColor(layer.outlineHex)
        val stroke = Stroke(width = STROKE_WIDTH)
        val c = m.centerX
        when (layer.shape) {
            AvatarGarmentShape.TEE -> scope.drawSleevedTop(m, sleeveBottomY = 130f, fill = fill, outline = outline)

            AvatarGarmentShape.LONG_SLEEVE -> scope.drawSleevedTop(m, sleeveBottomY = 212f, fill = fill, outline = outline)

            AvatarGarmentShape.COAT -> {
                scope.drawSleevedTop(
                    m,
                    sleeveBottomY = 212f,
                    fill = fill,
                    outline = outline,
                    hemY = 285f,
                    widen = 8f,
                )
                // Open-front hint: a vertical center line.
                scope.drawLine(color = outline, start = Offset(c, 92f), end = Offset(c, 280f), strokeWidth = 2.5f)
            }

            AvatarGarmentShape.DRESS -> {
                val hemHalf = m.hipHalf + 28f
                val path = Path().apply {
                    moveTo(c - m.shoulderHalf, 80f)
                    lineTo(c + m.shoulderHalf, 80f)
                    lineTo(c + m.waistHalf, m.waistY)
                    lineTo(c + hemHalf, 320f)
                    lineTo(c - hemHalf, 320f)
                    lineTo(c - m.waistHalf, m.waistY)
                    close()
                }
                scope.drawPath(path, color = fill)
                scope.drawPath(path, color = outline, style = stroke)
            }

            AvatarGarmentShape.TROUSERS -> scope.drawTrouserLegs(m, legBottomY = 355f, fill = fill, outline = outline)
            AvatarGarmentShape.SHORTS -> scope.drawTrouserLegs(m, legBottomY = 290f, fill = fill, outline = outline)

            AvatarGarmentShape.SKIRT -> {
                val hemHalf = m.hipHalf + 24f
                val path = Path().apply {
                    moveTo(c - m.hipHalf - 4f, 202f)
                    lineTo(c + m.hipHalf + 4f, 202f)
                    lineTo(c + hemHalf, 300f)
                    lineTo(c - hemHalf, 300f)
                    close()
                }
                scope.drawPath(path, color = fill)
                scope.drawPath(path, color = outline, style = stroke)
            }

            AvatarGarmentShape.SHOES -> {
                scope.drawFoot(m, isLeft = true, topY = m.feetTopY, bottomY = m.feetBottomY, fill = fill, outline = outline)
                scope.drawFoot(m, isLeft = false, topY = m.feetTopY, bottomY = m.feetBottomY, fill = fill, outline = outline)
            }

            AvatarGarmentShape.BOOTS -> {
                scope.drawFoot(m, isLeft = true, topY = 305f, bottomY = m.feetBottomY, fill = fill, outline = outline)
                scope.drawFoot(m, isLeft = false, topY = 305f, bottomY = m.feetBottomY, fill = fill, outline = outline)
            }

            AvatarGarmentShape.HEELS -> {
                scope.drawFoot(m, isLeft = true, topY = 354f, bottomY = 370f, fill = fill, outline = outline)
                scope.drawFoot(m, isLeft = false, topY = 354f, bottomY = 370f, fill = fill, outline = outline)
                for (isLeft in booleanArrayOf(true, false)) {
                    val sign = if (isLeft) -1f else 1f
                    val heel = Path().apply {
                        moveTo(c + sign * 6f, 368f)
                        lineTo(c + sign * 16f, 368f)
                        lineTo(c + sign * 16f, 378f)
                        lineTo(c + sign * 10f, 378f)
                        close()
                    }
                    scope.drawPath(heel, color = fill)
                    scope.drawPath(heel, color = outline, style = stroke)
                }
            }

            AvatarGarmentShape.HAT -> {
                val brimTopLeft = Offset(c - 38f, 26f)
                val brimSize = Size(76f, 9f)
                scope.drawRoundRect(color = fill, topLeft = brimTopLeft, size = brimSize, cornerRadius = CornerRadius(4f, 4f))
                scope.drawRoundRect(color = outline, topLeft = brimTopLeft, size = brimSize, cornerRadius = CornerRadius(4f, 4f), style = stroke)
                scope.drawArc(
                    color = fill,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(c - 24f, 2f),
                    size = Size(48f, 40f),
                )
                scope.drawArc(
                    color = outline,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(c - 24f, 2f),
                    size = Size(48f, 40f),
                    style = stroke,
                )
            }

            AvatarGarmentShape.SCARF -> {
                scope.drawOutlinedRoundRect(Offset(c - 16f, 62f), Size(32f, 24f), fill, outline, stroke)
                scope.drawOutlinedRoundRect(Offset(c, 80f), Size(16f, 62f), fill, outline, stroke)
            }

            AvatarGarmentShape.BAG -> {
                scope.drawLine(
                    color = outline,
                    start = Offset(c + m.shoulderHalf - 6f, 92f),
                    end = Offset(c + 42f, 150f),
                    strokeWidth = 4f,
                )
                scope.drawOutlinedRoundRect(Offset(c + 26f, 148f), Size(34f, 44f), fill, outline, stroke)
            }

            AvatarGarmentShape.GLASSES -> {
                for (eyeX in floatArrayOf(c - 15f, c + 15f)) {
                    scope.drawCircle(color = outline, radius = 10f, center = Offset(eyeX, m.headCenterY), style = stroke)
                }
                scope.drawLine(
                    color = outline,
                    start = Offset(c - 5f, m.headCenterY),
                    end = Offset(c + 5f, m.headCenterY),
                    strokeWidth = STROKE_WIDTH,
                )
            }

            AvatarGarmentShape.BROOCH -> {
                scope.drawCircle(color = fill, radius = 7f, center = Offset(c, 108f))
                scope.drawCircle(color = outline, radius = 7f, center = Offset(c, 108f), style = stroke)
            }
        }
    }

    override fun shapeBounds(shape: AvatarGarmentShape): Rect {
        val m = DollMetrics.REGULAR_TEMPLATE
        val c = m.centerX
        return when (shape) {
            AvatarGarmentShape.TEE, AvatarGarmentShape.LONG_SLEEVE ->
                Rect(c - m.shoulderHalf - 18f, 78f, c + m.shoulderHalf + 18f, 220f)

            AvatarGarmentShape.COAT ->
                Rect(c - m.shoulderHalf - 26f, 78f, c + m.shoulderHalf + 26f, 285f)

            AvatarGarmentShape.DRESS ->
                Rect(c - m.hipHalf - 28f, 80f, c + m.hipHalf + 28f, 320f)

            AvatarGarmentShape.TROUSERS ->
                Rect(c - 2f * m.legHalf - 4f, 202f, c + 2f * m.legHalf + 4f, 355f)

            AvatarGarmentShape.SHORTS ->
                Rect(c - 2f * m.legHalf - 4f, 202f, c + 2f * m.legHalf + 4f, 290f)

            AvatarGarmentShape.SKIRT ->
                Rect(c - m.hipHalf - 24f, 202f, c + m.hipHalf + 24f, 300f)

            AvatarGarmentShape.SHOES ->
                Rect(c - 2f * m.legHalf - 12f, m.feetTopY, c + 2f * m.legHalf + 12f, m.feetBottomY)

            AvatarGarmentShape.BOOTS ->
                Rect(c - 2f * m.legHalf - 12f, 305f, c + 2f * m.legHalf + 12f, m.feetBottomY)

            AvatarGarmentShape.HEELS ->
                Rect(c - 2f * m.legHalf - 12f, 354f, c + 2f * m.legHalf + 12f, 378f)

            AvatarGarmentShape.HAT -> Rect(c - 38f, 2f, c + 38f, 35f)

            AvatarGarmentShape.SCARF -> Rect(c - 16f, 62f, c + 16f, 142f)

            AvatarGarmentShape.BAG -> Rect(c + 26f, 88f, c + 60f, 192f)

            AvatarGarmentShape.GLASSES ->
                Rect(c - 25f, m.headCenterY - 10f, c + 25f, m.headCenterY + 10f)

            AvatarGarmentShape.BROOCH -> Rect(c - 7f, 101f, c + 7f, 115f)
        }
    }

    // -- shape helpers (all coordinates virtual) -----------------------------

    private fun torsoPath(m: DollMetrics): Path = Path().apply {
        val c = m.centerX
        moveTo(c - m.shoulderHalf, m.shoulderY)
        lineTo(c + m.shoulderHalf, m.shoulderY)
        lineTo(c + m.waistHalf, m.waistY)
        lineTo(c + m.hipHalf, m.hipY)
        lineTo(c - m.hipHalf, m.hipY)
        lineTo(c - m.waistHalf, m.waistY)
        close()
    }

    private fun DrawScope.drawLimb(
        m: DollMetrics,
        isLeft: Boolean,
        topY: Float,
        bottomY: Float,
        fill: Color,
        edge: Color,
        edgeStroke: Stroke,
    ) {
        val sign = if (isLeft) -1f else 1f
        val center = m.centerX + sign * (m.legHalf + 3f)
        val topLeft = Offset(center - m.legHalf, topY)
        val size = Size(2f * m.legHalf, bottomY - topY)
        drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(
            color = edge,
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(4f, 4f),
            style = edgeStroke,
        )
    }

    private fun DrawScope.drawFoot(
        m: DollMetrics,
        isLeft: Boolean,
        topY: Float,
        bottomY: Float,
        fill: Color,
        outline: Color,
    ) {
        val sign = if (isLeft) -1f else 1f
        val width = 2f * m.legHalf + 8f
        val topLeft = Offset(m.centerX + sign * (m.legHalf + 3f) - width / 2f, topY)
        drawOutlinedRoundRect(topLeft, Size(width, bottomY - topY), fill, outline, outlineStroke())
    }

    /**
     * The top garment cover: a rounded rectangle slightly wider than the
     * shoulders plus two sleeve stubs of [sleeveBottomY] depth. Garment
     * silhouettes use the REGULAR metrics so they look the same on every
     * body type (the doll underneath changes, the clothes do not).
     */
    private fun DrawScope.drawSleevedTop(
        m: DollMetrics,
        sleeveBottomY: Float,
        fill: Color,
        outline: Color,
        hemY: Float = 220f,
        widen: Float = 4f,
    ) {
        val c = m.centerX
        drawOutlinedRoundRect(
            Offset(c - m.shoulderHalf - widen, 78f),
            Size(2f * (m.shoulderHalf + widen), hemY - 78f),
            fill,
            outline,
            outlineStroke(),
            cornerRadius = 10f,
        )
        for (isLeft in booleanArrayOf(true, false)) {
            val sign = if (isLeft) -1f else 1f
            drawOutlinedRoundRect(
                Offset(c + sign * (m.shoulderHalf - 2f), 82f),
                Size(2f * m.armOuterHalf, sleeveBottomY - 82f),
                fill,
                outline,
                outlineStroke(),
                cornerRadius = 8f,
            )
        }
    }

    /** Trouser-like cover: hip block plus two legs of [legBottomY] depth. */
    private fun DrawScope.drawTrouserLegs(
        m: DollMetrics,
        legBottomY: Float,
        fill: Color,
        outline: Color,
    ) {
        val c = m.centerX
        drawOutlinedRoundRect(
            Offset(c - m.hipHalf - 4f, 202f),
            Size(2f * (m.hipHalf + 4f), 42f),
            fill,
            outline,
            outlineStroke(),
            cornerRadius = 8f,
        )
        drawLimb(m, isLeft = true, topY = 238f, bottomY = legBottomY, fill = fill, edge = outline, edgeStroke = outlineStroke())
        drawLimb(m, isLeft = false, topY = 238f, bottomY = legBottomY, fill = fill, edge = outline, edgeStroke = outlineStroke())
    }

    private fun DrawScope.drawOutlinedRoundRect(
        topLeft: Offset,
        size: Size,
        fill: Color,
        outline: Color,
        stroke: Stroke,
        cornerRadius: Float = 6f,
    ) {
        val radius = CornerRadius(cornerRadius, cornerRadius)
        drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = radius)
        drawRoundRect(color = outline, topLeft = topLeft, size = size, cornerRadius = radius, style = stroke)
    }
}
