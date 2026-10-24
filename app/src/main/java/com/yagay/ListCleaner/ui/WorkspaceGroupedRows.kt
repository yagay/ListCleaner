package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Visual-only grouped list. App and component remain independent LazyColumn items. */
@Composable
internal fun GroupedApplicationSurface(expanded: Boolean, content: @Composable () -> Unit) {
    val shape = if (expanded) RoundedCornerShape(
        topStart = MaterialUiTokens.listCardRadius, topEnd = MaterialUiTokens.listCardRadius
    ) else RoundedCornerShape(MaterialUiTokens.listCardRadius)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = MaterialUiTokens.groupHorizontal)
            .padding(top = MaterialUiTokens.groupedOuterGap),
        shape = shape,
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
        shape = if (last) RoundedCornerShape(
            bottomStart = MaterialUiTokens.listCardRadius,
            bottomEnd = MaterialUiTokens.listCardRadius
        ) else RoundedCornerShape(0.dp),
        // Component and expanded app header share one surface color; selection
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
