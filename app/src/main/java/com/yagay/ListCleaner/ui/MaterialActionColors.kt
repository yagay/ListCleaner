package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One theme accent for filled, outlined, text, icon and selected-filter actions.
 * Hierarchy stays visible without separate default palettes per component.
 */
internal object MaterialActionColors {
    @Composable
    fun filled() = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    @Composable
    fun outlined() = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary
    )

    @Composable
    fun outlinedBorder(enabled: Boolean): BorderStroke = BorderStroke(
        width = 1.dp,
        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )

    @Composable
    fun text() = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary
    )

    @Composable
    fun icon() = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.primary
    )

    @Composable
    fun filter() = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.primary,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
    )
}
