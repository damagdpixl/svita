package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Write side of the seeded taxonomy — the customization USP. See the contract
 * on [TaxonomyEditorRepository] for the system/custom rules; every public
 * method is transactional and runs off the caller thread.
 */
internal class TaxonomyEditorRepositoryImpl(private val db: AppDatabase) : TaxonomyEditorRepository {

    override suspend fun createCategory(
        section: Section,
        nameEn: String,
        nameUk: String,
        icon: String?,
    ): Long = db.write {
        val maxSort = db.categoriesQueries.selectMaxCategorySortOrder().executeAsOne().toInt()
        db.categoriesQueries.insertCustomCategory(
            parent_id = null,
            name_en = nameEn,
            name_uk = nameUk,
            icon = icon,
            sort_order = (maxSort + DEFAULT_SORT_STEP).toLong(),
            section = section.db,
        )
        val categoryId = db.categoriesQueries.selectLastInsertedCategoryId().executeAsOne()
        // One auto subtype so the category surfaces in the subtype pickers
        // (empty groups are dropped there). Key embeds the id -> unique;
        // thermal class N is the engine's neutral fallback for unknown keys.
        val maxSubtypeId = db.subtypesQueries.selectMaxSubtypeId().executeAsOne()
        db.subtypesQueries.insertSubtype(
            id = maxSubtypeId + 1,
            category_id = categoryId,
            key = CUSTOM_SUBTYPE_PREFIX + categoryId,
            name_en = nameEn,
            name_uk = nameUk,
            thermal_class = NEUTRAL_THERMAL_CLASS,
        )
        categoryId
    }

    override suspend fun updateCategory(
        id: Long,
        nameEn: String,
        nameUk: String,
        icon: String?,
    ): Unit = db.write {
        val row = db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()
            ?: throw NoSuchElementException("category $id does not exist")
        db.categoriesQueries.updateCategoryRow(
            name_en = nameEn,
            name_uk = nameUk,
            icon = icon,
            sort_order = row.sort_order,
            section = row.section,
            id = id,
        )
        if (row.system_flag == 0L) {
            db.renameCustomSubtype(id, nameEn, nameUk)
        }
    }

    override suspend fun updateCustomCategory(
        id: Long,
        section: Section,
        nameEn: String,
        nameUk: String,
        icon: String?,
        sortOrder: Int,
    ): Unit = db.write {
        val row = db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()
            ?: throw NoSuchElementException("category $id does not exist")
        if (row.system_flag != 0L) {
            throw IllegalArgumentException("category $id is system: names and icon only are editable")
        }
        db.categoriesQueries.updateCategoryRow(
            name_en = nameEn,
            name_uk = nameUk,
            icon = icon,
            sort_order = sortOrder.toLong(),
            section = section.db,
            id = id,
        )
        db.renameCustomSubtype(id, nameEn, nameUk)
    }

    override suspend fun moveCustomCategory(id: Long, direction: SortMove): Int = db.write {
        val row = db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()
            ?: throw NoSuchElementException("category $id does not exist")
        if (row.system_flag != 0L) {
            throw IllegalArgumentException("category $id is system: its order is locked")
        }
        val all = db.categoriesQueries.selectAllCategories().executeAsList()
        val index = all.indexOfFirst { it.id == id }
        val step = if (direction == SortMove.UP) -1 else 1
        val targetIndex = index + step
        // Outside the list: nothing to swap with, report the current slot.
        if (targetIndex < 0 || targetIndex >= all.size) return@write row.sort_order.toInt()
        val target = all[targetIndex]
        db.categoriesQueries.updateCategoryRow(
            name_en = row.name_en,
            name_uk = row.name_uk,
            icon = row.icon,
            sort_order = target.sort_order,
            section = row.section,
            id = row.id,
        )
        if (target.system_flag == 0L) {
            // Custom neighbour: a real swap keeps both rows addressable.
            db.categoriesQueries.updateCategoryRow(
                name_en = target.name_en,
                name_uk = target.name_uk,
                icon = target.icon,
                sort_order = row.sort_order,
                section = target.section,
                id = target.id,
            )
        }
        target.sort_order.toInt()
    }

