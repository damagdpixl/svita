package com.damagdpixl.svita.ui.wardrobe

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.data.ValueErrorKind
import com.damagdpixl.svita.core.designsystem.Charcoal
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.monoUpper
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Attribute config JSON shapes (mirror of the internal :core:data shapes). */
@Serializable
private data class OptionsConfigJson(val options: List<String> = emptyList())

@Serializable
private data class NumberConfigJson(val min: Double? = null, val max: Double? = null)

private val configJson: Json = Json { ignoreUnknownKeys = true }

/** Options of an ENUM/MULTI definition; empty when config is absent or broken. */
fun optionsOf(definition: AttributeDefinition): List<String> =
    definition.config?.let { config ->
        runCatching { configJson.decodeFromString<OptionsConfigJson>(config).options }.getOrNull()
    }.orEmpty()

/** Raw MULTI cell value -> selected options (tolerates malformed storage). */
fun decodeMulti(raw: String): List<String> =
    runCatching { configJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

/** Selected options -> normalized MULTI cell value (stable order, deduped). */
fun encodeMulti(selected: Collection<String>): String = configJson.encodeToString(selected.distinct())

/** Builds an ENUM/MULTI `config` JSON for a new attribute definition. */
fun encodeOptionsConfig(options: List<String>): String =
    configJson.encodeToString(OptionsConfigJson(options.distinct()))

/** Builds a NUMBER `config` JSON (null = unbounded side). */
fun encodeNumberConfig(min: Double?, max: Double?): String =
    configJson.encodeToString(NumberConfigJson(min, max))

/** Ukrainian-first price rendering: «1250» or «1250,5». */
fun formatPrice(price: Double): String {
    val rounded = kotlin.math.round(price * 100) / 100
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString().replace('.', ',')
    }
}

/** Comma-tolerant price parsing («1250,50» -> 1250.5). */
fun parsePrice(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

@StringRes
fun valueErrorRes(kind: ValueErrorKind): Int = when (kind) {
    ValueErrorKind.EMPTY_TEXT -> R.string.value_error_empty
    ValueErrorKind.TEXT_TOO_LONG -> R.string.value_error_too_long
    ValueErrorKind.NOT_A_NUMBER -> R.string.value_error_not_a_number
    ValueErrorKind.OUT_OF_RANGE -> R.string.value_error_out_of_range
    ValueErrorKind.MISSING_CONFIG -> R.string.value_error_missing_config
    ValueErrorKind.MALFORMED_CONFIG -> R.string.value_error_malformed_config
    ValueErrorKind.NOT_AN_OPTION -> R.string.value_error_not_an_option
    ValueErrorKind.MALFORMED_MULTI -> R.string.value_error_malformed_multi
    ValueErrorKind.MALFORMED_COLOR -> R.string.value_error_malformed_color
}

@StringRes
fun seasonLabelRes(season: Season): Int = when (season) {
    Season.SPRING -> R.string.season_spring
    Season.SUMMER -> R.string.season_summer
    Season.AUTUMN -> R.string.season_autumn
    Season.WINTER -> R.string.season_winter
}

@StringRes
fun sexLabelRes(sex: Sex): Int = when (sex) {
    Sex.MALE -> R.string.sex_male
    Sex.FEMALE -> R.string.sex_female
    Sex.UNISEX -> R.string.sex_unisex
}

@StringRes
fun attributeTypeLabelRes(type: AttributeType): Int = when (type) {
    AttributeType.TEXT -> R.string.attribute_type_text
    AttributeType.NUMBER -> R.string.attribute_type_number
    AttributeType.ENUM -> R.string.attribute_type_enum
    AttributeType.MULTI -> R.string.attribute_type_multi
    AttributeType.COLOR -> R.string.attribute_type_color
}

/** Editorial chip: flat outline, marigold when selected. [onDark] restyles the
 * unselected label for chips sitting on the charcoal panels/dialogs. */
@Composable
fun EditorialChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = if (onDark) Cream else MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = Marigold,
            selectedLabelColor = Charcoal,
        ),
    )
}

