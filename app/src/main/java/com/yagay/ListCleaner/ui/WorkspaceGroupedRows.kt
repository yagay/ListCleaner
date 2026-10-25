package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** App rows highlight only while expanded, using the category-menu highlight. */
@Composable
internal fun ContinuousApplicationSurface(
    expanded: Boolean,
    content: @Composable () -> Unit
) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.pageHorizontal,
        color = if (expanded) MaterialFlatPalette.selectionHighlightSurface()
            else MaterialFlatPalette.surface(),
        content = content
    )
}

/** Flat components gain the same highlight only when selected. */
@Composable
internal fun ContinuousDetailSurface(
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    FlatRowSurface(
        dividerInset = MaterialUiTokens.pageHorizontal,
        color = if (selected) MaterialFlatPalette.selectionHighlightSurface()
            else MaterialFlatPalette.surface(),
        content = content
    )
}
