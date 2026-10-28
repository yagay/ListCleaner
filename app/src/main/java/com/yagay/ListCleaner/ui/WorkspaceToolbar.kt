package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/** Single point of control for Entry, Components, and Ordering toolbar geometry. */
internal object WorkspaceToolbarMetrics {
    val actionSize = 40.dp
    val actionSpacing = 0.dp
}

@Composable
internal fun WorkspaceToolbarActions(
    sort: (@Composable () -> Unit)?,
    filter: @Composable () -> Unit,
    selection: (@Composable () -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WorkspaceToolbarMetrics.actionSpacing)
    ) {
        sort?.invoke()
        filter()
        selection?.invoke()
    }
}

/** Shared square touch feedback for toolbar icon actions. */
@Composable
internal fun WorkspaceToolbarAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(WorkspaceToolbarMetrics.actionSize),
        shape = RectangleShape,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
