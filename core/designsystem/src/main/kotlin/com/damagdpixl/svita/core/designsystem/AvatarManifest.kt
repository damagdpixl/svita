package com.damagdpixl.svita.core.designsystem

/**
 * Manifest-driven paper-doll avatar (P2 T5).
 *
 * The renderer consumes a plain data [AvatarManifest]: body type, skin tone and
 * a list of garment slots to draw. All styling decisions that need to be
 * testable WITHOUT a graphics surface live here:
 * - stack ordering (`resolvedLayers`): body base first, then
 *   body -> legs -> dress -> outer -> feet -> accessories (hat, scarf, bag,
 *   accessory). A DRESS slot REPLACES the body-top and legs garments — the
 *   dress silhouette covers both zones, so drawing a top/trousers under it
 *   would leak color at the waistline;
 * - tint resolution: each garment carries its primary color as an `#RRGGBB`
 *   hex (the 24-color system palette); malformed values fall back to a neutral
 *   gray instead of crashing the renderer;
 * - outline choice: every tint gets a contrast-safe neutral outline
 *   (dark charcoal on light tints, cream on dark tints).
 *
 * This file is deliberately free of Compose drawing code — only `Color`-free
 * string math — so the ordering and tint contracts are plain JVM tests.
 */

/**
 * Garment slots of the paper doll, mirroring the taxonomy sections
 * (v1 `section` column). [zOrder] ascending = drawn earlier = underneath.
 */
public enum class AvatarSlot(public val zOrder: Int) {
    BODY(0),
    LEGS(10),
    DRESS(20),
    OUTER(30),
    FEET(40),
    HAT(50),
    SCARF(60),
    BAG(70),
    ACCESSORY(80),
}

/** The three selectable silhouettes (Settings -> Avatar). */
public enum class AvatarBodyType(public val id: String) {
    SLIM("slim"),
    REGULAR("regular"),
    CURVY("curvy"),
    ;

    public companion object {
        /** Unknown or missing stored value falls back to the neutral middle. */
        public fun fromId(value: String?): AvatarBodyType =
            entries.firstOrNull { it.id == value } ?: REGULAR
    }
}

/** The three selectable skin tones: own flat fills with a darker edge. */
public enum class AvatarSkinTone(
    public val id: String,
    public val fillHex: String,
    public val edgeHex: String,
) {
    LIGHT("light", "#F2C9A7", "#C99B72"),
    MEDIUM("medium", "#C68B59", "#96683F"),
    DEEP("deep", "#8D5A3B", "#63402A"),
    ;

    public companion object {
        /** Unknown or missing stored value falls back to the first tone. */
        public fun fromId(value: String?): AvatarSkinTone =
            entries.firstOrNull { it.id == value } ?: LIGHT
    }
}

/**
 * Stylized vector template drawn for one garment slot. One slot maps to one
 * default shape; some slots branch on the garment kind (skirt vs trousers,
 * boots vs heels, glasses vs generic brooch).
 */
public enum class AvatarGarmentShape {
    TEE,
    LONG_SLEEVE,
    TROUSERS,
    SHORTS,
    SKIRT,
    DRESS,
    COAT,
    SHOES,
    BOOTS,
    HEELS,
    HAT,
    SCARF,
    BAG,
    GLASSES,
    BROOCH,
}

/** One garment to place on the doll. [tintHex] may be null or malformed. */
public data class AvatarGarment(
    public val garmentId: Long,
    public val slot: AvatarSlot,
    public val shape: AvatarGarmentShape,
    public val tintHex: String? = null,
)

/** One drawable stack entry: the doll body or a resolved (tinted) garment. */
public sealed interface AvatarLayer {
    public data class Body(
        val bodyType: AvatarBodyType,
        val tone: AvatarSkinTone,
    ) : AvatarLayer

    public data class Garment(
        val garmentId: Long,
        val slot: AvatarSlot,
        val shape: AvatarGarmentShape,
        val tintHex: String,
        val outlineHex: String,
    ) : AvatarLayer
}

