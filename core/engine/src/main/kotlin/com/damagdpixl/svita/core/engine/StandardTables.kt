package com.damagdpixl.svita.core.engine

/**
 * Functional data tables of the outfit engine v1.
 *
 * These are plain data (color names + sRGB hex values with precomputed OKLCH,
 * and garment subtype -> thermal class assignments). They are reproduced here
 * so the engine module and its tests are self-contained; the app wires the
 * same data from `:core:data` seeds. The OKLCH columns were precomputed once
 * with [ColorEntry.fromHex] and are pinned as constants so that color scoring
 * is bit-stable across platforms.
 */

/** The 24-color system palette, name -> hex + precomputed OKLCH (L, C, h deg). */
fun standardPalette(): Map<String, ColorEntry> = STANDARD_PALETTE

private val STANDARD_PALETTE: Map<String, ColorEntry> = linkedMapOf(
    "White" to ColorEntry("#FFFFFF", 1.000, 0.000, 89.9),
    "Sand" to ColorEntry("#C3B79A", 0.782, 0.042, 88.2),
    "SkyBlue" to ColorEntry("#3399DB", 0.655, 0.134, 241.9),
    "Mint" to ColorEntry("#61E0C7", 0.828, 0.119, 178.2),
    "Purple" to ColorEntry("#745EC5", 0.554, 0.154, 290.3),
    "Magenta" to ColorEntry("#B91F81", 0.532, 0.205, 347.9),
    "Black" to ColorEntry("#000000", 0.000, 0.000, 0.0),
    "Gray" to ColorEntry("#95A5A6", 0.710, 0.018, 201.3),
    "PowderBlue" to ColorEntry("#8497C6", 0.679, 0.073, 267.4),
    "Green" to ColorEntry("#2ECC71", 0.746, 0.181, 152.3),
    "Pink" to ColorEntry("#F47CC3", 0.743, 0.167, 345.4),
    "Maroon" to ColorEntry("#79302A", 0.411, 0.104, 27.2),
    "Blue" to ColorEntry("#3449BE", 0.464, 0.184, 269.8),
    "Red" to ColorEntry("#D71C0A", 0.561, 0.218, 30.2),
    "Tael" to ColorEntry("#3A6F81", 0.513, 0.063, 222.4),
    "ForestGreen" to ColorEntry("#345F41", 0.445, 0.069, 152.5),
    "Watermelon" to ColorEntry("#EF717A", 0.700, 0.155, 17.6),
    "Orange" to ColorEntry("#E77E23", 0.697, 0.160, 55.1),
    "Brown" to ColorEntry("#5E4433", 0.410, 0.045, 54.3),
    "Coffee" to ColorEntry("#A38671", 0.641, 0.047, 57.7),
    "Someblue" to ColorEntry("#58659F", 0.522, 0.093, 273.2),
    "Lime" to ColorEntry("#A5C63B", 0.777, 0.165, 122.0),
    "Plum" to ColorEntry("#5E335E", 0.392, 0.088, 327.1),
    "Yellow" to ColorEntry("#FFCD02", 0.867, 0.177, 90.8),
)

