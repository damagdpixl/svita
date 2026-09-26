package com.damagdpixl.svita.core.model

/**
 * Wardrobe sections (v1 schema `section` / `slot` columns).
 * Values mirror the storage encoding: body | legs | feet | dress | outer | accessory.
 */
enum class Section(val db: String) {
    BODY("body"),
    LEGS("legs"),
    FEET("feet"),
    DRESS("dress"),
    OUTER("outer"),
    ACCESSORY("accessory");

    companion object {
        fun fromDb(value: String): Section? = entries.firstOrNull { it.db == value }
    }
}

/**
 * Suitability of a garment to a season, stored as a 4-bit mask:
 * 1 = spring, 2 = summer, 4 = autumn, 8 = winter (all four = 15).
 */
enum class Season(val bit: Int) {
    SPRING(1),
    SUMMER(2),
    AUTUMN(4),
    WINTER(8);

    companion object {
        fun fromBitmask(mask: Int): Set<Season> =
            entries.filter { mask and it.bit != 0 }.toSet()
    }
}

fun Set<Season>.toBitmask(): Int = fold(0) { acc, season -> acc or season.bit }

/** Sex the garment is designed for: m = male, f = female, u = unisex. */
enum class Sex(val db: String) {
    MALE("m"),
    FEMALE("f"),
    UNISEX("u");

    companion object {
        fun fromDb(value: String): Sex? = entries.firstOrNull { it.db == value }
    }
}

/** Kind of a custom attribute and how its `config` JSON must be interpreted. */
enum class AttributeType(val db: String) {
    TEXT("text"),
    NUMBER("number"),
    ENUM("enum"),
    MULTI("multi"),
    COLOR("color");

    companion object {
        fun fromDb(value: String): AttributeType? = entries.firstOrNull { it.db == value }
    }
}
