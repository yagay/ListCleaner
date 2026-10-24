package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The same low-contrast material for app cards and their expanded component
 * rows across Cleanup, Ordering and Root. Only appearance changes: existing
 * card shapes, paddings, row measures and list structure remain untouched.
 */
internal object WorkspaceListPalette {
    @Composable fun surface() = MaterialFlatPalette.surface()
    @Composable fun outline() = MaterialFlatPalette.outline()
}

/** Four visual purposes, one Material 3 Card implementation. */
internal enum class UiCardVariant { SECTION, CATEGORY, LIST_GROUP, STATUS }

/**
 * Central card surface: color, shape, grouping gaps, elevation and warning borders
 * are resolved here. Callers supply content and feature-specific interactions.
 *
 * Inset is only applied by default to uncontained list rows. A list already inside
 * MaterialPageColumn must use horizontalInset = 0.dp to avoid doubled margins.
 */
@Composable
internal fun UiCard(
    modifier: Modifier = Modifier,
    variant: UiCardVariant = UiCardVariant.SECTION,
    position: ListRowPosition = ListRowPosition.SINGLE,
    horizontalInset: Dp = if (variant == UiCardVariant.LIST_GROUP) MaterialUiTokens.groupHorizontal else 0.dp,
    warning: Boolean = false,
    filterPalette: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // A single visual radius; the Card modifier and content geometry stay intact.
    val shape = RoundedCornerShape(MaterialUiTokens.listCardRadius)
    val colors = CardDefaults.cardColors(
        containerColor = when {
            warning -> MaterialTheme.colorScheme.errorContainer
            filterPalette -> MaterialSelectionPalette.categoryBackground()
            variant == UiCardVariant.CATEGORY || variant == UiCardVariant.LIST_GROUP ->
                WorkspaceListPalette.surface()
            else -> MaterialFlatPalette.sectionSurface()
        }
    )
    val border = when {
        warning -> CardDefaults.outlinedCardBorder()
        filterPalette -> BorderStroke(1.dp, MaterialSelectionPalette.outline())
        else -> BorderStroke(0.5.dp, MaterialFlatPalette.outline())
    }
    // Zero tonal/shadow elevation for every card variant (tap ripple retained).
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp
    )
    val surfaceModifier = modifier.padding(
        horizontal = horizontalInset,
        vertical = if (variant == UiCardVariant.LIST_GROUP) 2.dp else 0.dp
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            content = content
        )
    } else {
        Card(
            modifier = surfaceModifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            content = content
        )
    }
}

/** Shared detail-row surface inside a list-group card, read-only or selectable. */
@Composable
internal fun UiCardDetailRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.ListItem(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = WorkspaceListPalette.surface()
        ),
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    )
}
