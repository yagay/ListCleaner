package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single row surface for app headers and component details.
 *
 * Geometry belongs to the wrapper; the content owns checkboxes, labels,
 * menus, drag handles, and click/lock handling. Separators are drawn rather
 * than inserted as layout children, so they never shift row contents.
 */
@Composable
private fun WorkspaceListRowSurface(
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp,
    separatorInset: Dp = 0.dp,
    showSeparator: Boolean = true,
    content: @Composable () -> Unit,
) {
    val lineColor = WorkspaceListPalette.outline()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .then(
                if (showSeparator) Modifier.drawWithContent {
                    drawContent()
                    val stroke = 0.75.dp.toPx()
                    val inset = separatorInset.toPx().coerceAtMost(size.width / 2f)
                    val bottom = size.height - stroke / 2f
                    drawLine(
                        lineColor,
                        Offset(inset, bottom),
                        Offset(size.width - inset, bottom),
                        stroke
                    )
                } else Modifier
            ),
        shape = RectangleShape,
        color = WorkspaceListPalette.surface()
    ) { content() }
}

/** Full-width app header, inset hairline divider. */
@Composable
internal fun ContinuousApplicationSurface(content: @Composable () -> Unit) {
    WorkspaceListRowSurface(
        separatorInset = MaterialUiTokens.groupHorizontal,
        content = content
    )
}

/**
 * Same row surface for expanded component details. Keep the original
 * horizontal inset; headings, checkboxes and trailing actions are unchanged.
 */
@Composable
internal fun ContinuousDetailSurface(content: @Composable () -> Unit) {
    WorkspaceListRowSurface(
        horizontalInset = MaterialUiTokens.groupHorizontal,
        content = content
    )
}

/** Visual-only grouped alternative; component rows remain independent LazyColumn items. */
@Composable
internal fun GroupedApplicationSurface(expanded: Boolean, content: @Composable () -> Unit) {
    WorkspaceListRowSurface(
        modifier = Modifier.padding(top = MaterialUiTokens.groupedOuterGap),
        horizontalInset = MaterialUiTokens.groupHorizontal,
        showSeparator = false    ) {
        Column {
            content()
            if (expanded) HorizontalDivider(color = WorkspaceListPalette.outline())
        }
    }
}

@Composable
internal fun GroupedComponentSurface(position: ListRowPosition, selected: Boolean, content: @Composable () -> Unit) {
    val last = position == ListRowPosition.LAST
    WorkspaceListRowSurface(
        horizontalInset = MaterialUiTokens.groupHorizontal,
        showSeparator = false    ) {
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
