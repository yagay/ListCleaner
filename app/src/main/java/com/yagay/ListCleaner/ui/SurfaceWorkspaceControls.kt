package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Layers
import com.yagay.ListCleaner.domain.AppTypeFilter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/**
 * Reuse Entry/Root's flat picker, selected glyph, overflow and selection menu.
 * No settings switches: checked means a staged cleanup choice, not a preference.
 */
@Composable
internal fun SurfaceWorkspaceToolbar(
    category: String,
    options: List<Pair<String, String>>,
    onCategory: (String) -> Unit,
    filter: UiFilter,
    onFilter: (UiFilter) -> Unit,
    actions: List<WorkspaceMenuAction> = emptyList(),
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    onAppTypeFilter: (AppTypeFilter) -> Unit = {},
    showAppTypeFilter: Boolean = false,
) {
    var categoryOpen by remember { mutableStateOf(false) }
    WorkspaceCategoryFilterRow(
        category = {
            Box {
                WorkspacePickerButton(
                    label = options.firstOrNull { it.first == category }?.second ?: category,
                    icon = Icons.Rounded.Layers,
                    selected = category != options.firstOrNull()?.first,
                    expanded = categoryOpen,
                    onClick = { categoryOpen = true },
                )
                FlatWorkspaceDropdownMenu(
                    expanded = categoryOpen,
                    onDismissRequest = { categoryOpen = false },
                    menuWidth = 256.dp,
                ) {
                    options.forEach { (id, title) ->
                        WorkspaceFlatMenuRow(
                            title = title,
                            selected = category == id,
                            onClick = { categoryOpen = false; onCategory(id) },
                        )
                    }
                }
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
        afterFilter = {
            WorkspaceListActions(actions = actions)
        },
    )
}

internal fun surfaceFilterAllows(
    filter: UiFilter, selected: Boolean, locked: Boolean,
): Boolean = when (filter) {
    UiFilter.ALL -> true
    UiFilter.HIDE_SELECTED -> !selected
    UiFilter.SHOW_SELECTED -> selected
    UiFilter.LOCKED -> locked
}

/** One shared Entry/Root-like row; use a 3-dot menu for lock, order and OS actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SurfaceWorkspaceRow(
    icon: Bitmap? = null,
    packageName: String? = null,
    title: String,
    subtitle: String,
    selected: Boolean,
    indeterminate: Boolean = false,
    enabled: Boolean = true,
    locked: Boolean = false,
    onToggle: () -> Unit,
    actions: List<WorkspaceMenuAction> = emptyList(),
    expanded: Boolean = false,
    onExpand: (() -> Unit)? = null,
    details: @Composable (() -> Unit)? = null,
    nested: Boolean = false,
) {
    if (onExpand != null) {
        WorkspaceAppRow(
            icon = icon,
            packageName = packageName,
            title = title,
            subtitle = subtitle,
            expanded = expanded,
            lockState = if (locked) BulkLockState.FULL else BulkLockState.NONE,
            onExpand = onExpand,
            onLock = {},
            onUnlock = {},
            selection = {
                WorkspaceAppSelectionCheckbox(
                    state = when {
                        indeterminate -> ToggleableState.Indeterminate
                        selected -> ToggleableState.On
                        else -> ToggleableState.Off
                    },
                    enabled = enabled,
                    onClick = onToggle,
                )
            },
            menuActions = actions,
            expandedContent = { details?.invoke() },
        )
        return
    }
    // Entry/Component detail rows own the selection surface, inset and overflow.
    // Lists supplies only its own selection state and backend callbacks.
    WorkspaceDetailRow(
        position = ListRowPosition.SINGLE,
        checked = selected,
        enabled = enabled,
        locked = locked,
        onToggle = onToggle,
        content = {
            WorkspaceDetailHeading(title = title, kind = "")
            WorkspaceDetailCaption(subtitle)
        },
        trailing = { open, setOpen ->
            WorkspaceFlatOverflowMenu(
                actions = actions,
                expanded = open,
                onExpandedChange = setOpen,
                contentDescription = stringResource(R.string.common_more),
            )
        },
    )
}
