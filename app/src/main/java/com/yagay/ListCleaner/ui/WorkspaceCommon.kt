package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter

/** Visual sizing is shared between LSPosed entry and Root component editors. */
internal object WorkspaceUiTokens {
    val categoryHeight = 124.dp
    val categoryPadding = 14.dp
    val categoryGap = 10.dp
    val sectionGap = 14.dp
    val detailHeight = 48.dp
}

/** One card specification; null description reserves the same subtitle space. */
@Composable
internal fun WorkspaceCategoryCard(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AdaptiveCard(
        modifier = modifier.heightIn(min = WorkspaceUiTokens.categoryHeight)
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(WorkspaceUiTokens.categoryPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AdaptiveIcon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
            }
            AdaptiveText(
                title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            AdaptiveText(
                description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Identical action in both cleanup groups, using shared Material 3 rendering. */
@Composable
internal fun WorkspaceBrowseAllButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AdaptiveOutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        AdaptiveIcon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        AdaptiveText(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Used by both rule and Root editors; no duplicate back-header implementations. */
@Composable
internal fun WorkspaceBreadcrumb(
    homeLabel: String,
    onHome: () -> Unit,
    groupLabel: String? = null,
    onGroup: (() -> Unit)? = null,
    currentLabel: String? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdaptiveTextButton(
            onClick = onHome,
            contentPadding = MaterialControlMetrics.compactTextPadding
        ) {
            AdaptiveIcon(Icons.Rounded.GridView, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            AdaptiveText(homeLabel, maxLines = 1)
        }
        if (groupLabel != null) {
            AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(14.dp))
            if (onGroup != null) {
                AdaptiveTextButton(
                    onClick = onGroup,
                    contentPadding = MaterialControlMetrics.compactTextPadding
                ) { AdaptiveText(groupLabel, maxLines = 1) }
            } else {
                AdaptiveText(groupLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        if (currentLabel != null) {
            AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(14.dp))
            AdaptiveText(
                currentLabel, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal data class WorkspaceMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

/**
 * App-type and selected/locked filters share one implementation.
 * Operations differ per backend and are supplied as labeled callbacks.
 */
@Composable
internal fun WorkspaceFilterBar(
    appTypeFilter: AppTypeFilter,
    onAppTypeFilter: (AppTypeFilter) -> Unit,
    viewFilter: UiFilter,
    onViewFilter: (UiFilter) -> Unit,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    actions: List<WorkspaceMenuAction> = emptyList(),
    extraFilter: (@Composable () -> Unit)? = null,
    help: (@Composable () -> Unit)? = null
) {
    var appMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AdaptiveTextButton(onClick = { appMenu = true }, contentPadding = MaterialControlMetrics.compactTextPadding) {
                AdaptiveText(stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.app_type_filter),
                    stringResource(appTypeFilter.titleRes())
                ))
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
            AdaptiveDropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
                AppTypeFilter.entries.forEach { option ->
                    AdaptiveDropdownMenuItem(
                        text = { AdaptiveText(stringResource(option.titleRes())) },
                        leadingIcon = {
                            if (option == appTypeFilter) AdaptiveIcon(Icons.Rounded.Check, null)
                        },
                        onClick = { appMenu = false; onAppTypeFilter(option) }
                    )
                }
            }
        }
        Box {
            AdaptiveTextButton(onClick = { viewMenu = true }, contentPadding = MaterialControlMetrics.compactTextPadding) {
                AdaptiveText(stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.view_filter),
                    viewTitle(viewFilter)
                ))
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
            AdaptiveDropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                UiFilter.entries.forEach { option ->
                    AdaptiveDropdownMenuItem(
                        text = { AdaptiveText(viewTitle(option)) },
                        leadingIcon = {
                            if (option == viewFilter) AdaptiveIcon(Icons.Rounded.Check, null)
                        },
                        onClick = { viewMenu = false; onViewFilter(option) }
                    )
                }
            }
        }
        extraFilter?.invoke()
        if (actions.isNotEmpty()) {
            Box {
                AdaptiveTextButton(
                    onClick = { actionMenu = true },
                    enabled = actions.any { it.enabled },
                    contentPadding = MaterialControlMetrics.compactTextPadding
                ) {
                    AdaptiveText(stringResource(R.string.selection_actions))
                    AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
                }
                AdaptiveDropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                    actions.forEach { action ->
                        AdaptiveDropdownMenuItem(
                            text = { AdaptiveText(action.label) },
                            enabled = action.enabled,
                            onClick = { actionMenu = false; action.onClick() }
                        )
                    }
                }
            }
        }
        help?.invoke()
    }
}

@Composable
internal fun WorkspaceListSummary(
    title: String,
    secondary: String? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AdaptiveText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        if (secondary != null) AdaptiveText(
            secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, maxLines = 1
        )
    }
}

@Composable
internal fun WorkspaceEmptyState(
    message: String,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AdaptiveText(message, style = MaterialTheme.typography.bodyMedium)
        if (clearLabel != null && onClear != null) {
            AdaptiveTextButton(onClick = onClear) { AdaptiveText(clearLabel) }
        }
    }
}


/** One help toggle in the filter toolbar for both engines. */
@Composable
internal fun WorkspaceHelpToggle(expanded: Boolean, onClick: () -> Unit) {
    AdaptiveTextButton(
        onClick = onClick,
        contentPadding = MaterialControlMetrics.compactTextPadding
    ) {
        AdaptiveText(stringResource(R.string.entry_help_title), maxLines = 1)
        AdaptiveIcon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            null, Modifier.size(16.dp)
        )
    }
}

/** Same text inset, typography, and wrapping in the Rule and Root help surfaces. */
@Composable
internal fun WorkspaceHelpSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content
    )
}

@Composable
internal fun WorkspaceStatusMessage(message: String, isError: Boolean = false) {
    if (message.isBlank()) return
    AdaptiveText(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun WorkspaceProgress(progress: Float? = null) {
    if (progress == null) {
        AdaptiveLinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        AdaptiveLinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Read-only sorting details share the same indent and minimum height, not a selection checkbox. */
@Composable
internal fun WorkspaceReadOnlyDetailRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ).heightIn(min = WorkspaceUiTokens.detailHeight)
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Identical detail-row surface, checkbox, lock menu and hit area for both engines. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceDetailRow(
    position: ListRowPosition,
    checked: Boolean,
    enabled: Boolean = true,
    locked: Boolean,
    lockToggleEnabled: Boolean,
    onToggle: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var lockMenuExpanded by remember { mutableStateOf(false) }
    AdaptiveGroupSurface(Modifier.fillMaxWidth(), position = position) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
                .combinedClickable(
                    role = androidx.compose.ui.semantics.Role.Checkbox,
                    onClick = { if (enabled) onToggle() },
                    onLongClick = { if (lockToggleEnabled) lockMenuExpanded = true }
                )
                .heightIn(min = WorkspaceUiTokens.detailHeight)
                .padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdaptiveCheckbox(
                checked = checked, enabled = enabled, onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
            if (locked) AdaptiveIcon(
                Icons.Rounded.Lock, stringResource(R.string.bulk_lock_full),
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
            )
            trailing()
        }
        BulkLockContextMenu(
            expanded = lockMenuExpanded,
            onDismiss = { lockMenuExpanded = false },
            canLock = lockToggleEnabled && !locked,
            canUnlock = lockToggleEnabled && locked,
            onLock = onLock,
            onUnlock = onUnlock
        )
    }
}