/** Flow of chips with editorial spacing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * Editorial modal overlay: charcoal scrim + centered charcoal panel. Hand-rolled
 * instead of M3 dialogs/sheets so the flow stays deterministic under Robolectric
 * (no ambient animation clock) and stays in the design language.
 */
@Composable
fun EditorialDialog(
    modifier: Modifier = Modifier,
    scrimTag: String = "dialog_scrim",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Charcoal.copy(alpha = 0.55f))
            .testTag(scrimTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CharcoalPanel(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
        ) {
            content()
        }
    }
}

/** One editable text line used across the wardrobe UI (editorial outline).
 * [onDark] restyles it for the charcoal dialog panels. */
@Composable
fun OutlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorRes: Int? = null,
    singleLine: Boolean = true,
    numberKeyboard: Boolean = false,
    label: String? = null,
    testTagValue: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onDark: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTagValue != null) Modifier.testTag(testTagValue) else Modifier),
        singleLine = singleLine,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        isError = errorRes != null,
        colors = if (onDark) {
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Cream,
                unfocusedTextColor = Cream,
                cursorColor = Marigold,
                focusedBorderColor = Cream,
                unfocusedBorderColor = Cream.copy(alpha = 0.45f),
                focusedLabelColor = Cream,
                unfocusedLabelColor = Cream.copy(alpha = 0.6f),
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numberKeyboard) KeyboardType.Number else KeyboardType.Text,
        ),
        keyboardActions = keyboardActions,
        supportingText = if (errorRes != null) {
            { Text(stringResource(errorRes)) }
        } else {
            null
        },
    )
}

/** One custom-attribute input cell, rendered by the definition type. */
@Composable
fun AttributeField(
    input: AttributeInput,
    errorRes: Int?,
    palette: List<PaletteColor>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val definition = input.definition
    Column(modifier = modifier.testTag("attribute_${definition.id}")) {
        Text(
            text = definition.key.monoUpper(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        when (definition.type) {
            AttributeType.TEXT -> OutlinedInput(
                value = input.value,
                onValueChange = onValueChange,
                errorRes = errorRes,
                singleLine = false,
            )

            AttributeType.NUMBER -> OutlinedInput(
                value = input.value,
                onValueChange = onValueChange,
                errorRes = errorRes,
                singleLine = true,
                numberKeyboard = true,
            )

            AttributeType.ENUM -> ChipFlowRow {
                optionsOf(definition).forEach { option ->
                    EditorialChip(
                        text = option,
                        selected = input.value == option,
                        onClick = { onValueChange(option) },
                    )
                }
            }

            AttributeType.MULTI -> {
                val selected = decodeMulti(input.value).toSet()
                ChipFlowRow {
                    optionsOf(definition).forEach { option ->
                        EditorialChip(
                            text = option,
                            selected = option in selected,
                            onClick = {
                                val next = if (option in selected) {
                                    selected - option
                                } else {
                                    selected + option
                                }
                                onValueChange(encodeMulti(next))
                            },
                        )
                    }
                }
            }

            AttributeType.COLOR -> ColorSwatchRow(
                palette = palette,
                selectedHex = input.value,
                onSelect = onValueChange,
            )
        }
        if (errorRes != null) {
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    palette: List<PaletteColor>,
    selectedHex: String?,
    onSelect: (String) -> Unit,
) {
    ChipFlowRow {
        palette.forEach { color ->
            val selected = color.hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(color.hex))
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Charcoal.copy(alpha = 0.25f)
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color.hex) }
                    .testTag("color_${color.key}"),
            )
        }
    }
}

fun parseHexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrDefault(Cream)

/** An editable attribute cell value shared by the wizard and the editor. */
data class AttributeInput(
    val definition: AttributeDefinition,
    val value: String,
)

/**
 * Decodes a stored photo file off the main thread, downsampled for display.
 * Missing/corrupt files yield null — callers render their own placeholder.
 */
@Composable
fun rememberStoredPhoto(path: String?, targetLongSide: Int = 1280): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, path, targetLongSide) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetLongSide) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }.value
}

/** Photo (or placeholder) rendered from a stored file path. */
@Composable
fun StoredPhoto(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit,
) {
    val bitmap = rememberStoredPhoto(path)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier = modifier) { placeholder() }
    }
}
