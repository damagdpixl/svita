package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Flat cream menu bar: leading icon slot + mono uppercase label,
 * with optional trailing content pushed to the end.
 */
@Composable
fun CreamMenuBar(
    label: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = Cream,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = Charcoal,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Charcoal,
            )
            Spacer(modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}