/** What the renderer draws: a doll configuration, no repository knowledge. */
public data class AvatarManifest(
    val bodyType: AvatarBodyType = AvatarBodyType.REGULAR,
    val skinTone: AvatarSkinTone = AvatarSkinTone.LIGHT,
    val garments: List<AvatarGarment> = emptyList(),
)

/**
 * Resolves the manifest into the draw stack.
 *
 * Rules (pinned by AvatarManifestTest):
 * 1. the body layer is always first (the doll itself never disappears);
 * 2. a DRESS garment removes BODY and LEGS garments from the stack;
 * 3. remaining garments sort by slot z-order — body, legs, dress, outer,
 *    feet, then the accessory band (hat, scarf, bag, accessory topmost);
 * 4. garments sharing a slot keep a stable ascending [AvatarGarment.garmentId]
 *    order;
 * 5. every tint is normalized and paired with a contrast-safe outline.
 */
public fun AvatarManifest.resolvedLayers(): List<AvatarLayer> {
    val hasDress = garments.any { it.slot == AvatarSlot.DRESS }
    val visible = if (hasDress) {
        garments.filter { it.slot != AvatarSlot.BODY && it.slot != AvatarSlot.LEGS }
    } else {
        garments
    }
    return listOf(AvatarLayer.Body(bodyType, skinTone)) +
        visible
            .sortedWith(compareBy({ it.slot.zOrder }, { it.garmentId }))
            .map { garment ->
                val tint = AvatarTint.normalized(garment.tintHex)
                AvatarLayer.Garment(
                    garmentId = garment.garmentId,
                    slot = garment.slot,
                    shape = garment.shape,
                    tintHex = tint,
                    outlineHex = AvatarTint.outlineFor(tint),
                )
            }
}

/**
 * Color math for garment tints and outlines. Pure string/sRGB arithmetic so
 * it is verifiable on the JVM without a graphics stack.
 */
public object AvatarTint {

    /** Neutral gray used when a garment has no parseable color. */
    public const val FALLBACK_HEX: String = "#95A5A6"

    /** Outline for light tints (WCAG relative luminance >= [LIGHT_THRESHOLD]). */
    public const val OUTLINE_DARK: String = "#111111"

    /** Outline for dark tints. */
    public const val OUTLINE_LIGHT: String = "#F0EEE6"

    /** At or above this relative luminance a tint counts as light. */
    public const val LIGHT_THRESHOLD: Double = 0.40

    /**
     * Normalizes a color value to uppercase `#RRGGBB`. Accepts the exact
     * 6-digit form (with or without `#`); anything else — palette keys,
     * 3-digit hex, garbage — yields [FALLBACK_HEX] rather than a crash.
     */
    public fun normalized(hex: String?): String {
        if (hex == null) return FALLBACK_HEX
        val digits = hex.trim().removePrefix("#")
        if (digits.length != 6 || digits.any { Character.digit(it, 16) < 0 }) {
            return FALLBACK_HEX
        }
        return "#${digits.uppercase()}"
    }

    /** WCAG relative luminance of an `#RRGGBB` value in [0, 1]. */
    public fun relativeLuminance(normalizedHex: String): Double {
        val digits = normalizedHex.removePrefix("#")
        val r = linearChannel(digits.substring(0, 2))
        val g = linearChannel(digits.substring(2, 4))
        val b = linearChannel(digits.substring(4, 6))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Neutral outline that stays visible on top of [normalizedHex]: dark
     * charcoal on light tints, cream on dark ones.
     */
    public fun outlineFor(normalizedHex: String): String =
        if (relativeLuminance(normalizedHex) >= LIGHT_THRESHOLD) OUTLINE_DARK else OUTLINE_LIGHT

    private fun linearChannel(pair: String): Double {
        val srgb = Integer.parseInt(pair, 16) / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else Math.pow((srgb + 0.055) / 1.055, 2.4)
    }
}
