package com.damagdpixl.svita.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.data.CategoryDeleteBlock
import com.damagdpixl.svita.core.data.SortMove
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.designsystem.monoUpper
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialChip
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import com.damagdpixl.svita.ui.wardrobe.OutlinedInput
import com.damagdpixl.svita.ui.wardrobe.attributeTypeLabelRes
import com.damagdpixl.svita.ui.wardrobe.optionsOf
import com.damagdpixl.svita.ui.wardrobe.parsePrice

@StringRes
fun sectionLabelRes(section: Section): Int = when (section) {
    Section.BODY -> R.string.section_body
    Section.LEGS -> R.string.section_legs
    Section.FEET -> R.string.section_feet
    Section.DRESS -> R.string.section_dress
    Section.OUTER -> R.string.section_outer
    Section.ACCESSORY -> R.string.section_accessory
    Section.HAT -> R.string.section_hat
    Section.SCARF -> R.string.section_scarf
    Section.BAG -> R.string.section_bag
}

/** Small built-in icon vocabulary (the seed's own keys), stored as a string. */
val categoryIconKeys: List<String> = listOf(
    "top", "shirt", "sweater", "dress", "skirt", "trousers",
    "jacket", "coat", "shoes", "hat", "scarf", "bag",
)

/** Localized display name of a category, per the current locale. */
@Composable
fun Category.localizedName(): String =
    if (LocalConfiguration.current.locales[0].language == "uk") nameUk else nameEn

/**
 * Кастомізація: the customization USP. Categories (add/edit/reorder/delete,
 * «Скинути до стандартних») and custom fields (typed editors matching the
 * :core:data config JSON shapes). Every change lands in the repositories the
 * item editor observes, so pickers and attribute fields update immediately.
 */
