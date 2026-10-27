package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One sticky surface for Cleanup's mode selector and category/filter controls.
 * All editors use this same header shell. Runtime alerts, help and list summaries
 * are deliberately separate scrolling items so that long screens stay usable.
 */
@Composable
internal fun WorkspaceStickyHeader(
    mode: ClassicCleanupMode? = null,
    onEntries: () -> Unit = {},
    onComponents: () -> Unit = {},
    filters: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (mode != null) {
            ClassicCleanupTabs(
                active = mode,
                onEntries = onEntries,
                onComponents = onComponents
            )
        }
        filters()
    }
}