/** Subtype -> thermal class: female 118 + male 89 taxonomy subtypes (133 keys). */
internal val STANDARD_SUBTYPE_CLASS: Map<String, String> = buildMap {
    putClass("C1",
        "body.tank",
        "feet.sandals.flip-flops", "feet.sandals.pool shoes")
    putClass("C2",
        "head.hats.panama", "head.hats.boater",
        "body.dress.strapless", "body.t-shirt",
        "legs.shorts",
        "legs.jumpsuits.short dungaree", "legs.jumpsuits.short boilersuit",
        "feet.sandals.sandals", "feet.sandals.columbia",
        "feet.sandals.heel sandals", "feet.sandals.platform sandals",
        "feet.flats.espadrilles", "feet.shoes.espadrilles")
    putClass("C3",
        "body.top", "body.dress.mini", "body.dress.gored", "body.dress.shirt",
        "legs.skirt.tulip", "legs.skirt.gored", "legs.jumpsuits.skirt dungaree",
        "feet.sandals.clog", "feet.heels.wedge",
        "feet.flats.ballerina", "feet.flats.boat-shoes",
        "feet.sport.trainers with heel", "feet.sport.low trainers",
        "feet.sport.keds", "feet.sport.plimsolls", "feet.sport.slipons",
        "feet.shoes.boat shoes", "feet.shoes.moccasins")
    putClass("C4",
        "body.shirt",
        "legs.skirt.pencil",
        "legs.trousers.chino", "legs.trousers.sport", "legs.trousers.formal",
        "legs.jeans", "legs.leggins",
        "legs.jumpsuits.long dungaree", "legs.jumpsuits.long boilersuit",
        "feet.heels.kitten-heel", "feet.heels.classic", "feet.heels.platform",
        "feet.heels.chunky-heel", "feet.heels.heels without-heels",
        "feet.flats.loafer", "feet.flats.classic lace-up", "feet.flats.creepers",
        "feet.sport.high trainers",
        "feet.classic shoes.classic lace-up", "feet.classic shoes.brogues",
        "feet.classic shoes.oxford", "feet.classic shoes.loafer",
        "feet.shoes.creepers")
    putClass("C5",
        "body.sweatshirt.sweats", "body.sweatshirt.sweats-with-zip",
        "body.sweatshirt.hoody", "body.sweatshirt.hoody-with-zip",
        "body.cardigan", "body.sweater", "body.gilet.sweater")
    putClass("C6",
        "body.blazer", "body.blazer.single breasted", "body.blazer.double breasted",
        "body.gilet.denim", "body.gilet.blazer", "body.gilet.jacket",
        "body.jacket.denim", "body.jacket.sport", "body.jacket.anorak",
        "body.jacket.classic", "body.jacket.moto", "body.jacket.punk",
        "body.jacket.varcity", "body.jacket.field",
        "body.coat.capes", "body.coat.raincoat")
    putClass("C7",
        "body.gilet.fur", "body.jacket.alaska", "body.jacket.parka",
        "body.coat.pea", "body.coat.chesterfield", "body.coat.duffle",
        "body.coat.military", "body.coat.monument")
    putClass("C8", "body.fur")
    putClass("DA",
        "head.hats.cap", "head.hats.beret", "head.hats.flat cap",
        "head.hats.pillbox", "head.hats.trilby-hat", "head.hats.boy-hat",
        "head.scarfs.babushka", "head.scarfs.classic")
    putClass("WA",
        "head.hats.beanie", "head.hats.peruvian-hat", "head.hats.ushanka",
        "head.scarfs.snood", "accessory.gloves")
    putClass("DB",
        "feet.boots.desert-chukka", "feet.boots.combat", "feet.boots.chelsea",
        "feet.boots.cowboy", "feet.boots.hiking", "feet.boots.moto",
        "feet.boots.classic heel boots", "feet.boots.hard life boots",
        "feet.boots.wedge boots", "feet.hight boots.riding boots")
    putClass("WB",
        "feet.boots.ugg", "feet.hight boots.foldover",
        "feet.hight boots.knee-highboots")
    putClass("N",
        "accessory.glasses", "accessory.earrings", "accessory.necklace",
        "accessory.ring", "accessory.bracelet", "accessory.watch",
        "accessory.belt", "accessory.umbrella",
        "accessory.bow-tie", "accessory.tie",
        "bag.wallet", "bag.bag", "bag.clutch", "bag.cotton_bag",
        "bag.backpack", "bag.sport_bag", "bag.travel_bag", "bag.paper_bag")
}

private fun MutableMap<String, String>.putClass(clazz: String, vararg subtypes: String) {
    for (subtype in subtypes) put(subtype, clazz)
}
