package com.yagay.ListCleaner.ui

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.clickable
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
) {
    var categoryOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    WorkspaceCategoryFilterRow(
        category = {
            Box {
                WorkspacePickerButton(
                    label = options.firstOrNull { it.first == category }?.second ?: category,
                    icon = Icons.Rounded.Layers, selected = category != options.firstOrNull()?.first,
                    expanded = categoryOpen, onClick = { categoryOpen = true },
                )
                FlatWorkspaceDropdownMenu(
                    expanded = categoryOpen, onDismissRequest = { categoryOpen = false },
                    menuWidth = 256.dp,
                ) {
                    options.forEach { (id, title) ->
                        WorkspaceFlatMenuRow(
                            title = title, selected = category == id,
                            onClick = { categoryOpen = false; onCategory(id) },
                        )
                    }
                }
            }
        },
        filter = {
            Box {
                WorkspacePickerButton(
                    label = stringResource(R.string.lc_filter_sheet_button),
                    icon = Icons.Rounded.FilterAlt, selected = filter != UiFilter.ALL,
                    expanded = filterOpen, iconOnly = true,
                    onClick = { filterOpen = true },
                )
                FlatWorkspaceDropdownMenu(
                    expanded = filterOpen, onDismissRequest = { filterOpen = false },
                ) {
                    UiFilter.entries.forEach { option ->
                        WorkspaceFlatMenuRow(
                            title = stringResource(option.titleRes()),
                            selected = option == filter,
                            onClick = { filterOpen = false; onFilter(option) },
                        )
                    }
                }
            }
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
@Composable
internal fun SurfaceWorkspaceRow(
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
            icon = null,
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
    var overflow by remember { mutableStateOf(false) }
    ContinuousApplicationSurface(expanded = expanded && onExpand != null) {
        Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MaterialUiTokens.listRowHeight)
                .background(if (nested) MaterialFlatPalette.selectionHighlightSurface()
                    else androidx.compose.ui.graphics.Color.Transparent)
                .padding(start = MaterialUiTokens.pageHorizontal + (if (nested) 24.dp else 0.dp),
                    end = MaterialUiTokens.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap),
        ) {
            WorkspaceAppSelectionCheckbox(
                state = when {
                    indeterminate -> ToggleableState.Indeterminate
                    selected -> ToggleableState.On
                    else -> ToggleableState.Off
                },
                enabled = enabled, onClick = onToggle,
            )
            Column(Modifier.weight(1f).then(if (onExpand != null) Modifier.clickable(onClick = onExpand) else Modifier.clickable(onClick = onToggle))) {
                AdaptiveText(
                    title, style = if (nested) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                AdaptiveText(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked) AdaptiveIcon(
                Icons.Rounded.Lock, stringResource(R.string.filter_locked),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            WorkspaceFlatOverflowMenu(
                actions = actions, expanded = overflow, onExpandedChange = { overflow = it },
                contentDescription = stringResource(R.string.common_more),
            )
            if (onExpand != null) AdaptiveIconButton(onClick = onExpand) {
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    stringResource(if (expanded) R.string.common_collapse else R.string.common_expand),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (expanded) details?.invoke()
        }
    }
}
