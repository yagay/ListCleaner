package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter

/**
 * Entry-facing controls shared by the Hook-backed Settings Apps and Notification
 * Cleanup editors. Root component selection is deliberately not exposed here.
 */
@Composable
internal fun EntryManagedSurfaceToolbar(
    surface: ManagedComponentSurface,
    appTypeFilter: AppTypeFilter,
    onAppTypeFilter: (AppTypeFilter) -> Unit,
    viewFilter: UiFilter,
    onViewFilter: (UiFilter) -> Unit,
    onEntry: () -> Unit,
    onManagedSurface: (ManagedComponentSurface) -> Unit,
    onOpenSort: () -> Unit,
    canSort: Boolean,
    actions: List<WorkspaceMenuAction>,
) {
    WorkspaceCategoryFilterRow(
        category = {
            EntryCategoryPicker(
                selected = null,
                managedSurface = surface,
                onSelected = { onEntry() },
                onManagedSurface = onManagedSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        beforeFilter = {
            WorkspaceSortModeAction(
                supported = canSort,
                onOpen = onOpenSort,
            )
        },
        filter = {
            WorkspaceFilterBar(
                appTypeFilter = appTypeFilter,
                onAppTypeFilter = onAppTypeFilter,
                viewFilter = viewFilter,
                onViewFilter = onViewFilter,
                viewTitle = { filter ->
                    stringResource(when (filter) {
                        UiFilter.ALL -> R.string.common_all
                        UiFilter.SHOW_SELECTED -> R.string.lc_hook_filter_selected
                        UiFilter.HIDE_SELECTED -> R.string.lc_hook_filter_unselected
                        UiFilter.LOCKED -> R.string.filter_locked
                    })
                },
            )
        },
        afterFilter = {
            WorkspaceListActions(actions = actions)
        },
    )
}
