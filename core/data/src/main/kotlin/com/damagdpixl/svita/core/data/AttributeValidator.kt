package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Max length of a TEXT attribute value (v1 product spec). */
public const val TEXT_ATTRIBUTE_MAX_LENGTH: Int = 500

/**
 * Config shapes stored in `attribute_definitions.config`, interpreted per
 * [AttributeType]: NUMBER reads min/max bounds, ENUM/MULTI read the allowed
 * option list. Extra keys are ignored so the config can grow without a
 * migration; a broken shape is reported as MALFORMED_CONFIG, an absent
 * config (where one is required) as MISSING_CONFIG.
 */
@Serializable
internal data class NumberConfigJson(val min: Double? = null, val max: Double? = null)

@Serializable
internal data class OptionsConfigJson(val options: List<String> = emptyList())

internal val attributeConfigJson: Json = Json { ignoreUnknownKeys = true }

private val colorHexRegex: Regex = Regex("#[0-9a-fA-F]{6}")

private sealed interface ConfigParse {
    data class Options(val options: List<String>) : ConfigParse
    data object Missing : ConfigParse
    data object Malformed : ConfigParse
}

private fun parseOptions(definition: AttributeDefinition): ConfigParse {
    val config = definition.config
    return when {
        config == null -> ConfigParse.Missing
        else -> runCatching {
            ConfigParse.Options(attributeConfigJson.decodeFromString<OptionsConfigJson>(config).options)
        }.getOrElse { ConfigParse.Malformed }
    }
}

/**
 * Pure validation of one raw attribute value against its definition.
 * Returns the normalized storage form on success — TEXT is trimmed, NUMBER is
 * re-encoded canonically (accepting `,` as the decimal separator), MULTI is
 * re-encoded as a de-duplicated JSON array, COLOR is uppercased. Invalid
 * values are reported as [ValueErrorKind]s; strings live in the UI layer.
 */
internal fun validateValue(definition: AttributeDefinition, raw: String): ValueValidation = when (definition.type) {
    AttributeType.TEXT -> {
        val trimmed = raw.trim()
        when {
            trimmed.isEmpty() -> ValueValidation.Invalid(ValueErrorKind.EMPTY_TEXT)
            trimmed.length > TEXT_ATTRIBUTE_MAX_LENGTH -> ValueValidation.Invalid(ValueErrorKind.TEXT_TOO_LONG)
            else -> ValueValidation.Valid(trimmed)
        }
    }

    AttributeType.NUMBER -> {
        val config = definition.config
        val bounds = if (config == null) {
            null
        } else {
            val parsed = runCatching { attributeConfigJson.decodeFromString<NumberConfigJson>(config) }
                .getOrElse { return ValueValidation.Invalid(ValueErrorKind.MALFORMED_CONFIG) }
            if (parsed.min != null && parsed.max != null && parsed.min > parsed.max) {
                return ValueValidation.Invalid(ValueErrorKind.MALFORMED_CONFIG)
            }
            parsed
        }
        // The app is Ukrainian-first: accept the comma decimal separator too.
        val value = raw.trim().replace(',', '.').toDoubleOrNull()
            ?: return ValueValidation.Invalid(ValueErrorKind.NOT_A_NUMBER)
        if (value.isNaN() || value.isInfinite()) {
            return ValueValidation.Invalid(ValueErrorKind.NOT_A_NUMBER)
        }
        if (bounds?.min != null && value < bounds.min) {
            return ValueValidation.Invalid(ValueErrorKind.OUT_OF_RANGE)
        }
        if (bounds?.max != null && value > bounds.max) {
            return ValueValidation.Invalid(ValueErrorKind.OUT_OF_RANGE)
        }
        ValueValidation.Valid(value.toString())
    }

    AttributeType.ENUM -> when (val parse = parseOptions(definition)) {
        is ConfigParse.Options -> {
            val candidate = raw.trim()
            if (candidate in parse.options) {
                ValueValidation.Valid(candidate)
            } else {
                ValueValidation.Invalid(ValueErrorKind.NOT_AN_OPTION)
            }
        }
        ConfigParse.Missing -> ValueValidation.Invalid(ValueErrorKind.MISSING_CONFIG)
        ConfigParse.Malformed -> ValueValidation.Invalid(ValueErrorKind.MALFORMED_CONFIG)
    }

    AttributeType.MULTI -> when (val parse = parseOptions(definition)) {
        is ConfigParse.Options -> {
            val parsed = runCatching { attributeConfigJson.decodeFromString<List<String>>(raw) }
                .getOrElse { return ValueValidation.Invalid(ValueErrorKind.MALFORMED_MULTI) }
            if (parsed.any { it !in parse.options }) {
                return ValueValidation.Invalid(ValueErrorKind.NOT_AN_OPTION)
            }
            ValueValidation.Valid(attributeConfigJson.encodeToString(parsed.distinct()))
        }
        ConfigParse.Missing -> ValueValidation.Invalid(ValueErrorKind.MISSING_CONFIG)
        ConfigParse.Malformed -> ValueValidation.Invalid(ValueErrorKind.MALFORMED_CONFIG)
    }

    AttributeType.COLOR -> {
        val candidate = raw.trim()
        if (colorHexRegex.matches(candidate)) {
            ValueValidation.Valid(candidate.uppercase())
        } else {
            ValueValidation.Invalid(ValueErrorKind.MALFORMED_COLOR)
        }
    }
}
