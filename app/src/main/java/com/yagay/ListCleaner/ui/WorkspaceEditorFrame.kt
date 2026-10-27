package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared editor viewport for Entry, Root and Ordering.
 *
 * The pending action bar participates in measure/layout instead of covering
 * list content using an assumed 90dp height. This also works with font scale,
 * large touch targets, and navigation insets handled by the parent Scaffold.
 *
 * A BoxScope is intentional: Ordering's dragged-item overlay stays in the same
 * scroll viewport, while the action bar is always measured below it.
 */
@Composable
internal fun WorkspaceEditorFrame(
    pendingCount: Int,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        PendingChangesBar(
            count = pendingCount,
            onDiscard = onDiscard,
            onApply = onApply
        )
    }
}
