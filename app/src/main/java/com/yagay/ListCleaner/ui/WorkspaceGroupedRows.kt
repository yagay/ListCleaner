package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** App headers and component details reuse the one flat row primitive. */
/** Full-width app header, inset hairline divider. */
@Composable
internal fun ContinuousApplicationSurface(content: @Composable () -> Unit) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.groupHorizontal,
        content = content
    )
}

/**
 * Same row surface for expanded component details. Keep the original
 * horizontal inset; headings, checkboxes and trailing actions are unchanged.
 */
@Composable
internal fun ContinuousDetailSurface(content: @Composable () -> Unit) {
    FlatRowSurface(
        horizontalInset = MaterialUiTokens.groupHorizontal,
        dividerInset = 0.dp,
        color = MaterialFlatPalette.detailSurface(),
        content = content
    )
}

/** Visual-only grouped alternative; component rows remain independent LazyColumn items. */
@Composable
internal fun GroupedApplicationSurface(expanded: Boolean, content: @Composable () -> Unit) {
    FlatRowSurface(
        modifier = Modifier.padding(top = MaterialUiTokens.groupedOuterGap),
        horizontalInset = MaterialUiTokens.groupHorizontal,
        showDivider = false
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
    FlatRowSurface(
        horizontalInset = MaterialUiTokens.groupHorizontal,
        showDivider = false,
        color = MaterialFlatPalette.detailSurface()
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
