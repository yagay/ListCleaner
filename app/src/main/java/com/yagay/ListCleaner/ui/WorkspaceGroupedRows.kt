package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The default app-list surface. App headers fill the available width and use
 * a subtle inset separator, rather than retaining an old card container.
 *
 * Only app headers use this. Expanded Entry/Root/Priority component rows still
 * flow through WorkspaceDetailRow and WorkspaceReadOnlyDetailRow, retaining
 * their existing insets, hit targets, locking and selection controls.
 */
@Composable
internal fun ContinuousApplicationSurface(content: @Composable () -> Unit) {
    val lineColor = WorkspaceListPalette.outline()
    val inset = MaterialUiTokens.groupHorizontal
    Surface(
        modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            val stroke = 0.75.dp.toPx()
            val x = inset.toPx().coerceAtMost(size.width / 2f)
            val bottom = size.height - stroke / 2f
            drawLine(lineColor, Offset(x, bottom), Offset(size.width - x, bottom), stroke)
        },
        shape = RectangleShape,
        color = WorkspaceListPalette.surface()
    ) { content() }
}

/** Visual-only grouped alternative; component rows remain independent LazyColumn items. */
@Composable
internal fun GroupedApplicationSurface(expanded: Boolean, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = MaterialUiTokens.groupHorizontal)
            .padding(top = MaterialUiTokens.groupedOuterGap),
        shape = RectangleShape,
        color = WorkspaceListPalette.surface()
    ) {
        Column {
            content()
            if (expanded) HorizontalDivider(color = WorkspaceListPalette.outline())
        }
    }
}

@Composable
internal fun GroupedComponentSurface(position: ListRowPosition, selected: Boolean, content: @Composable () -> Unit) {
    val last = position == ListRowPosition.LAST
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(start = MaterialUiTokens.groupHorizontal,
                end = MaterialUiTokens.groupHorizontal),
        shape = RectangleShape,
        // Components and expanded app header share the page background; selection
        // remains visible through the trailing checked icon.
        color = WorkspaceListPalette.surface()
    ) {
        Column {
            content()
            // Visible inset separator between siblings; no divider after the last row.
            if (!last) HorizontalDivider(
                modifier = Modifier.padding(
                    start = MaterialUiTokens.groupedDetailStartPadding,
                    end = MaterialUiTokens.groupedDetailEndPadding
                ),
                thickness = 1.dp,
                color = WorkspaceListPalette.outline()
            )
        }
    }
}
