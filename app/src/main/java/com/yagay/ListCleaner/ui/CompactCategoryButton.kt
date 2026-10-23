package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Preserve the original compact TextButton geometry for category filters.
 * Selection changes its colour/outline only; it never adds a leading icon
 * or the larger FilterChip layout.
 */
@Composable
internal fun CompactCategoryButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val decoration = if (selected) {
        modifier
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        modifier
    }
    AdaptiveTextButton(
        onClick = onClick,
        modifier = decoration,
        contentPadding = MaterialUiTokens.compactButtonPadding,
        content = content
    )
}

@Composable
internal fun compactCategoryLabelColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.primary
