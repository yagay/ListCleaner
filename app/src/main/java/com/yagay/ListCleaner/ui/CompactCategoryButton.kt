package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The clickable TextButton keeps its Material3 minimum touch target, but the
 * primary-filled highlight is drawn only around the inner label row (not the 40-48dp target).
 * This prevents the selected option from looking taller than its neighbours.
 */
@Composable
internal fun CompactCategoryButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    AdaptiveTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface, shape
                )
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant, shape
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
internal fun compactCategoryLabelColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
