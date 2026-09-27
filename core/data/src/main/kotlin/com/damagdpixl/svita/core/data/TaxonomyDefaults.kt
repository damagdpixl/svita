package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.Section

/**
 * The pristine system-category rows the taxonomy ships with, mirrored from
 * `taxonomy_seed.sq`. `TaxonomyEditorRepository.resetToDefaults` restores
 * system categories from this table: modified rows are overwritten, rows the
 * user deleted are re-inserted with their seed id.
 *
 * Deliberately a hand-mirrored constant (not a schema-time capture): a hidden
 * snapshot table would need a schema migration, and re-running the seed script
 * is impossible on an existing database. Drift is caught by test
 * `TaxonomyEditorRepositoryTest` which resets a pristine database and asserts
 * every system row is byte-identical to its seed.
 */
internal data class CategoryDefault(
    val id: Long,
    val nameEn: String,
    val nameUk: String,
    val icon: String?,
    val sortOrder: Int,
    val section: Section,
)

internal object TaxonomyDefaults {
    val categories: List<CategoryDefault> = listOf(
        CategoryDefault(1001, "Tops", "Топи", "top", 10, Section.BODY),
        CategoryDefault(1002, "Shirts", "Сорочки", "shirt", 20, Section.BODY),
        CategoryDefault(1003, "Sweaters & sweatshirts", "Светри й світшоти", "sweater", 30, Section.BODY),
        CategoryDefault(1004, "Dresses", "Сукні", "dress", 40, Section.DRESS),
        CategoryDefault(1005, "Skirts", "Спідниці", "skirt", 50, Section.LEGS),
        CategoryDefault(1006, "Trousers", "Штани", "trousers", 60, Section.LEGS),
        CategoryDefault(1007, "Jumpsuits", "Комбінезони", "jumpsuit", 70, Section.LEGS),
        CategoryDefault(1008, "Blazers", "Блейзери", "blazer", 80, Section.OUTER),
        CategoryDefault(1009, "Jackets", "Куртки", "jacket", 90, Section.OUTER),
        CategoryDefault(1010, "Coats", "Пальта", "coat", 100, Section.OUTER),
        CategoryDefault(1011, "Vests", "Жилети", "vest", 110, Section.OUTER),
        CategoryDefault(1012, "Cardigans", "Кардигани", "cardigan", 120, Section.OUTER),
        CategoryDefault(1013, "Fur", "Хутро", "fur", 130, Section.OUTER),
        CategoryDefault(1014, "Sandals", "Сандалі", "sandals", 140, Section.FEET),
        CategoryDefault(1015, "Heels", "Туфлі на підборах", "heels", 150, Section.FEET),
        CategoryDefault(1016, "Flats & loafers", "Лофери й балетки", "flats", 160, Section.FEET),
        CategoryDefault(1017, "Sneakers", "Кросівки й кеди", "sneakers", 170, Section.FEET),
        CategoryDefault(1018, "Classic shoes", "Класичне взуття", "classic_shoes", 180, Section.FEET),
        CategoryDefault(1019, "Other shoes", "Інше взуття", "shoes", 190, Section.FEET),
        CategoryDefault(1020, "Boots", "Чоботи й черевики", "boots", 200, Section.FEET),
        CategoryDefault(1021, "Hats", "Капелюхи й шапки", "hat", 210, Section.HAT),
        CategoryDefault(1022, "Scarves", "Шарфи й хустки", "scarf", 220, Section.SCARF),
        CategoryDefault(1023, "Accessories", "Аксесуари", "accessory", 230, Section.ACCESSORY),
        CategoryDefault(1024, "Bags", "Сумки", "bag", 240, Section.BAG),
    )
}
