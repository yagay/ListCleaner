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
    val top = if (this == ListRowPosition.SINGLE || this == ListRowPosition.FIRST)
        MaterialUiTokens.cardRadius else MaterialUiTokens.groupedInnerRadius
    val bottom = if (this == ListRowPosition.SINGLE || this == ListRowPosition.LAST)
        MaterialUiTokens.cardRadius else MaterialUiTokens.groupedInnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

internal fun ListRowPosition.verticalPadding(): PaddingValues = PaddingValues(
    top = if (this == ListRowPosition.SINGLE || this == ListRowPosition.FIRST)
        MaterialUiTokens.groupedOuterGap else MaterialUiTokens.groupedInnerGap,
    bottom = if (this == ListRowPosition.SINGLE || this == ListRowPosition.LAST)
        MaterialUiTokens.groupedOuterGap else MaterialUiTokens.groupedInnerGap
)

@Composable
internal fun PageSectionTitle(title: String) {
    AdaptiveText(
        title, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 2
    )
}

@Composable
internal fun WorkspaceSectionTitle(title: String) = PageSectionTitle(title)

@Composable
internal fun PageFilterSection(content: @Composable () -> Unit) {
    AdaptiveSurface(tonalElevation = 2.dp, content = content)
}
