package com.yagay.ListCleaner.ui

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One page-matched background for Entry, Ordering, Root and their details. */
internal object WorkspaceListPalette {
    @Composable fun surface() = MaterialFlatPalette.surface()
    @Composable fun outline() = MaterialFlatPalette.outline()
}

/** Semantic variants preserve warnings and actions, without card decoration. */
internal enum class UiCardVariant { SECTION, CATEGORY, LIST_GROUP, STATUS }

/**
 * Cardless list/settings surface. Retains the same content slots and horizontal
 * alignment. Hairline dividers are painted instead of inserting layout rows.
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
    val dividerColor = WorkspaceListPalette.outline()
    val withDivider = !warning && !filterPalette &&
        (variant == UiCardVariant.LIST_GROUP || variant == UiCardVariant.CATEGORY)
    val container = when {
        warning -> MaterialTheme.colorScheme.errorContainer
        filterPalette -> MaterialSelectionPalette.categoryBackground()
        variant == UiCardVariant.STATUS -> MaterialFlatPalette.sectionSurface()
        else -> WorkspaceListPalette.surface()
    }
    val surfaceModifier = modifier.padding(horizontal = horizontalInset).then(
        if (withDivider) Modifier.drawWithContent {
            drawContent()
            val thickness = 0.75.dp.toPx()
            val y = size.height - thickness / 2f
            drawLine(dividerColor, Offset(0f, y), Offset(size.width, y), thickness)
        } else Modifier
    )
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = RectangleShape,
            color = container
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = RectangleShape,
            color = container
        ) { Column(content = content) }
    }
}

/** Shared selectable detail-row body; outer FlatRowSurface owns the background. */
@Composable
internal fun UiCardDetailRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = MaterialUiTokens.detailHeight)
            .padding(
                start = MaterialUiTokens.pageHorizontal + MaterialUiTokens.groupedDetailIndent,
                end = MaterialUiTokens.pageHorizontal + MaterialUiTokens.appExpandIndicatorWidth,
                top = MaterialUiTokens.detailVertical,
                bottom = MaterialUiTokens.detailVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
