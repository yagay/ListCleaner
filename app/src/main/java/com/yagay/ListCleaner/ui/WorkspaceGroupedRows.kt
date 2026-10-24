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

/** Recessed, subtly tinted component details under the parent app row. */
@Composable
internal fun ContinuousDetailSurface(content: @Composable () -> Unit) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.pageHorizontal + MaterialUiTokens.groupedDetailIndent,
        color = MaterialFlatPalette.detailSurface(),
        content = content
    )
}
