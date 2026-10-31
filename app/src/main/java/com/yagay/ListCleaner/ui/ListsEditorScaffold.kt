package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single layout for all Lists V2 editors. Unlike the previous four independent
 * Column/LazyColumn implementations, controls, scroll and commit placement
 * have exactly one owner. The content provider supplies typed rows only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListsEditorScaffold(
    controls: @Composable () -> Unit,
    summary: @Composable () -> Unit,
    pending: @Composable () -> Unit,
    rows: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            stickyHeader(key = "lists-v2-controls") {
                WorkspaceStickyHeader(filters = controls)
            }
            item(key = "lists-v2-summary") { summary() }
            rows()
        }
        // Commit controls are never appended to the data list or scrolled away.
        pending()
    }
}
