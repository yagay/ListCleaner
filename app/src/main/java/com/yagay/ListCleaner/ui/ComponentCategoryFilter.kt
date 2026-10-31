package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ListCleaner.data.CleanupKind

/**
 * Component-category filtering changes the visible Root list only.
 * Category selection now delegates to the shared Material 3 bottom sheet.
 */
internal object ComponentCategoryFilterPolicy {
    val choices: List<CleanupKind?> = listOf(null) + CleanupKind.entries
    fun matches(selected: CleanupKind?, candidate: CleanupKind): Boolean =
        selected == null || selected == candidate
}

@Composable
internal fun ClassicComponentCategoryFilter(
    selected: CleanupKind?,
    onSelected: (CleanupKind?) -> Unit,
    modifier: Modifier = Modifier,
    previewActions: List<WorkspaceMenuAction> = emptyList(),
    onManagedSurface: (ManagedComponentSurface) -> Unit = {},
) {
    ComponentCategoryPicker(
        selected, onSelected, modifier, previewActions,
        onManagedSurface = onManagedSurface,
    )
}
