package com.damagdpixl.svita.core.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit self-checks of the engine formulas and deterministic helpers, mirroring
 * the behavioral checks of the reference matrix test: thermal windows with the
 * documented cold-side interpretation, CLO model, color categories and pair
 * harmony, style fallbacks, crc32-based look ids, exact decimal rounding, and
 * the informational-only comfort delta.
 */
class OutfitEngineSelfCheckTest {

    private val engine = OutfitEngine(standardPalette(), ThermalMapping.standard())

    private fun garment(id: Long, subtype: String, color: String? = null, vararg styles: String) =
        EngineGarment(id, subtype, listOfNotNull(color), styles.toList())

    /** The fixed 25-garment test wardrobe carried by the parity fixtures. */
    private fun testWardrobe(): List<EngineGarment> = listOf(
        garment(101, "body.tank", "White", "casual", "sport"),
        garment(102, "body.top", "SkyBlue", "casual", "sport"),
        garment(103, "body.shirt", "White", "business", "classic"),
        garment(104, "body.sweatshirt.hoody", "Gray", "casual", "sport"),
        garment(105, "body.sweater", "Gray", "casual", "classic"),
        garment(107, "body.dress.strapless", "Watermelon", "cocktail"),
        garment(109, "body.jacket.denim", "Blue", "casual", "sport"),
        garment(110, "body.jacket.parka", "ForestGreen", "casual", "sport"),
        garment(111, "body.coat.chesterfield", "Black", "business", "classic"),
        garment(112, "body.fur", "Black", "casual", "cocktail"),
        garment(113, "legs.shorts", "SkyBlue", "casual", "sport"),
        garment(114, "legs.jeans", "Blue", "casual", "sport"),
        garment(115, "legs.skirt.pencil", "Black", "business", "classic"),
        garment(116, "legs.capri", "White", "casual", "sport"), // unknown subtype -> class N
        garment(117, "feet.sandals.sandals", "Brown", "casual"),
        garment(118, "feet.flats.ballerina", "Black", "casual", "business"),
        garment(119, "feet.sport.low trainers", "White", "casual", "sport"),
        garment(120, "feet.boots.chelsea", "Brown", "casual", "classic"),
        garment(121, "feet.boots.ugg", "Black", "casual"),
        garment(122, "feet.sandals.flip-flops", "Yellow", "casual"),
        garment(123, "head.hats.panama", "Sand", "casual"),
        garment(124, "head.hats.beanie", "Maroon", "casual", "sport"),
        garment(125, "head.scarfs.snood", "Gray", "casual"),
        garment(126, "accessory.gloves", "Black", "casual", "sport"),
        garment(128, "bag.backpack", "ForestGreen", "casual", "sport"),
    )

    // ------------------------------------------------------------------
    // Data tables
    // ------------------------------------------------------------------

    @Test
    fun `standard tables have reference sizes`() {
        assertEquals(24, standardPalette().size)
        assertEquals(133, ThermalMapping.standard().subtypes.size)
    }

