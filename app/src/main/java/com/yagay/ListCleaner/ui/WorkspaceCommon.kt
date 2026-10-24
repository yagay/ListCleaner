package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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

/**
 * Home categories are *cards*, not list rows. Material 3 ListItem reserves
 * horizontal room for its leading/trailing slots, leaving only a few characters
 * for a headline inside a two-column grid. In a card, put those actions above
 * full-width text so long translated titles never create mismatched row heights.
 *
 * All geometry is shared by Entry Cleanup and Root Component Cleanup.
 */
@Composable
internal fun WorkspaceCategoryCard(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    UiCard(
        modifier = modifier.fillMaxHeight(),
        variant = UiCardVariant.CATEGORY,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = MaterialUiTokens.categoryHeight)
                .padding(
                    horizontal = MaterialUiTokens.categoryPadding,
                    vertical = MaterialUiTokens.categoryPadding
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.categoryContentGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdaptiveIcon(
                    icon, null, Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                AdaptiveIcon(
                    Icons.Rounded.ChevronRight, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AdaptiveText(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                minLines = 1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            AdaptiveText(
                description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
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
        Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = MaterialUiTokens.detailVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdaptiveTextButton(
            onClick = onHome,
            contentPadding = MaterialUiTokens.compactButtonPadding
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
                    contentPadding = MaterialUiTokens.compactButtonPadding
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

/** Filter accordion state; exactly one group may be expanded. */
private enum class FilterMenuSection { APPS, VIEW }

/**
 * One anchored Material 3 filter popup for Entry, Root and Ordering.
 *
 * Radio choices are draft-only until "Apply filters". Dismissal discards
 * these drafts and never changes LSPosed/Root/priority mutations.
 * OPEN/Browser's additional subfilters keep their existing callbacks.
 */
@Composable
internal fun WorkspaceFilterBar(
    appTypeFilter: AppTypeFilter,
    onAppTypeFilter: (AppTypeFilter) -> Unit,
    viewFilter: UiFilter,
    onViewFilter: (UiFilter) -> Unit,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    modifier: Modifier = Modifier,
    extraFilter: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var draftAppType by remember { mutableStateOf(appTypeFilter) }
    var draftView by remember { mutableStateOf(viewFilter) }
    // Match the category picker: show two group headers, not every filter row.
    var openSection by remember { mutableStateOf<FilterMenuSection?>(null) }
    val activeCount = (if (appTypeFilter != AppTypeFilter.ALL) 1 else 0) +
        (if (viewFilter != UiFilter.ALL) 1 else 0)

    // Put the popup in the trigger's Box so Material 3 anchors its position
    // directly below the top Filter button, just like the Category menu.
    Box(modifier = modifier) {
        CompactCategoryButton(
            selected = activeCount > 0,
            onClick = {
                draftAppType = appTypeFilter
                draftView = viewFilter
                openSection = null
                expanded = true
            }
        ) {
            AdaptiveIcon(
                Icons.Rounded.FilterList, null, Modifier.size(18.dp),
                tint = compactCategoryLabelColor(activeCount > 0)
            )
            WorkspaceControlLabel(
                stringResource(R.string.lc_filter_sheet_button),
                color = compactCategoryLabelColor(activeCount > 0)
            )
            if (activeCount > 0) {
                androidx.compose.material3.Badge {
                    AdaptiveText(activeCount.toString())
                }
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 328.dp)
                .heightIn(max = 440.dp)
        ) {
            AdaptiveText(
                stringResource(R.string.lc_filter_sheet_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Reuse the category menu's section and choice geometry.
            val appsOpen = openSection == FilterMenuSection.APPS
            WorkspaceMenuSection(
                title = stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.app_type_filter),
                    stringResource(draftAppType.titleRes())
                ),
                active = draftAppType != AppTypeFilter.ALL,
                expanded = appsOpen,
                onClick = { openSection = if (appsOpen) null else FilterMenuSection.APPS }
            )
            if (appsOpen) {
                AppTypeFilter.entries.forEach { option ->
                    WorkspaceMenuChoice(
                        title = stringResource(option.titleRes()),
                        selected = draftAppType == option,
                        indented = true,
                        onClick = {
                            draftAppType = option
                            openSection = null
                        }
                    )
                }
            }
            val viewOpen = openSection == FilterMenuSection.VIEW
            WorkspaceMenuSection(
                title = stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.view_filter),
                    viewTitle(draftView)
                ),
                active = draftView != UiFilter.ALL,
                expanded = viewOpen,
                onClick = { openSection = if (viewOpen) null else FilterMenuSection.VIEW }
            )
            if (viewOpen) {
                UiFilter.entries.forEach { option ->
                    WorkspaceMenuChoice(
                        title = viewTitle(option),
                        selected = draftView == option,
                        indented = true,
                        onClick = {
                            draftView = option
                            openSection = null
                        }
                    )
                }
            }
            if (extraFilter != null) {
                AdaptiveHorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = 8.dp, vertical = 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    extraFilter()
                }
            }
            AdaptiveHorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 8.dp, vertical = 4.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdaptiveTextButton(onClick = {
                    draftAppType = AppTypeFilter.ALL
                    draftView = UiFilter.ALL
                }) {
                    AdaptiveText(stringResource(R.string.lc_filter_sheet_reset))
                }
                AdaptiveButton(onClick = {
                    if (draftAppType != appTypeFilter) onAppTypeFilter(draftAppType)
                    if (draftView != viewFilter) onViewFilter(draftView)
                    expanded = false
                }) {
                    AdaptiveText(stringResource(R.string.lc_filter_sheet_apply))
                }
            }
        }
    }
}

/** Batch operations and help belong beside the list summary, not inside filters. */
@Composable
internal fun WorkspaceListActions(
    actions: List<WorkspaceMenuAction> = emptyList(),
    helpExpanded: Boolean = false,
    onHelpToggle: (() -> Unit)? = null,
) {
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A labeled menu provides all batch actions without a visible tri-state.
        if (actions.isNotEmpty()) {
            Box {
                AdaptiveTextButton(
                    onClick = { selectionMenuExpanded = true },
                    enabled = actions.any { it.enabled }
                ) {
                    AdaptiveText(stringResource(R.string.selection_actions), maxLines = 1)
                    AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                }
                AdaptiveDropdownMenu(
                    expanded = selectionMenuExpanded,
                    onDismissRequest = { selectionMenuExpanded = false }
                ) {
                    actions.forEach { action ->
                        AdaptiveDropdownMenuItem(
                            text = { AdaptiveText(action.label) },
                            enabled = action.enabled,
                            onClick = {
                                selectionMenuExpanded = false
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }
        // Help is always the final control for Entry, Component and Ordering.
        if (onHelpToggle != null) {
            AdaptiveIconButton(onClick = onHelpToggle) {
                AdaptiveIcon(
                    Icons.Rounded.Info,
                    stringResource(R.string.lc_filter_help_short),
                    Modifier.size(20.dp),
                    tint = if (helpExpanded) MaterialSelectionPalette.activeText()
                    else MaterialSelectionPalette.text()
                )
            }
        }
    }
}

@Composable
internal fun WorkspaceListSummary(
    title: String,
    secondary: String? = null,
    actions: List<WorkspaceMenuAction> = emptyList(),
    helpExpanded: Boolean = false,
    onHelpToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdaptiveText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        if (secondary != null) AdaptiveText(
            secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, maxLines = 1
        )
        WorkspaceListActions(actions, helpExpanded, onHelpToggle)
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
    CompactCategoryButton(
        selected = expanded,
        onClick = onClick
    ) {
        WorkspaceControlLabel(stringResource(R.string.lc_filter_help_short), color = compactCategoryLabelColor(expanded))
        AdaptiveIcon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            null, Modifier.size(16.dp), tint = compactCategoryLabelColor(expanded)
        )
    }
}

/** Same text inset, typography, and wrapping in the Rule and Root help surfaces. */
@Composable
internal fun WorkspaceHelpSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content
    )
}

@Composable
internal fun WorkspaceStatusMessage(message: String, isError: Boolean = false) {
    MaterialInlineStatus(message = message, isError = isError)
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

/** Read-only and selectable details share the same surface geometry. */
@Composable
internal fun WorkspaceReadOnlyDetailRow(
    position: ListRowPosition = ListRowPosition.SINGLE,
    content: @Composable RowScope.() -> Unit
) {
    if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) {
        GroupedComponentSurface(position, selected = false) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = MaterialUiTokens.groupedDetailStartPadding,
                    end = MaterialUiTokens.groupedDetailEndPadding,
                    top = MaterialUiTokens.groupedDetailPadding,
                    bottom = MaterialUiTokens.groupedDetailPadding
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else {
        AdaptiveGroupSurface(Modifier.fillMaxWidth(), position = position) {
            UiCardDetailRow(content = content)
        }
    }
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
    val contentBlock: @Composable () -> Unit = {
        val clickModifier = Modifier.combinedClickable(
                role = androidx.compose.ui.semantics.Role.Checkbox,
                onClick = { if (enabled) onToggle() },
                onLongClick = { if (lockToggleEnabled) lockMenuExpanded = true }
            )
        val rowContents: @Composable RowScope.() -> Unit = {
            if (UiPreferencesRuntime.current.listStyle != UiListStyle.GROUPED) {
                AdaptiveCheckbox(
                    checked = checked, enabled = enabled, onCheckedChange = null,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
            if (locked) AdaptiveIcon(
                Icons.Rounded.Lock, stringResource(R.string.bulk_lock_full),
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
            )
            trailing()
            if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) {
                AdaptiveIcon(
                    if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    null,
                    modifier = Modifier.padding(start = 1.dp).size(18.dp),
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
        if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) {
            Row(
                modifier = clickModifier.fillMaxWidth().padding(
                    start = MaterialUiTokens.groupedDetailStartPadding,
                    end = MaterialUiTokens.groupedDetailEndPadding,
                    top = MaterialUiTokens.groupedDetailPadding,
                    bottom = MaterialUiTokens.groupedDetailPadding
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = rowContents
            )
        } else {
            UiCardDetailRow(modifier = clickModifier, content = rowContents)
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
    if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) {
        GroupedComponentSurface(position, checked, contentBlock)
    } else {
        AdaptiveGroupSurface(Modifier.fillMaxWidth(), position = position) { contentBlock() }
    }
}
