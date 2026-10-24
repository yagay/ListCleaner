package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable

/**
 * Compatibility adapters intentionally delegate to native Material 3 colors.
 * Never override an indicator, disabled color or selected chip palette here.
 */
internal object MaterialActionColors {
    @Composable fun filled() = ButtonDefaults.buttonColors()
    @Composable fun outlined() = ButtonDefaults.outlinedButtonColors()
    // Destructive actions use the semantic error palette, never an unrelated accent.
    @Composable fun dangerOutlined() = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error
    )
    @Composable fun dangerOutlinedBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    @Composable fun outlinedBorder(enabled: Boolean) = ButtonDefaults.outlinedButtonBorder(enabled)
    @Composable fun text() = ButtonDefaults.textButtonColors()
    @Composable fun icon() = IconButtonDefaults.iconButtonColors()
    @Composable fun navigation() = NavigationBarItemDefaults.colors()
    @Composable fun filter() = FilterChipDefaults.filterChipColors()
}