    @Test
    fun `palette OKLCH recompute matches pinned table`() {
        for ((name, entry) in standardPalette()) {
            val computed = ColorEntry.fromHex(entry.hex)
            assertTrue(abs(computed.l - entry.l) <= 0.004, "$name L ${computed.l} vs ${entry.l}")
            assertTrue(abs(computed.c - entry.c) <= 0.004, "$name C ${computed.c} vs ${entry.c}")
            if (entry.c >= 0.02) {
                val dh = abs(computed.h - entry.h).let { minOf(it, 360.0 - it) }
                assertTrue(dh <= 0.6, "$name h ${computed.h} vs ${entry.h}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Thermal windows (D1), CLO model
    // ------------------------------------------------------------------

    @Test
    fun `thermal windows prune absurd combinations only`() {
        // hot side: full pruning for every class
        assertTrue(!engine.inWindow("feet.sandals.sandals", -10.0)) // C3 sandals in frost
        assertTrue(!engine.inWindow("body.jacket.parka", 30.0)) // C7 parka in heat
        assertTrue(!engine.inWindow("body.fur", 21.0))
        // cold side: only summer classes C1/C2/C3 are pruned
        assertTrue(engine.inWindow("body.sweater", -5.0)) // C5 survives under a coat
        assertTrue(engine.inWindow("body.fur", 20.0))
        assertTrue(engine.inWindow("body.coat.chesterfield", -30.0))
        assertTrue(engine.inWindow("head.scarfs.snood", -30.0)) // WA
        assertTrue(engine.inWindow("feet.boots.ugg", -25.0)) // WB
        // neutral fallback has the widest window
        assertTrue(engine.inWindow("legs.capri", 45.0))
        assertTrue(engine.inWindow("legs.capri", -30.0))
        // bare class names are accepted too
        assertTrue(engine.inWindow("C4", 0.0))
        assertTrue(!engine.inWindow("C1", 0.0))
    }

    @Test
    fun `clo model matches reference formulas`() {
        assertEquals(0.0, engine.cloReq(26.0))
        assertEquals(2.0, engine.cloReq(-4.0))
        assertEquals(2.0, engine.cloReq(-40.0))
        assertEquals(0.0, engine.cloReq(40.0))
        assertEquals(16.0 / 15.0, engine.cloReq(10.0))
        // tolerance-based like the reference self-checks (ULP noise allowed)
        assertTrue(abs(engine.warmFit(1.04, 16.0 / 15.0) - 0.9666666666666666) < 1e-9)
        assertTrue(abs(engine.warmFit(0.22, 0.0) - 0.725) < 1e-9)
        assertEquals(1.0, engine.warmFit(1.04, 1.04))
        assertEquals(0.0, engine.warmFit(0.0, 2.0))
    }

    @Test
    fun `roles derive from subtype sections`() {
        assertEquals("dress", engine.roleOf("body.dress.mini"))
        assertEquals("outer", engine.roleOf("body.jacket.denim"))
        assertEquals("outer", engine.roleOf("body.coat.pea"))
        assertEquals("outer", engine.roleOf("body.cardigan"))
        assertEquals("outer", engine.roleOf("body.fur"))
        assertEquals("body", engine.roleOf("body.t-shirt"))
        assertEquals("legs", engine.roleOf("legs.jeans"))
        assertEquals("feet", engine.roleOf("feet.boots.chelsea"))
        assertEquals("hat", engine.roleOf("head.hats.beanie"))
        assertEquals("scarf", engine.roleOf("head.scarfs.snood"))
        assertEquals("accessory", engine.roleOf("accessory.gloves"))
        assertEquals("bag", engine.roleOf("bag.backpack"))
        assertEquals("legs", engine.roleOf("legs.capri")) // unknown subtype still has a section role
        assertEquals(null, engine.roleOf("head.bandana")) // unknown section -> no role
        assertEquals(null, engine.roleOf("misc.thing"))
    }

    @Test
    fun `outer mode by temperature`() {
        assertEquals(OuterMode.FORBIDDEN, engine.outerMode(22.1))
        assertEquals(OuterMode.FORBIDDEN, engine.outerMode(45.0))
        assertEquals(OuterMode.REQUIRED, engine.outerMode(12.0))
        assertEquals(OuterMode.REQUIRED, engine.outerMode(-30.0))
        assertEquals(OuterMode.OPTIONAL, engine.outerMode(12.1))
        assertEquals(OuterMode.OPTIONAL, engine.outerMode(22.0))
    }

    // ------------------------------------------------------------------
    // Color harmony
    // ------------------------------------------------------------------

    @Test
    fun `color categories by chroma thresholds`() {
        assertEquals("neutral", engine.colorCategory(null))
        assertEquals("neutral", engine.colorCategory("NotAColor"))
        assertEquals("neutral", engine.colorCategory("White"))
        assertEquals("neutral", engine.colorCategory("Black"))
        assertEquals("neutral", engine.colorCategory("Gray"))
        assertEquals("neutral", engine.colorCategory("Sand")) // 0.042
        assertEquals("muted", engine.colorCategory("Tael")) // 0.063
        assertEquals("muted", engine.colorCategory("Maroon")) // 0.104
        assertEquals("bright", engine.colorCategory("Red")) // 0.218
    }

    @Test
    fun `pair harmony reference examples`() {
        assertEquals(0.95, engine.pairScore("Black", "White"))
        assertEquals(1.00, engine.pairScore("Red", "Orange")) // dh ~24.9 analogous
        assertEquals(0.90, engine.pairScore("SkyBlue", "Orange")) // dh ~173 complementary
        assertEquals(0.25, engine.pairScore("Red", "Blue")) // dh ~120 clash
        assertEquals(0.85, engine.pairScore("Red", "Maroon")) // dh small
        assertEquals(0.95, engine.pairScore(null, "Red"))
    }

    @Test
    fun `color score reference examples`() {
        fun cg(color: String?) = EngineGarment(0, "body.tank", listOfNotNull(color), emptyList())
        // tolerance-based like the reference self-checks (ULP noise allowed)
        assertTrue(abs(engine.colorScore(listOf(cg("Black"), cg("White"), cg("Red"))) - 0.95) < 1e-9)
        assertTrue(abs(engine.colorScore(listOf(cg("Green"), cg("Yellow"), cg("SkyBlue"))) - 0.34) < 1e-6)
        // two bright accents at dh ~120: 0.6 x 0.25 x 0.95
        assertTrue(abs(engine.colorScore(listOf(cg("Red"), cg("Blue"))) - 0.1425) < 1e-9)
        // neutral pairs score 0.95 each -> mean 0.95
        assertTrue(abs(engine.colorScore(listOf(cg("Black"), cg("White"))) - 0.95) < 1e-9)
        // single unknown-color garment: base 0.9, no pairs
        assertTrue(abs(engine.colorScore(listOf(cg(null))) - 0.9) < 1e-9)
    }

    // ------------------------------------------------------------------
    // Style tags
    // ------------------------------------------------------------------

    @Test
    fun `style intersection and fallbacks`() {
        val a = garment(1, "body.tank", "White", "casual", "sport")
        val b = garment(2, "legs.jeans", "Blue", "sport", "business")
        val b2 = garment(4, "legs.jeans", "Blue", "classic")
        val noStyle = garment(3, "feet.boots.chelsea", "Brown")
        assertEquals(setOf("sport"), engine.styleCommon(listOf(a, b)))
        // garments with empty styles do not participate in the intersection
        assertEquals(setOf("casual", "sport"), engine.styleCommon(listOf(a, noStyle)))
        assertEquals(emptySet(), engine.styleCommon(listOf(a, b2)))
        assertEquals(1.0, engine.styleScore(listOf(a, b), setOf("sport")))
        assertEquals(0.0, engine.styleScore(listOf(a, b), setOf("business")))
        assertEquals(1.0, engine.styleScore(listOf(a, noStyle), null))
        assertEquals(0.6, engine.styleScore(listOf(noStyle), null))
        assertEquals(listOf("sport"), engine.lookStyle(listOf(a, b), setOf("sport", "casual")))
        assertEquals(listOf("casual"), engine.lookStyle(listOf(noStyle), null))
    }

    // ------------------------------------------------------------------
    // Deterministic helpers
    // ------------------------------------------------------------------

    @Test
    fun `crc32 matches the reference algorithm`() {
        assertEquals(0L, crc32(ByteArray(0)))
        assertEquals(3421780262L, crc32("123456789".toByteArray(Charsets.UTF_8))) // 0xCBF43926
        assertEquals(3904355907L, crc32("a".toByteArray(Charsets.UTF_8))) // 0xE8B7BE43
        assertEquals(1095738169L, crc32("The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `neumaier sum matches the reference built-in sum`() {
        // naive accumulation gives 5.7 here; the reference runtime's sum() gives 5.699999999999999
        assertEquals(5.699999999999999, neumaierSum(List(6) { 0.95 }))
        assertEquals(2.8499999999999996, neumaierSum(listOf(0.95, 0.95, 0.95)))
        assertEquals(0.19, neumaierSum(listOf(0.05, 0.05, 0.09)))
        assertEquals(0.0, neumaierSum(emptyList()))
        assertEquals(4.75, neumaierSum(listOf(0.25, 0.25, 0.25, 0.25, 0.5, 3.25)))
    }

    @Test
    fun `look id is order independent and string sorted`() {
        assertEquals(427977029L, engine.lookId(listOf(12, 7, 30)))
        assertEquals(engine.lookId(listOf(30, 12, 7)), engine.lookId(listOf(12, 7, 30)))
        assertEquals(488733617L, engine.lookId(listOf(101, 102, 117)))
        assertEquals(1065965161L, engine.lookId(listOf(101, 113, 117, 109, 123, 125, 128, 126)))
        // string sort: "1011" < "102" — distinct from numeric sort
        assertEquals(engine.lookId(listOf(102, 1011)), engine.lookId(listOf(1011, 102)))
        val masked = engine.lookId(listOf(1, 2, 3))
        assertTrue(masked in 0..0x7FFFFFFFL)
    }

    @Test
    fun `roundHalfEven4 is exact decimal rounding`() {
        assertEquals(0.1235, roundHalfEven4(0.12345)) // binary value sits above the tie
        assertEquals(0.0003, roundHalfEven4(0.00025)) // above the tie
        assertEquals(0.0001, roundHalfEven4(0.00015)) // below the tie
        assertEquals(0.0001, roundHalfEven4(5e-05))
        assertEquals(-0.0001, roundHalfEven4(-5e-05))
        assertEquals(0.9667, roundHalfEven4(0.9666666666666666))
        assertEquals(1.0001, roundHalfEven4(1.00005))
        assertEquals(1.0, roundHalfEven4(0.99995))
        assertEquals(0.15, roundHalfEven4(0.15))
        assertEquals(0.5, roundHalfEven4(0.5))
        assertEquals(2.675, roundHalfEven4(2.675))
        // negative zero propagates like the reference decimal rounding
        val nz = roundHalfEven4(-1e-9)
        assertTrue(nz == 0.0 && (1.0 / nz) == Double.NEGATIVE_INFINITY)
        // exact binary ties (odd multiples of 1/32 scale to n.5) round to even
        assertEquals(0.0312, roundHalfEven4(0.03125)) // 312.5 -> 312
        assertEquals(0.0938, roundHalfEven4(0.09375)) // 937.5 -> 938
    }

    // ------------------------------------------------------------------
    // Request-level behaviors
    // ------------------------------------------------------------------

    @Test
    fun `comfort delta is informational in v1`() {
        val w = testWardrobe()
        val withDelta = engine.looks(w, 5.0, comfortDelta = -15.0)
        val withoutDelta = engine.looks(w, 5.0, comfortDelta = 0.0)
        assertEquals(withoutDelta, withDelta)
    }

    @Test
    fun `temperature is clamped to the working range`() {
        val w = testWardrobe()
        assertEquals(engine.looks(w, -30.0), engine.looks(w, -100.0))
        assertEquals(engine.looks(w, 45.0), engine.looks(w, 100.0))
    }

    @Test
    fun `exclude recent is a hard pool exclusion`() {
        val w = testWardrobe()
        val allFeet = setOf(117L, 118L, 119L, 120L, 121L, 122L)
        val empty = engine.looks(w, 20.0, excludeRecent = allFeet)
        assertEquals(emptyList<EngineLook>(), empty.looks)
        val noJeans = engine.looks(w, 20.0, excludeRecent = setOf(114))
        assertTrue(noJeans.looks.isNotEmpty())
        assertTrue(noJeans.looks.all { look -> 114L !in look.garmentIds })
    }

    @Test
    fun `top-n response size is capped at five`() {
        assertEquals(5, engine.looks(testWardrobe(), 15.0).looks.size)
    }
}
