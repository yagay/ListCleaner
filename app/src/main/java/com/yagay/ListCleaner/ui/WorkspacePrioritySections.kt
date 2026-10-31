package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/**
 * Section boundary contract shared by Entry Priority, Root Tiles and
 * Settings Apps ordering. Apply it to the already-filtered, rank-first list
 * so hidden/filtered rows never leave an empty or misleading section header.
 */
internal fun <T> startsPrioritySection(
    visible: List<T>,
    index: Int,
    isPriority: (T) -> Boolean,
): Boolean = index in visible.indices &&
    (index == 0 || isPriority(visible[index - 1]) != isPriority(visible[index]))

@Composable
internal fun WorkspacePrioritySectionHeading(isPriority: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.groupHorizontal)) {
        PageSectionTitle(
            stringResource(
                if (isPriority) R.string.priority_selected else R.string.priority_unselected
            )
        )
    }
}
