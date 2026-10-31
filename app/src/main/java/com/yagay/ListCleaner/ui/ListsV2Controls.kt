package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.yagay.ListCleaner.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.AppTypeFilter

/**
 * Lists V2 control row. The category picker exists only for a real choice;
 * fixed-kind screens do not expose the old inert "All" dropdown.
 * Geometry, filter semantics and batch menu come from Entry/Root controls.
 */
@Composable
internal fun ListsV2Controls(
    category: String,
    options: List<Pair<String, String>>,
    onCategory: (String) -> Unit,
    filter: UiFilter,
    onFilter: (UiFilter) -> Unit,
    actions: List<WorkspaceMenuAction> = emptyList(),
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    onAppTypeFilter: (AppTypeFilter) -> Unit = {},
    showAppTypeFilter: Boolean = false,
    sortSupported: Boolean = false,
    sorting: Boolean = false,
    onToggleSort: () -> Unit = {},
) {
    val context = LocalContext.current
    var categoryExpanded by remember { mutableStateOf(false) }
    WorkspaceCategoryFilterRow(
        showCategory = options.size > 1,
        category = {
            if (options.size > 1) {
                Box {
                    WorkspacePickerButton(
                        label = options.firstOrNull { it.first == category }?.second
                            ?: options.first().second,
                        icon = Icons.Rounded.Layers,
                        selected = category != options.first().first,
                        expanded = categoryExpanded,
                        onClick = { categoryExpanded = true },
                    )
                    FlatWorkspaceDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        menuWidth = 256.dp,
                    ) {
                        options.forEach { (id, title) ->
                            WorkspaceFlatMenuRow(
                                title = title,
                                selected = id == category,
                                onClick = {
                                    categoryExpanded = false
                                    onCategory(id)
                                },
                            )
                        }
                    }
                }
            }
        },
        beforeFilter = {
            WorkspaceToolbarAction(
                onClick = {
                    if (sortSupported) onToggleSort()
                    else Toast.makeText(context, R.string.priority_unsupported_current_list,
                        Toast.LENGTH_SHORT).show()
                },
                active = sorting,
            ) {
                AdaptiveIcon(
                    Icons.AutoMirrored.Rounded.Sort,
                    stringResource(R.string.nav_priority),
                    modifier = androidx.compose.ui.Modifier.size(22.dp),
                    tint = if (!sortSupported)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else if (sorting) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        filter = {
            WorkspaceFilterBar(
                appTypeFilter = appTypeFilter,
                onAppTypeFilter = onAppTypeFilter,
                showAppTypeFilter = showAppTypeFilter,
                viewFilter = filter,
                onViewFilter = onFilter,
            )
        },
        afterFilter = { WorkspaceListActions(actions = actions) },
    )
}
