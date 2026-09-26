package com.damagdpixl.svita.core.designsystem

import java.util.Locale

/**
 * Uppercase in the root locale — the uppercase mono control voice of the
 * «Editorial Collage» design system. Locale.ROOT avoids locale-specific
 * mappings (e.g. the Turkish dotless-i) corrupting UI labels.
 */
fun String.monoUpper(): String = uppercase(Locale.ROOT)
