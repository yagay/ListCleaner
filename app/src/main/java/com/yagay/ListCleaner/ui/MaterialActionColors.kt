package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

/**
 * Compatibility adapters intentionally delegate to native Material 3 colors.
 * Never override an indicator, disabled color or selected chip palette here.
 */
/** Single semantic palette for interactive controls. Change these values for a future theme. */
internal object MaterialSelectionPalette {
    @Composable fun background(): Color = MaterialTheme.colorScheme.surface
    @Composable fun outline(): Color = MaterialTheme.colorScheme.outline
    @Composable fun text(): Color = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun activeBackground(): Color = MaterialTheme.colorScheme.secondaryContainer
    @Composable fun activeText(): Color = MaterialTheme.colorScheme.onSecondaryContainer
    @Composable fun title(): Color = MaterialTheme.colorScheme.onSurface
}

internal object MaterialActionColors {
    @Composable fun segmented() = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialSelectionPalette.activeBackground(),
        activeContentColor = MaterialSelectionPalette.activeText(),
        inactiveContainerColor = MaterialSelectionPalette.background(),
        inactiveContentColor = MaterialSelectionPalette.text()
    )
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
    @Composable fun navigation() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialSelectionPalette.activeText(),
        selectedTextColor = MaterialSelectionPalette.title(),
        indicatorColor = MaterialSelectionPalette.activeBackground(),
        unselectedIconColor = MaterialSelectionPalette.text(),
        unselectedTextColor = MaterialSelectionPalette.text()
    )
    @Composable fun filter() = FilterChipDefaults.filterChipColors()
}