    override suspend fun itemCountsByCategory(): Map<Long, Long> = withContext(Dispatchers.IO) {
        db.categoriesQueries.selectItemCountsByCategory().executeAsList()
            .associate { it.categoryId to it.itemCount }
    }

    override suspend fun deleteCategory(id: Long): CategoryDeleteResult {
        val row = withContext(Dispatchers.IO) {
            db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()
        } ?: throw NoSuchElementException("category $id does not exist")
        val itemCount = withContext(Dispatchers.IO) {
            db.categoriesQueries.countItemsByCategory(id).executeAsOne()
        }
        if (itemCount > 0) return CategoryDeleteResult.Blocked(CategoryDeleteBlock.HAS_ITEMS)
        val subtypeCount = withContext(Dispatchers.IO) {
            db.categoriesQueries.countSubtypesByCategory(id).executeAsOne()
        }
        if (row.system_flag != 0L && subtypeCount > 0) {
            return CategoryDeleteResult.Blocked(CategoryDeleteBlock.HAS_SUBTYPES)
        }
        return db.write {
            db.categoriesQueries.deleteAttributeDefinitionsByCategory(id)
            db.categoriesQueries.deleteSubtypesByCategory(id)
            db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()
                ?: return@write CategoryDeleteResult.Deleted
            db.categoriesQueries.deleteCategoryRow(id)
            CategoryDeleteResult.Deleted
        }
    }

    override suspend fun resetToDefaults(): ResetTaxonomyResult = db.write {
        val itemsByCategory = db.categoriesQueries.selectItemCountsByCategory().executeAsList()
            .associate { it.categoryId to it.itemCount }
        val customs = db.categoriesQueries.selectAllCategories().executeAsList()
            .filter { it.system_flag == 0L }
        if (customs.any { (itemsByCategory[it.id] ?: 0L) > 0L }) {
            return@write ResetTaxonomyResult.BlockedByItems
        }
        // Custom categories: definitions and auto subtypes first, then the rows.
        customs.forEach { custom ->
            db.categoriesQueries.deleteAttributeDefinitionsByCategory(custom.id)
            db.categoriesQueries.deleteSubtypesByCategory(custom.id)
        }
        db.categoriesQueries.detachCustomCategoryParents()
        db.categoriesQueries.deleteCustomCategories()
        // System rows: restore the modified, re-insert the deleted.
        TaxonomyDefaults.categories.forEach { default ->
            val existing = db.categoriesQueries.selectCategoryById(default.id).executeAsOneOrNull()
            if (existing == null) {
                db.categoriesQueries.insertCategory(
                    id = default.id,
                    parent_id = null,
                    name_en = default.nameEn,
                    name_uk = default.nameUk,
                    icon = default.icon,
                    sort_order = default.sortOrder.toLong(),
                    system_flag = 1L,
                    section = default.section.db,
                )
            } else {
                db.categoriesQueries.restoreCategoryRow(
                    parent_id = null,
                    name_en = default.nameEn,
                    name_uk = default.nameUk,
                    icon = default.icon,
                    sort_order = default.sortOrder.toLong(),
                    system_flag = 1L,
                    section = default.section.db,
                    id = default.id,
                )
            }
        }
        ResetTaxonomyResult.Reset
    }

    /** Auto-subtype display names follow the custom category's rename. */
    private fun AppDatabase.renameCustomSubtype(categoryId: Long, nameEn: String, nameUk: String) {
        categoriesQueries.renameCustomSubtypeNames(
            name_en = nameEn,
            name_uk = nameUk,
            category_id = categoryId,
        )
    }

    private companion object {
        const val DEFAULT_SORT_STEP: Int = 10
        const val CUSTOM_SUBTYPE_PREFIX: String = "custom."
        const val NEUTRAL_THERMAL_CLASS: String = "N"
    }
}
