package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.Composable

/**
 * Compatibility adapters intentionally delegate to native Material 3 colors.
 * Never override an indicator, disabled color or selected chip palette here.
 */
/** Single semantic palette for interactive controls. Change these values for a future theme. */
internal object UiColorRoles {
    val keys = setOf("buttonBackground", "buttonText", "buttonOutline",
        "selectedBackground", "selectedText", "categoryBackground",
        "filterBackground", "navigationBackground", "navigationText")
    @Composable
    fun resolve(key: String, default: Color): Color {
        val effectiveKey = if (LocalListCleanerDarkTheme.current) "dark_$key" else key
        val hex = UiPreferencesRuntime.current.colorOverrides[effectiveKey] ?: return default
        return androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    }
}

/**
 * Semantic text colors must remain distinct from selected control backgrounds.
 * In custom-accent themes tertiary is deliberately mapped to primary, so it
 * cannot be used for non-selected informational status text.
 */
internal object MaterialStatusPalette {
    @Composable
    fun informationalText(): Color =
        if (LocalListCleanerDarkTheme.current) Color(0xFF80DFD0)
        else Color(0xFF006C62)
}

/**
 * Unified flat surfaces across existing screens. These are paint-only tokens:
 * callers retain their shape bounds, padding, position and click behaviour.
 */
internal object MaterialFlatPalette {
    // Unselected list and settings rows blend into the page; selected controls
    // retain the current Material theme highlight.
    @Composable fun surface(): Color = MaterialTheme.colorScheme.surface
    @Composable fun sectionSurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow
    @Composable fun detailSurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow
    @Composable
    fun selectedDetailSurface(): Color =
        MaterialSelectionPalette.activeBackground()
            .copy(alpha = if (LocalListCleanerDarkTheme.current) 0.24f else 0.16f)
            .compositeOver(detailSurface())
    @Composable fun outline(): Color = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (LocalListCleanerDarkTheme.current) 0.42f else 0.30f
    )

    @Composable
    fun selectedSurface(): Color {
        // Preserve explicit user overrides instead of silently washing them out.
        val key = if (LocalListCleanerDarkTheme.current) "dark_selectedBackground" else "selectedBackground"
        val selected = MaterialSelectionPalette.activeBackground()
        return if (key in UiPreferencesRuntime.current.colorOverrides) selected
        else selected.copy(alpha = if (LocalListCleanerDarkTheme.current) 0.70f else 0.44f)
            .compositeOver(surface())
    }
}

internal object MaterialSelectionPalette {
    @Composable fun background(): Color = UiColorRoles.resolve("buttonBackground", MaterialTheme.colorScheme.surface)
    @Composable fun outline(): Color = UiColorRoles.resolve("buttonOutline", MaterialTheme.colorScheme.outline)
    @Composable fun text(): Color = UiColorRoles.resolve("buttonText", MaterialTheme.colorScheme.onSurfaceVariant)
    @Composable fun activeBackground(): Color = UiColorRoles.resolve("selectedBackground", MaterialTheme.colorScheme.secondaryContainer)
    @Composable fun activeText(): Color = UiColorRoles.resolve("selectedText", MaterialTheme.colorScheme.onSecondaryContainer)
    @Composable fun title(): Color = MaterialTheme.colorScheme.onSurface
    @Composable fun categoryBackground(): Color = UiColorRoles.resolve("categoryBackground", background())
    @Composable fun filterBackground(): Color = UiColorRoles.resolve("filterBackground", background())
    @Composable fun navigationBackground(): Color = UiColorRoles.resolve("navigationBackground", activeBackground())
    @Composable fun navigationText(): Color = UiColorRoles.resolve("navigationText", activeText())
}

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
    @Composable fun navigation() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialSelectionPalette.navigationText(),
        selectedTextColor = MaterialSelectionPalette.navigationText(),
        indicatorColor = Color.Transparent,
        unselectedIconColor = MaterialSelectionPalette.text(),
        unselectedTextColor = MaterialSelectionPalette.text()
    )

}
