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
    @Composable fun surface() = MaterialTheme.colorScheme.surfaceContainerLowest
    @Composable fun outline() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
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
    val shape = when (variant) {
        UiCardVariant.CATEGORY -> MaterialTheme.shapes.large
        UiCardVariant.LIST_GROUP -> RoundedCornerShape(MaterialUiTokens.listCardRadius)
        UiCardVariant.SECTION, UiCardVariant.STATUS -> MaterialTheme.shapes.medium
    }
    val colors = CardDefaults.cardColors(
        containerColor = when {
            warning -> MaterialTheme.colorScheme.errorContainer
            filterPalette -> MaterialSelectionPalette.categoryBackground()
            variant == UiCardVariant.CATEGORY -> MaterialTheme.colorScheme.surfaceContainer
            variant == UiCardVariant.LIST_GROUP -> WorkspaceListPalette.surface()
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
    )
    val border = if (warning) CardDefaults.outlinedCardBorder()
        else if (filterPalette) BorderStroke(1.dp, MaterialSelectionPalette.outline())
        else if (variant == UiCardVariant.LIST_GROUP)
            BorderStroke(0.5.dp, WorkspaceListPalette.outline())
        else null
    val elevation = if (variant == UiCardVariant.LIST_GROUP)
        CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp
        )
        else CardDefaults.cardElevation()
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
