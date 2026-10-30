package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter

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
    onLock: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
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
            onLock = { onLock?.invoke() },
            onUnlock = { onUnlock?.invoke() },
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

/** Shared searchable workspace control; data source and filtering stay with each editor. */
@Composable
internal fun SurfaceWorkspaceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int = 255,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal)) {
        CompactSearchField(
            query = value,
            onQueryChange = { onValueChange(it.take(maxLength)) },
            autoFocus = false,
            placeholder = label,
        )
    }
}

/** Entry-style bulk selection semantics shared by all Lists editors.
 * Only visible, unlocked identifiers are passed as editable.
 * Existing selections outside the viewport are preserved.
 */
internal enum class SurfaceBulkOperation { ALL, INVERT, NONE }

internal fun <T> applySurfaceBulkSelection(
    selected: Set<T>,
    editable: Collection<T>,
    operation: SurfaceBulkOperation,
): Set<T> {
    val targets = editable.toSet()
    return when (operation) {
        SurfaceBulkOperation.ALL -> selected + targets
        SurfaceBulkOperation.INVERT -> (selected - targets) + (targets - selected)
        SurfaceBulkOperation.NONE -> selected - targets
    }
}

/** Number of pending rule changes across all Lists dimensions. */
internal fun surfacePendingChangeCount(
    saved: com.yagay.ListCleaner.domain.SurfaceFilterConfig,
    draft: com.yagay.ListCleaner.domain.SurfaceFilterConfig,
): Int {
    fun <K, V> changedSets(old: Map<K, Set<V>>, newer: Map<K, Set<V>>): Int =
        (old.keys + newer.keys).sumOf { key ->
            (old[key].orEmpty() - newer[key].orEmpty()).size +
                (newer[key].orEmpty() - old[key].orEmpty()).size
        }
    fun <T> changedSet(old: Set<T>, newer: Set<T>) =
        (old - newer).size + (newer - old).size
    return changedSets(saved.packages, draft.packages) +
        changedSets(saved.locks, draft.locks) +
        (saved.order.keys + draft.order.keys).count { saved.order[it] != draft.order[it] } +
        changedSet(saved.hiddenNotificationChannels, draft.hiddenNotificationChannels) +
        changedSet(saved.lockedNotificationChannels, draft.lockedNotificationChannels) +
        changedSet(saved.hiddenNotificationCategories, draft.hiddenNotificationCategories)
}

/** Shared UI-only state. Rule data and cleanup implementations remain in each editor. */
internal class SurfaceWorkspaceUiState(
    initialFilter: UiFilter,
    initialAppType: AppTypeFilter,
) {
    var viewFilter by mutableStateOf(initialFilter)
    var appTypeFilter by mutableStateOf(initialAppType)
    var expandedKey by mutableStateOf<String?>(null)
        private set
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("ALL")

    fun toggleExpanded(key: String) {
        expandedKey = if (expandedKey == key) null else key
    }
}

private val SurfaceWorkspaceUiSaver = Saver<SurfaceWorkspaceUiState, List<String>>(
    save = { state ->
        listOf(state.viewFilter.name, state.appTypeFilter.name, state.expandedKey.orEmpty(), state.searchQuery, state.selectedCategory)
    },
    restore = { values ->
        SurfaceWorkspaceUiState(
            UiFilter.entries.firstOrNull { it.name == values.getOrNull(0) } ?: UiFilter.ALL,
            AppTypeFilter.entries.firstOrNull { it.name == values.getOrNull(1) } ?: AppTypeFilter.USER,
        ).also { state ->
            values.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let(state::toggleExpanded)
            state.searchQuery = values.getOrNull(3).orEmpty()
            state.selectedCategory = values.getOrNull(4) ?: "ALL"
        }
    },
)

@Composable
internal fun rememberSurfaceWorkspaceUiState(
    userId: Int,
    pageKey: String,
    initialFilter: UiFilter,
    initialAppType: AppTypeFilter,
): SurfaceWorkspaceUiState = rememberSaveable(userId, pageKey, saver = SurfaceWorkspaceUiSaver) {
    SurfaceWorkspaceUiState(initialFilter, initialAppType)
}
