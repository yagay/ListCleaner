package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One scroll/confirmation layout shared by Entry, Root and managed surfaces.
 *
 * Each engine only supplies its own rows and a commit callback; Root component
 * state and Entry/Surface rules never share a persistence transaction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnifiedCleanupList(
    pendingCount: Int,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (pendingCount > 0) 90.dp else 16.dp),
            content = content,
        )
        PendingChangesBar(
            count = pendingCount,
            onDiscard = onDiscard,
            onApply = onApply,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
