package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter

/** Entry and Root category rows always use the shared flat list presentation. */
@Composable
internal fun WorkspaceCategoryRow(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FlatCategoryRow(icon, title, description, modifier, onClick)
}

/** Browse-all is a navigation row, never a visually distinct oversized button. */
@Composable
internal fun WorkspaceBrowseAllButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlatCategoryRow(
        icon = Icons.Rounded.Apps,
        title = label,
        description = null,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    )
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
    val activeCount = (if (appTypeFilter != AppTypeFilter.ALL) 1 else 0) +
        (if (viewFilter != UiFilter.ALL) 1 else 0)
    // Leave space for the title, fixed actions, and insets in smaller windows.
    val optionsMaxHeight = (LocalConfiguration.current.screenHeightDp - 220)
        .coerceIn(96, 280).dp

    // Put the popup in the trigger's Box so Material 3 anchors its position
    // directly below the top Filter button, just like the Category menu.
    Box(modifier = modifier) {
        WorkspacePickerButton(
            label = stringResource(R.string.lc_filter_sheet_button),
            icon = Icons.Rounded.FilterList,
            selected = activeCount > 0,
            expanded = expanded,
            count = activeCount,
            onClick = {
                draftAppType = appTypeFilter
                draftView = viewFilter
                expanded = true
            }
        )
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = optionsMaxHeight + 100.dp),
            menuWidth = 264.dp,
            header = { FlatPopupHeader(stringResource(R.string.lc_filter_sheet_title)) },
            footer = {
                // The common footer owns the divider and 48dp row height.
                FlatPopupFooter {
                    WorkspaceFilterFooterAction(
                        label = stringResource(R.string.lc_filter_sheet_reset),
                        onClick = {
                            draftAppType = AppTypeFilter.ALL
                            draftView = UiFilter.ALL
                        }
                    )
                    WorkspaceFilterFooterAction(
                        label = stringResource(R.string.lc_filter_sheet_apply),
                        primary = true,
                        onClick = {
                            if (draftAppType != appTypeFilter) onAppTypeFilter(draftAppType)
                            if (draftView != viewFilter) onViewFilter(draftView)
                            expanded = false
                        }
                    )
                }
            }
        ) {
            // Options alone scroll inside FlatPopupMenu. No nested scrolling
            // Column or Material DropdownMenu padding can displace the footer.
            WorkspaceFilterChoiceGroup(
                title = stringResource(R.string.app_type_filter),
                options = AppTypeFilter.entries.toList(),
                selected = draftAppType,
                titleFor = { stringResource(it.titleRes()) },
                onSelected = { draftAppType = it }
            )
            AdaptiveHorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            WorkspaceFilterChoiceGroup(
                title = stringResource(R.string.view_filter),
                options = UiFilter.entries.toList(),
                selected = draftView,
                titleFor = { viewTitle(it) },
                onSelected = { draftView = it }
            )
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
        }
    }
}

/** One accessible text action inside the shared footer's centered 48dp row. */
@Composable
private fun WorkspaceFilterFooterAction(
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveText(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Filter selections are single-column flat rows; only Apply commits changes. */
@Composable
internal fun <T> WorkspaceFilterChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    titleFor: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    AdaptiveText(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    options.forEach { option ->
        WorkspaceFlatMenuRow(
            title = titleFor(option),
            selected = option == selected,
            onClick = { onSelected(option) },
            compactOption = true
        )
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
                FlatWorkspaceDropdownMenu(
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
        // Help remains the final control for Entry, Component and Ordering.
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
    secondaryMaxLines: Int = 1,
    actions: List<WorkspaceMenuAction> = emptyList(),
    helpExpanded: Boolean = false,
    onHelpToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            AdaptiveText(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            if (secondary != null) AdaptiveText(
                secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, maxLines = secondaryMaxLines
            )
        }
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

/** All expanded components use one visual surface.
 * Position is retained in the API for future optional presentation modes.
 */
@Composable
private fun WorkspaceDetailContainer(
    position: ListRowPosition,
    selected: Boolean,
    content: @Composable () -> Unit
) {
    ContinuousDetailSurface(selected = selected, content = content)
}

/** Read-only details use the exact same row body and indentation. */
@Composable
internal fun WorkspaceReadOnlyDetailRow(
    position: ListRowPosition = ListRowPosition.SINGLE,
    content: @Composable RowScope.() -> Unit
) {
    WorkspaceDetailContainer(position, selected = false) {
        UiCardDetailRow(content = content)
    }
}

/** Shared component detail: app-icon-aligned title, selected background and trailing lock. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceDetailRow(
    position: ListRowPosition,
    checked: Boolean,
    enabled: Boolean = true,
    locked: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    trailing: @Composable RowScope.(Boolean, (Boolean) -> Unit) -> Unit = { _, _ -> }
) {
    // The long press and the three-dot button both control this same anchored menu.
    var menuExpanded by remember { mutableStateOf(false) }
    val contentBlock: @Composable () -> Unit = {
        val clickModifier = Modifier.combinedClickable(
                role = androidx.compose.ui.semantics.Role.Checkbox,
                onClick = { if (enabled) onToggle() },
                onLongClick = { menuExpanded = true }
            ).semantics {
                toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On
                    else androidx.compose.ui.state.ToggleableState.Off
            }
        val rowContents: @Composable RowScope.() -> Unit = {
            // Preserve alignment with the parent app icon: its first 48dp
            // is the selection target, not a lock indicator.
            Spacer(Modifier.width(MaterialUiTokens.iconTouchTarget))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
            // Both long-press and the three-dot icon control this same menu.
            trailing(menuExpanded) { menuExpanded = it }
            // Reuse the space reserved for the app row's expand arrow, so
            // moving the lock here never shifts the three-dot menu.
            Box(
                modifier = Modifier.size(MaterialUiTokens.appExpandIndicatorWidth),
                contentAlignment = Alignment.Center
            ) {
                if (locked) {
                    AdaptiveIcon(
                        Icons.Rounded.Lock,
                        stringResource(R.string.bulk_lock_full),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        UiCardDetailRow(
            modifier = clickModifier,
            leadingInset = 0.dp,
            trailingAccessoryWidth = MaterialUiTokens.appExpandIndicatorWidth,
            content = rowContents
        )
    }
    WorkspaceDetailContainer(position, checked, contentBlock)
}
