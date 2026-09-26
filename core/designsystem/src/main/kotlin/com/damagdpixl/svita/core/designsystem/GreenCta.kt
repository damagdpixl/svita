package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The primary action look: flat action-green shape with a mono uppercase label
 * and charcoal text — no elevation, pure editorial flatness.
 */
@Composable
fun GreenCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionGreen,
            contentColor = Charcoal,
            disabledContainerColor = ActionGreen.copy(alpha = 0.40f),
            disabledContentColor = Charcoal.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