@Composable
fun CustomizationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomizationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CustomizationViewModel(SvitaGraph.get()) }
        },
    )
    val state by viewModel.state.collectAsState()

    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryCreator by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var editingField by remember { mutableStateOf<AttributeDefinition?>(null) }
    var showFieldCreator by remember { mutableStateOf(false) }
    var deletingField by remember { mutableStateOf<AttributeDefinition?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("customization_root"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("customization_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.customization_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.categories_title).monoUpper(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.category_add),
                    onClick = { showCategoryCreator = true },
                    modifier = Modifier.testTag("category_add"),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            state.categories.forEach { category ->
                CategoryRow(
                    category = category,
                    itemCount = state.itemCounts[category.id] ?: 0L,
                    onEdit = { editingCategory = category },
                    onDelete = { deletingCategory = category },
                    onMoveUp = { viewModel.moveCategory(category.id, SortMove.UP) },
                    onMoveDown = { viewModel.moveCategory(category.id, SortMove.DOWN) },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.fields_title).monoUpper(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.field_add),
                    onClick = { showFieldCreator = true },
                    modifier = Modifier.testTag("field_add"),
                )
            }
            if (state.definitions.isEmpty()) {
                Text(
                    text = stringResource(R.string.fields_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            state.definitions.forEach { definition ->
                FieldRow(
                    definition = definition,
                    categories = state.categories,
                    onEdit = { editingField = definition },
                    onDelete = { deletingField = definition },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            GreenCta(
                text = stringResource(R.string.categories_reset),
                onClick = { showResetConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("categories_reset"),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ---- dialogs ---------------------------------------------------------

    if (showCategoryCreator) {
        CategoryEditorDialog(
            categories = state.categories,
            onCreate = { section, en, uk, icon ->
                viewModel.createCategory(section, en, uk, icon)
                showCategoryCreator = false
            },
            onDismiss = { showCategoryCreator = false },
        )
    }
    editingCategory?.let { category ->
        CategoryEditorDialog(
            categories = state.categories,
            existing = category,
            onUpdate = { section, en, uk, icon, sortOrder ->
                if (category.isSystem) {
                    viewModel.updateCategory(category.id, en, uk, icon)
                } else {
                    viewModel.updateCustomCategory(
                        id = category.id,
                        section = section,
                        nameEn = en,
                        nameUk = uk,
                        icon = icon,
                        sortOrder = sortOrder,
                    )
                }
                editingCategory = null
            },
            onDismiss = { editingCategory = null },
        )
    }
    deletingCategory?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.category_delete_confirm_title),
            text = stringResource(R.string.category_delete_confirm_text, category.localizedName()),
            confirmTag = "category_delete_confirm",
            onConfirm = {
                viewModel.deleteCategory(category.id)
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null },
        )
    }
    when (val event = state.event) {
        is CustomizationEvent.CategoryDeleteBlocked -> EditorialDialog(
            onDismiss = viewModel::consumeEvent,
            scrimTag = "category_blocked_scrim",
        ) {
            Text(
                text = stringResource(
                    if (event.reason == CategoryDeleteBlock.HAS_ITEMS) {
                        R.string.category_delete_blocked_items
                    } else {
                        R.string.category_delete_blocked_subtypes
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Cream,
                modifier = Modifier.testTag("category_blocked_text"),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = viewModel::consumeEvent,
                modifier = Modifier.testTag("category_blocked_ok"),
            )
        }

        is CustomizationEvent.ResetBlocked -> EditorialDialog(
            onDismiss = viewModel::consumeEvent,
            scrimTag = "reset_blocked_scrim",
        ) {
            Text(
                text = stringResource(R.string.categories_reset_blocked),
                style = MaterialTheme.typography.bodyLarge,
                color = Cream,
                modifier = Modifier.testTag("reset_blocked_text"),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = viewModel::consumeEvent,
                modifier = Modifier.testTag("reset_blocked_ok"),
            )
        }

        null -> Unit
    }

    if (showFieldCreator) {
        FieldEditorDialog(
            categories = state.categories,
            onSave = { name, type, options, min, max, categoryId, onResult ->
                viewModel.createDefinition(name, type, options, min, max, categoryId, onResult)
            },
            onDismiss = { showFieldCreator = false },
        )
    }
    editingField?.let { definition ->
        FieldEditorDialog(
            categories = state.categories,
            existing = definition,
            onSave = { name, type, options, min, max, categoryId, onResult ->
                viewModel.updateDefinition(
                    definition.id, name, type, options, min, max, categoryId, onResult,
                )
            },
            onDismiss = { editingField = null },
        )
    }
    deletingField?.let { definition ->
        ConfirmDialog(
            title = stringResource(R.string.field_delete_confirm_title),
            text = stringResource(R.string.field_delete_confirm_text, definition.key),
            confirmTag = "field_delete_confirm",
            onConfirm = {
                viewModel.deleteDefinition(definition.id)
                deletingField = null
            },
            onDismiss = { deletingField = null },
        )
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.categories_reset_confirm_title),
            text = stringResource(R.string.categories_reset_confirm_text),
            confirmTag = "reset_confirm",
            onConfirm = {
                viewModel.resetToDefaults()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    itemCount: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val moveUpLabel = stringResource(R.string.category_move_up)
    val moveDownLabel = stringResource(R.string.category_move_down)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        category.icon?.let { append("[$it] ") }
                        append(category.localizedName())
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("category_row_${category.id}"),
                )
                Text(
                    text = stringResource(sectionLabelRes(category.section)) +
                        " · " + stringResource(R.string.category_items_count, itemCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
            if (!category.isSystem) {
                Text(
                    text = "↑",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clickable(onClick = onMoveUp)
                        .padding(6.dp)
                        .semantics {
                            contentDescription = moveUpLabel
                        }
                        .testTag("category_up_${category.id}"),
                )
                Text(
                    text = "↓",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clickable(onClick = onMoveDown)
                        .padding(6.dp)
                        .semantics {
                            contentDescription = moveDownLabel
                        }
                        .testTag("category_down_${category.id}"),
                )
            }
            Text(
                text = stringResource(R.string.action_edit),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable(onClick = onEdit)
                    .padding(6.dp)
                    .testTag("category_edit_${category.id}"),
            )
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
                    .testTag("category_delete_${category.id}"),
            )
        }
    }
}

@Composable
private fun FieldRow(
    definition: AttributeDefinition,
    categories: List<Category>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition.key.monoUpper(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("field_row_${definition.id}"),
            )
            val scopeName = definition.categoryId
                ?.let { id -> categories.firstOrNull { it.id == id } }
                ?.let { it.localizedName() }
                ?: stringResource(R.string.field_scope_global)
            Text(
                text = stringResource(attributeTypeLabelRes(definition.type)) + " · " + scopeName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
        Text(
            text = stringResource(R.string.action_edit),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(6.dp)
                .testTag("field_edit_${definition.id}"),
        )
        Text(
            text = "✕",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(6.dp)
                .testTag("field_delete_${definition.id}"),
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    categories: List<Category>,
    existing: Category? = null,
    onCreate: (Section, String, String, String?) -> Unit = { _, _, _, _ -> },
    onUpdate: (Section, String, String, String?, Int) -> Unit = { _, _, _, _, _ -> },
    onDismiss: () -> Unit,
) {
    var nameEn by remember { mutableStateOf(existing?.nameEn.orEmpty()) }
    var nameUk by remember { mutableStateOf(existing?.nameUk.orEmpty()) }
    var section by remember { mutableStateOf(existing?.section ?: Section.BODY) }
    var icon by remember { mutableStateOf(existing?.icon) }
    val sortOrder = existing?.sortOrder ?: (categories.maxOfOrNull { it.sortOrder } ?: 0) + 10
    val systemLocked = existing?.isSystem == true

    EditorialDialog(onDismiss = onDismiss, scrimTag = "category_editor_scrim") {
        Text(
            text = stringResource(
                if (existing == null) R.string.category_add else R.string.category_edit,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        if (systemLocked) {
            Text(
                text = stringResource(R.string.category_system_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.7f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedInput(
            value = nameUk,
            onValueChange = { nameUk = it },
            label = stringResource(R.string.category_name_uk),
            testTagValue = "category_name_uk",
            onDark = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedInput(
            value = nameEn,
            onValueChange = { nameEn = it },
            label = stringResource(R.string.category_name_en),
            testTagValue = "category_name_en",
            onDark = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.category_section_label),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipRow(
            entries = Section.entries,
            label = { stringResource(sectionLabelRes(it)) },
            tagOf = { it.name },
            selected = { it == section },
            enabled = !systemLocked,
            onSelect = { section = it },
            tagPrefix = "category_section",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.category_icon),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipRow(
            entries = categoryIconKeys,
            label = { it },
            tagOf = { it },
            selected = { it == icon },
            enabled = true,
            onSelect = { icon = it },
            tagPrefix = "category_icon",
        )
        EditorialChip(
            text = stringResource(R.string.category_icon_none),
            selected = icon == null,
            onClick = { icon = null },
            modifier = Modifier.testTag("category_icon_none"),
            onDark = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("category_editor_cancel"),
            )
            GreenCta(
                text = stringResource(R.string.action_save),
                enabled = nameUk.isNotBlank() && nameEn.isNotBlank(),
                onClick = {
                    if (existing == null) {
                        onCreate(section, nameEn.trim(), nameUk.trim(), icon)
                    } else {
                        onUpdate(section, nameEn.trim(), nameUk.trim(), icon, sortOrder)
                    }
                },
                modifier = Modifier.testTag("category_editor_save"),
            )
        }
    }
}

@Composable
private fun FieldEditorDialog(
    categories: List<Category>,
    existing: AttributeDefinition? = null,
    onSave: (String, AttributeType, List<String>, String, String, Long?, (Boolean) -> Unit) -> Unit =
        { _, _, _, _, _, _, _ -> },
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.key.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AttributeType.TEXT) }
    var options by remember { mutableStateOf(existing?.let(::optionsOf).orEmpty()) }
    var optionInput by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var scopeId by remember { mutableStateOf(existing?.categoryId) }
    var duplicate by remember { mutableStateOf(false) }
    var optionsError by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf(false) }

    EditorialDialog(onDismiss = onDismiss, scrimTag = "field_editor_scrim") {
        Text(
            text = stringResource(
                if (existing == null) R.string.field_add else R.string.field_edit,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedInput(
            value = name,
            onValueChange = {
                name = it
                duplicate = false
            },
            label = stringResource(R.string.editor_attribute_name),
            errorRes = if (duplicate) R.string.field_duplicate else null,
            testTagValue = "field_name",
            onDark = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.editor_attribute_type),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipRow(
            entries = AttributeType.entries,
            label = { stringResource(attributeTypeLabelRes(it)) },
            tagOf = { it.name },
            selected = { it == type },
            enabled = true,
            onSelect = {
                type = it
                optionsError = false
                rangeError = false
            },
            tagPrefix = "field_type",
        )
        if (type == AttributeType.ENUM || type == AttributeType.MULTI) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.editor_attribute_options),
                style = MaterialTheme.typography.labelMedium,
                color = Cream.copy(alpha = 0.7f),
            )
            options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Cream,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("field_option_$index"),
                    )
                    Text(
                        text = "↑",
                        color = Cream,
                        modifier = Modifier
                            .clickable(enabled = index > 0) {
                                options = options.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                }
                            }
                            .padding(6.dp)
                            .testTag("field_option_up_$index"),
                    )
                    Text(
                        text = "↓",
                        color = Cream,
                        modifier = Modifier
                            .clickable(enabled = index < options.lastIndex) {
                                options = options.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                }
                            }
                            .padding(6.dp)
                            .testTag("field_option_down_$index"),
                    )
                    Text(
                        text = "✕",
                        color = Cream,
                        modifier = Modifier
                            .clickable { options = options - option }
                            .padding(6.dp)
                            .testTag("field_option_remove_$index"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = optionInput,
                        onValueChange = { optionInput = it },
                        label = stringResource(R.string.field_option_add),
                        testTagValue = "field_option_input",
                        onDark = true,
                    )
                }
                PillButton(
                    text = stringResource(R.string.action_add),
                    onClick = {
                        val trimmed = optionInput.trim()
                        if (trimmed.isNotEmpty()) {
                            options = options + trimmed
                            optionInput = ""
                            optionsError = false
                        }
                    },
                    modifier = Modifier.testTag("field_option_commit"),
                )
            }
            if (optionsError) {
                Text(
                    text = stringResource(R.string.field_options_required),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("field_options_error"),
                )
            }
        }
        if (type == AttributeType.NUMBER) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = min,
                        onValueChange = {
                            min = it
                            rangeError = false
                        },
                        label = stringResource(R.string.editor_attribute_min),
                        numberKeyboard = true,
                        errorRes = if (rangeError) R.string.field_range_invalid else null,
                        testTagValue = "field_min",
                        onDark = true,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedInput(
                        value = max,
                        onValueChange = {
                            max = it
                            rangeError = false
                        },
                        label = stringResource(R.string.editor_attribute_max),
                        numberKeyboard = true,
                        errorRes = if (rangeError) R.string.field_range_invalid else null,
                        testTagValue = "field_max",
                        onDark = true,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.field_scope),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.7f),
        )
        ChipRow(
            entries = categories,
            label = { it.localizedName() },
            tagOf = { it.id.toString() },
            selected = { it.id == scopeId },
            enabled = true,
            onSelect = { picked ->
                scopeId = if (scopeId == picked.id) null else picked.id
            },
            tagPrefix = "field_scope",
        )
        EditorialChip(
            text = stringResource(R.string.field_scope_global),
            selected = scopeId == null,
            onClick = { scopeId = null },
            modifier = Modifier.testTag("field_scope_global"),
            onDark = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("field_editor_cancel"),
            )
            GreenCta(
                text = stringResource(R.string.action_save),
                enabled = name.isNotBlank(),
                onClick = {
                    // Client-side gates first: no write attempt on a broken form.
                    val needsOptions = type == AttributeType.ENUM || type == AttributeType.MULTI
                    if (needsOptions && options.none { it.isNotBlank() }) {
                        optionsError = true
                        return@GreenCta
                    }
                    if (type == AttributeType.NUMBER) {
                        val minBound = parsePrice(min)
                        val maxBound = parsePrice(max)
                        if (minBound != null && maxBound != null && minBound > maxBound) {
                            rangeError = true
                            return@GreenCta
                        }
                    }
                    duplicate = false
                    onSave(name.trim(), type, options.toList(), min, max, scopeId) { saved ->
                        if (saved) {
                            onDismiss()
                        } else {
                            // Duplicate key+scope (or a repository refusal):
                            // keep the dialog open with the inline error.
                            duplicate = true
                        }
                    }
                },
                modifier = Modifier.testTag("field_editor_save"),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    EditorialDialog(onDismiss = onDismiss, scrimTag = "confirm_scrim") {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Cream.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_cancel"),
            )
            GreenCta(
                text = stringResource(R.string.action_delete),
                onClick = onConfirm,
                modifier = Modifier.testTag(confirmTag),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    entries: List<T>,
    label: @Composable (T) -> String,
    tagOf: (T) -> String,
    selected: (T) -> Boolean,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    tagPrefix: String,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.forEach { entry ->
            EditorialChip(
                text = label(entry),
                selected = selected(entry),
                onClick = { if (enabled) onSelect(entry) },
                modifier = Modifier.testTag("${tagPrefix}_${tagOf(entry)}"),
                onDark = true,
            )
        }
    }
}
