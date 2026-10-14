package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One shared row grouping/layout policy for every Material 3 list. */
internal enum class ListRowPosition { SINGLE, FIRST, MIDDLE, LAST }

internal fun ListRowPosition.shape(): RoundedCornerShape {
    val top = if (this == ListRowPosition.SINGLE || this == ListRowPosition.FIRST) 20.dp else 4.dp
    val bottom = if (this == ListRowPosition.SINGLE || this == ListRowPosition.LAST) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

internal fun ListRowPosition.verticalPadding(): PaddingValues = PaddingValues(
    top = if (this == ListRowPosition.SINGLE || this == ListRowPosition.FIRST) 4.dp else 1.dp,
    bottom = if (this == ListRowPosition.SINGLE || this == ListRowPosition.LAST) 4.dp else 1.dp
)

@Composable
internal fun PageSectionTitle(title: String) {
    AdaptiveText(
        title, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 2
    )
}

@Composable
internal fun WorkspaceSectionTitle(title: String) {
    AdaptiveText(
        text = title, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 2
    )
}

@Composable
internal fun PageFilterSection(content: @Composable () -> Unit) {
    AdaptiveSurface(tonalElevation = 2.dp, content = content)
}
