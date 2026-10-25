package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shared full-width app header used by Entry, Root and Ordering. */
@Composable
internal fun ContinuousApplicationSurface(content: @Composable () -> Unit) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.pageHorizontal,
        content = content
    )
}

/** Subtly tinted component details under the parent app row, edge-aligned. */
@Composable
internal fun ContinuousDetailSurface(
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.pageHorizontal,
        color = if (selected) MaterialFlatPalette.selectedDetailSurface()
            else MaterialFlatPalette.detailSurface(),
        content = content
    )
}
