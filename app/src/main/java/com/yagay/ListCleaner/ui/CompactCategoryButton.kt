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
 * highlight is drawn only around the inner label row (not the 40-48dp target).
 * This prevents the selected option from looking taller than its neighbours.
 */
@Composable
internal fun CompactCategoryButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    AdaptiveTextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .then(if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, shape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else Modifier)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
internal fun compactCategoryLabelColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.primary
