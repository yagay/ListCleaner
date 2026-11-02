package com.yagay.ListCleaner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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

/** Selected details keep their background highlight and gain a slim leading rail. */
@Composable
internal fun ContinuousDetailSurface(
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val selectionColor = MaterialTheme.colorScheme.primary
    FlatRowSurface(
        modifier = if (selected) Modifier.drawWithContent {
            drawContent()
            // The shared detail title starts after the 48dp selection slot.
            // Place the rail directly before that title, not at the screen edge.
            // Paint in place so text, icons and hit targets do not shift.
            val verticalInset = 8.dp.toPx()
            val railCenterX = MaterialUiTokens.iconTouchTarget.toPx() - 4.dp.toPx()
            drawLine(
                color = selectionColor,
                start = Offset(railCenterX, verticalInset),
                end = Offset(railCenterX, size.height - verticalInset),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else Modifier,
        dividerInset = MaterialUiTokens.pageHorizontal,
        color = if (selected) MaterialFlatPalette.selectionHighlightSurface()
            else MaterialFlatPalette.surface(),
        content = content
    )
}
