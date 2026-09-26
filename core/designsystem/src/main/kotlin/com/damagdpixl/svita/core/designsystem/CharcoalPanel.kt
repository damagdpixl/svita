package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Rounded charcoal container — the collage panel.
 * Content sits on `#2A2A2A` with cream text in both themes.
 */
@Composable
fun CharcoalPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = CharcoalPanel,
        contentColor = Cream,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}
