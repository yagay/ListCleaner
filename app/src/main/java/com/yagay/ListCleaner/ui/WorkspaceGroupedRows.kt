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
            // Draw over the surface rather than adding padding or shifting the row.
            val verticalInset = 8.dp.toPx()
            drawLine(
                color = selectionColor,
                start = Offset(2.dp.toPx(), verticalInset),
                end = Offset(2.dp.toPx(), size.height - verticalInset),
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
