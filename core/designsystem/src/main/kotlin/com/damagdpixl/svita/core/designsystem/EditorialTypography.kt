@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.damagdpixl.svita.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial voice pairing: Playfair Display (serif) for headlines,
 * JetBrains Mono (uppercase) for controls, labels and buttons.
 *
 * Playfair Display ships as a variable font; each weight is exposed through
 * explicit variation settings (safe on minSdk 26+).
 */
val PlayfairDisplayFamily: FontFamily = FontFamily(
    Font(
        R.font.playfair_display_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.playfair_display_variable,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.playfair_display_variable,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.playfair_display_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.playfair_display_variable,
        FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
)

val JetBrainsMonoFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Material 3 [Typography] restyled for «Editorial Collage»:
 * display/headline/title use Playfair Display, labels use JetBrains Mono
 * with wide tracking for the uppercase control voice.
 */
val EditorialTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = PlayfairDisplayFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        ),
    )
}
