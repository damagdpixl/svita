package com.damagdpixl.svita.ui.avatar

import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.designsystem.AvatarBodyType
import com.damagdpixl.svita.core.designsystem.AvatarGarment
import com.damagdpixl.svita.core.designsystem.AvatarGarmentShape
import com.damagdpixl.svita.core.designsystem.AvatarManifest
import com.damagdpixl.svita.core.designsystem.AvatarSkinTone
import com.damagdpixl.svita.core.designsystem.AvatarSlot
import com.damagdpixl.svita.core.designsystem.AvatarTint
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Section

/**
 * Wardrobe -> paper-doll bridge (P2 T5): turns item aggregates into
 * [AvatarGarment]s and assembles an [AvatarManifest].
 *
 * Mapping contract:
 * - slot: the item's category section is authoritative (custom categories may
 *   point anywhere); only when the category row is missing does the subtype
 *   key prefix act as a fallback;
 * - shape: a stylized template branched off the seeded subtype key
 *   (skirt/shorts vs trousers, boots/heels vs shoes, sweaters get long
 *   sleeves, glasses render on the face, everything else is the slot default);
 * - tint: the FIRST COLOR-type attribute value of the item is the primary
 *   color. Values may be a palette key (resolved through [paletteHexByKey])
 *   or a raw `#RRGGBB`; anything unparseable yields a null tint and the
 *   renderer falls back to its neutral gray.
 */
object AvatarMapper {

    /** Aggregates one wardrobe item into the drawable garment. */
    fun garmentFor(
        aggregate: ItemAggregate,
        colorDefinitionIds: Set<Long>,
        paletteHexByKey: Map<String, String> = emptyMap(),
    ): AvatarGarment {
        val slot = slotOf(aggregate)
        return AvatarGarment(
            garmentId = aggregate.item.id,
            slot = slot,
            shape = shapeFor(aggregate.subtype.key, slot),
            tintHex = primaryTintOf(aggregate, colorDefinitionIds, paletteHexByKey),
        )
    }

    /** Full manifest for an outfit's items, in the given order. */
    fun manifestFor(
        aggregates: List<ItemAggregate>,
        definitions: List<AttributeDefinition>,
        paletteHexByKey: Map<String, String> = emptyMap(),
        bodyType: AvatarBodyType = AvatarBodyType.REGULAR,
        skinTone: AvatarSkinTone = AvatarSkinTone.LIGHT,
    ): AvatarManifest {
        val colorDefinitionIds = definitions
            .filter { it.type == AttributeType.COLOR }
            .map { it.id }
            .toSet()
        return AvatarManifest(
            bodyType = bodyType,
            skinTone = skinTone,
            garments = aggregates.map { garmentFor(it, colorDefinitionIds, paletteHexByKey) },
        )
    }

    // -- slot ----------------------------------------------------------------

    private fun slotOf(aggregate: ItemAggregate): AvatarSlot {
        aggregate.category?.let { return sectionToSlot(it.section) }
        return fallbackSlot(aggregate.subtype.key)
    }

    private fun sectionToSlot(section: Section): AvatarSlot = when (section) {
        Section.BODY -> AvatarSlot.BODY
        Section.LEGS -> AvatarSlot.LEGS
        Section.FEET -> AvatarSlot.FEET
        Section.DRESS -> AvatarSlot.DRESS
        Section.OUTER -> AvatarSlot.OUTER
        Section.ACCESSORY -> AvatarSlot.ACCESSORY
        Section.HAT -> AvatarSlot.HAT
        Section.SCARF -> AvatarSlot.SCARF
        Section.BAG -> AvatarSlot.BAG
    }

    /**
     * Key-prefix fallback for aggregates without a category row. Mirrors the
     * seeded key scheme: dress/outer garments are keyed `body.*` but must not
     * land in the body slot.
     */
    private fun fallbackSlot(subtypeKey: String): AvatarSlot = when {
        subtypeKey.startsWith("body.dress") -> AvatarSlot.DRESS
        OUTER_KEY_PREFIXES.any { subtypeKey.startsWith(it) } -> AvatarSlot.OUTER
        subtypeKey.startsWith("legs.") -> AvatarSlot.LEGS
        subtypeKey.startsWith("feet.") -> AvatarSlot.FEET
        subtypeKey.startsWith("head.hats") -> AvatarSlot.HAT
        subtypeKey.startsWith("head.scarfs") -> AvatarSlot.SCARF
        subtypeKey.startsWith("bag.") -> AvatarSlot.BAG
        subtypeKey.startsWith("accessory.") -> AvatarSlot.ACCESSORY
        else -> AvatarSlot.BODY
    }

    private val OUTER_KEY_PREFIXES = listOf(
        "body.blazer", "body.jacket", "body.coat", "body.gilet", "body.fur",
    )

    // -- shape ---------------------------------------------------------------

    private fun shapeFor(subtypeKey: String, slot: AvatarSlot): AvatarGarmentShape = when (slot) {
        AvatarSlot.BODY -> if (LONG_SLEEVE_KEY_PREFIXES.any { subtypeKey.startsWith(it) }) {
            AvatarGarmentShape.LONG_SLEEVE
        } else {
            AvatarGarmentShape.TEE
        }

        AvatarSlot.LEGS -> when {
            subtypeKey.startsWith("legs.skirt") ||
                subtypeKey.startsWith("legs.jumpsuits.skirt") -> AvatarGarmentShape.SKIRT

            subtypeKey.startsWith("legs.shorts") ||
                subtypeKey.startsWith("legs.jumpsuits.short") -> AvatarGarmentShape.SHORTS

            else -> AvatarGarmentShape.TROUSERS
        }

        AvatarSlot.DRESS -> AvatarGarmentShape.DRESS
        AvatarSlot.OUTER -> AvatarGarmentShape.COAT

        AvatarSlot.FEET -> when {
            subtypeKey.startsWith("feet.boots") ||
                subtypeKey.startsWith("feet.hight boots") -> AvatarGarmentShape.BOOTS

            subtypeKey.startsWith("feet.heels") -> AvatarGarmentShape.HEELS

            else -> AvatarGarmentShape.SHOES
        }

        AvatarSlot.HAT -> AvatarGarmentShape.HAT
        AvatarSlot.SCARF -> AvatarGarmentShape.SCARF
        AvatarSlot.BAG -> AvatarGarmentShape.BAG
        AvatarSlot.ACCESSORY -> if (subtypeKey == "accessory.glasses") {
            AvatarGarmentShape.GLASSES
        } else {
            AvatarGarmentShape.BROOCH
        }
    }

    private val LONG_SLEEVE_KEY_PREFIXES = listOf(
        "body.sweater", "body.sweatshirt", "body.shirt",
    )

    // -- tint ----------------------------------------------------------------

    private val HEX = Regex("^[0-9a-fA-F]{6}$")

    private fun primaryTintOf(
        aggregate: ItemAggregate,
        colorDefinitionIds: Set<Long>,
        paletteHexByKey: Map<String, String>,
    ): String? = aggregate.attributes
        .firstOrNull { it.definitionId in colorDefinitionIds }
        ?.let { entry -> resolveTint(entry.value, paletteHexByKey) }

    private fun resolveTint(raw: String?, paletteHexByKey: Map<String, String>): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        // Palette keys (e.g. "SkyBlue") resolve through the color table.
        paletteHexByKey[value.lowercase()]?.let { return AvatarTint.normalized(it) }
        return if (HEX.matches(value.removePrefix("#"))) AvatarTint.normalized(value) else null
    }
}
