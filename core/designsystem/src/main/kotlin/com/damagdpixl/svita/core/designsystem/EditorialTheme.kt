package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Marigold,
    onPrimary = Charcoal,
    primaryContainer = Cream,
    onPrimaryContainer = Charcoal,
    secondary = Orange,
    onSecondary = Charcoal,
    secondaryContainer = Cream,
    onSecondaryContainer = Charcoal,
    tertiary = ActionGreen,
    onTertiary = Charcoal,
    background = White,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    surfaceVariant = Cream,
    onSurfaceVariant = Charcoal.copy(alpha = 0.72f),
    outline = Charcoal.copy(alpha = 0.40f),
)

private val DarkColors = darkColorScheme(
    primary = Marigold,
    onPrimary = Charcoal,
    primaryContainer = CharcoalPanel,
    onPrimaryContainer = Cream,
    secondary = Orange,
    onSecondary = Charcoal,
    secondaryContainer = CharcoalPanel,
    onSecondaryContainer = Cream,
    tertiary = ActionGreen,
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = Cream,
    surface = CharcoalPanel,
    onSurface = Cream,
    surfaceVariant = CharcoalPanel,
    onSurfaceVariant = Cream.copy(alpha = 0.72f),
    outline = Cream.copy(alpha = 0.40f),
)

/**
 * «Editorial Collage» theme: Material 3 [MaterialTheme] restyled with the
 * Svita tokens — cream/yellow-led light scheme, charcoal/yellow-led dark scheme,
 * Playfair Display headlines and JetBrains Mono control labels.
 */
@Composable
fun EditorialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EditorialTypography,
        shapes = EditorialShapes,
        content = content,
    )
}
