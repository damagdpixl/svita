package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.core.model.Subtype
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class TaxonomyRepositoryImpl(private val db: AppDatabase) : TaxonomyRepository {

    override fun observeCategories(): Flow<List<Category>> =
        db.categoriesQueries.selectAllCategories().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        db.categoriesQueries.selectAllCategories().executeAsList().map { it.toDomain() }
    }

    override suspend fun category(id: Long): Category? = withContext(Dispatchers.IO) {
        db.categoriesQueries.selectCategoryById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun categoryTree(): List<CategoryNode> = withContext(Dispatchers.IO) {
        val all = db.categoriesQueries.selectAllCategories().executeAsList().map { it.toDomain() }
        val knownIds = all.map { it.id }.toSet()
        val childrenByParent = all.groupBy { it.parentId }
        // Categories pointing at a missing parent surface as roots: the seed
        // never does that, but user rows could after future taxonomy edits.
        fun build(category: Category): CategoryNode = CategoryNode(
            category = category,
            children = (childrenByParent[category.id] ?: emptyList()).map(::build),
        )
        all.filter { it.parentId == null || it.parentId !in knownIds }.map(::build)
    }

    override suspend fun subtypesByCategory(categoryId: Long): List<Subtype> = withContext(Dispatchers.IO) {
        db.subtypesQueries.selectSubtypesByCategory(categoryId).executeAsList().map { it.toDomain() }
    }

    override suspend fun subtypesBySection(section: Section): List<Subtype> = withContext(Dispatchers.IO) {
        db.subtypesQueries.selectSubtypesBySection(section.db).executeAsList().map { it.toDomain() }
    }

    override suspend fun subtype(id: Long): Subtype? = withContext(Dispatchers.IO) {
        db.subtypesQueries.selectSubtypeById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun subtypeByKey(key: String): Subtype? = withContext(Dispatchers.IO) {
        db.subtypesQueries.selectSubtypeByKey(key).executeAsOneOrNull()?.toDomain()
    }

    override fun observeColors(): Flow<List<PaletteColor>> =
        db.colorsQueries.selectAllColors().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun colorByKey(key: String): PaletteColor? = withContext(Dispatchers.IO) {
        db.colorsQueries.selectColorByKey(key).executeAsOneOrNull()?.toDomain()
    }

    override fun observeStyleTags(): Flow<List<StyleTag>> =
        db.style_tagsQueries.selectAllStyleTags().observed().map { list -> list.map { it.toDomain() } }

    override suspend fun styleTagByKey(key: String): StyleTag? = withContext(Dispatchers.IO) {
        db.style_tagsQueries.selectStyleTagByKey(key).executeAsOneOrNull()?.toDomain()
    }
}

internal class AttributesRepositoryImpl(private val db: AppDatabase) : AttributesRepository {

    override fun observeDefinitions(categoryId: Long?): Flow<List<AttributeDefinition>> =
        if (categoryId == null) {
            db.attribute_definitionsQueries.selectAllAttributeDefinitions().observed()
        } else {
            db.attribute_definitionsQueries.selectAttributeDefinitionsForCategory(categoryId).observed()
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun definitions(): List<AttributeDefinition> = withContext(Dispatchers.IO) {
        db.attribute_definitionsQueries.selectAllAttributeDefinitions().executeAsList().map { it.toDomain() }
    }

    override suspend fun definition(id: Long): AttributeDefinition? = withContext(Dispatchers.IO) {
        db.attribute_definitionsQueries.selectAttributeDefinitionById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun validate(definitionId: Long, rawValue: String): ValueValidation {
        val definition = definition(definitionId)
            ?: throw NoSuchElementException("attribute definition $definitionId does not exist")
        return validateValue(definition, rawValue)
    }

    override suspend fun createDefinition(
        categoryId: Long?,
        key: String,
        type: AttributeType,
        config: String?,
        sortOrder: Int,
    ): Long = db.write {
        db.attribute_definitionsQueries.insertAttributeDefinition(
            category_id = categoryId,
            key = key,
            type = type.db,
            config = config,
            sort_order = sortOrder.toLong(),
        )
        db.attribute_definitionsQueries.selectLastInsertedAttributeDefinitionId().executeAsOne()
    }

    override suspend fun updateDefinition(
        id: Long,
        categoryId: Long?,
        key: String,
        type: AttributeType,
        config: String?,
        sortOrder: Int,
    ): Unit = db.write {
        if (db.attribute_definitionsQueries.selectAttributeDefinitionById(id).executeAsOneOrNull() == null) {
            throw NoSuchElementException("attribute definition $id does not exist")
        }
        db.attribute_definitionsQueries.updateAttributeDefinition(
            category_id = categoryId,
            key = key,
            type = type.db,
            config = config,
            sort_order = sortOrder.toLong(),
            id = id,
        )
    }

    override suspend fun deleteDefinition(id: Long): Unit = db.write {
        // attribute_values rows cascade away via FK.
        db.attribute_definitionsQueries.deleteAttributeDefinition(id)
    }

    override suspend fun setValue(itemId: Long, definitionId: Long, rawValue: String?): ValueWriteResult = db.write {
        val definition = db.attribute_definitionsQueries.selectAttributeDefinitionById(definitionId)
            .executeAsOneOrNull()?.toDomain() ?: return@write ValueWriteResult.UnknownDefinition
        if (rawValue == null) {
            db.attribute_valuesQueries.deleteAttributeValue(itemId, definitionId)
            return@write ValueWriteResult.Saved
        }
        when (val outcome = validateValue(definition, rawValue)) {
            is ValueValidation.Invalid -> ValueWriteResult.Rejected(outcome.kind)
            is ValueValidation.Valid -> {
                db.attribute_valuesQueries.upsertAttributeValue(itemId, definitionId, outcome.normalized)
                ValueWriteResult.Saved
            }
        }
    }

    override suspend fun valuesForItem(itemId: Long): List<AttributeEntry> = withContext(Dispatchers.IO) {
        db.attribute_valuesQueries.selectAttributeValuesByItem(itemId).executeAsList().map { it.toDomain() }
    }
}
