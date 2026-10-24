package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Visual-only grouped list. App and component remain independent LazyColumn items. */
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
