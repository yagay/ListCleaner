package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.domain.AppTypeFilter

private data class PendingRootBatch(val kind: CleanupKind?, val targets: List<RootComponent>, val enable: Boolean?)

private fun componentAppSelectionRank(items: List<RootComponent>): Int {
    val editable = items.filter { it.blocked == null && it.enabled != null }
    val disabledCount = editable.count { it.enabled == false }
    return when {
        editable.isNotEmpty() && disabledCount == editable.size -> 0
        disabledCount > 0 -> 1
        else -> 2
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RootComponentsScreen(
    state: MainState,
    vm: MainViewModel,
    initialKind: CleanupKind? = null,
    embedded: Boolean = false,
    backEnabled: Boolean = true,
    onExit: () -> Unit = {}
) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    val message by vm.componentMessage.collectAsStateWithLifecycle()
    val batchProgress by vm.componentBatchProgress.collectAsStateWithLifecycle()
    val rootNotice by vm.componentRootNotice.collectAsStateWithLifecycle()
    rootNotice?.let { notice ->
        AdaptiveAlertDialog(
            onDismissRequest = vm::dismissComponentRootNotice,
            title = { AdaptiveText(stringResource(R.string.root_permission_required)) },
            text = { AdaptiveText(notice) },
            confirmButton = {
                AdaptiveTextButton(onClick = vm::dismissComponentRootNotice) {
                    AdaptiveText(stringResource(R.string.root_permission_ack))
                }
            }
        )
    }

    var kind by rememberSaveable(initialKind, embedded) {
        mutableStateOf(initialKind)
    }
    var browseAll by rememberSaveable(initialKind, embedded) {
        mutableStateOf(embedded && initialKind == null)
    }
    BackHandler(enabled = backEnabled && (embedded || kind != null || browseAll)) {
        if (embedded) {
            onExit()
        } else {
            kind = null
            browseAll = false
        }
    }
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var appTypeMenu by remember { mutableStateOf(false) }
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingBatch by remember { mutableStateOf<PendingRootBatch?>(null) }
    pendingBatch?.let { batch ->
        val eligible = batch.targets.filter { item ->
            item.blocked == null && item.enabled != null &&
                !vm.isComponentBulkProtected(batch.kind, item) &&
                (batch.enable == null || item.enabled != batch.enable)
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        val action = stringResource(when (batch.enable) {
            true -> R.string.root_batch_action_enable
            false -> R.string.root_batch_action_disable
            null -> R.string.root_batch_action_invert
        })
        AdaptiveAlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = { AdaptiveText(stringResource(R.string.root_batch_confirm_title)) },
            text = { AdaptiveText(stringResource(R.string.root_batch_confirmation_text, action)) },
            confirmButton = {
                AdaptiveTextButton(enabled = eligible.isNotEmpty() && !busy, onClick = {
                    pendingBatch = null
                    if (batch.enable == null) vm.invertComponentsBulk(batch.kind, eligible)
                    else vm.changeComponentsBulk(batch.kind, eligible, batch.enable)
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { pendingBatch = null }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    LaunchedEffect(Unit) { vm.refreshComponents() }
    if (!embedded && kind == null && !browseAll && state.query.isBlank()) {
        RootWorkspaceHome(
            onChoose = { selected -> kind = selected },
            onAll = { browseAll = true },
            onRefresh = vm::refreshComponents,
            kinds = CleanupKind.entries,
            apps = scan.items.map { "${it.user}|${it.component.packageName}" }.distinct().size,
            components = scan.items.size
        )
        return
    }


    val kindItems = scan.items.filter { kind == null || it.kind == kind }
    val lockItemsByApp = remember(kindItems, bulkLockRevision) {
        kindItems.groupBy(::componentBulkLockAppId)
    }
    val scopeItems = kindItems.filter { appTypeFilter.matches(it.appType) }
    val baseVisible = scopeItems.filter {
        (when (viewFilter) {
                UiFilter.ALL, UiFilter.LOCKED -> true
                UiFilter.SHOW_SELECTED -> it.enabled == false
                UiFilter.HIDE_SELECTED -> it.enabled == true
            }) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    val lockVisible = if (viewFilter == UiFilter.LOCKED) {
        baseVisible.filter { vm.isComponentBulkProtected(kind, it) }
    } else baseVisible
    val baseGroups = lockVisible.groupBy { "${it.user}|${it.component.packageName}" }.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                .thenBy { it.value.first().owner.lowercase() }
                .thenBy { it.key }
        )
    val groups = baseGroups
    val visible = groups.flatMap { it.value }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "root-breadcrumb") {
            WorkspaceBreadcrumb(
                homeLabel = stringResource(R.string.workspace_groups),
                onHome = {
                    if (embedded) onExit()
                    else {
                        kind = null
                        browseAll = false
                        vm.setQuery("")
                    }
                },
                currentLabel = kind?.let { stringResource(it.titleRes()) }
                    ?: stringResource(R.string.workspace_browse_all)
            )
        }
        stickyHeader(key = "controls") {
            PageFilterSection {
                Column(Modifier.fillMaxWidth().background(
                    if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE)
                        top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer
                    else MaterialTheme.colorScheme.surface
                )) {
                    val canDisable = !busy && visible.any { it.blocked == null && it.enabled == true && !vm.isComponentBulkProtected(kind, it) }
                    val canEnable = !busy && visible.any { it.blocked == null && it.enabled == false && !vm.isComponentBulkProtected(kind, it) }
                    val canInvert = !busy && visible.any { it.blocked == null && it.enabled != null && !vm.isComponentBulkProtected(kind, it) }
                    WorkspaceFilterBar(
                        appTypeFilter = appTypeFilter,
                        onAppTypeFilter = { appTypeFilter = it },
                        viewFilter = viewFilter,
                        onViewFilter = { viewFilter = it },
                        actions = listOf(
                            WorkspaceMenuAction(stringResource(R.string.root_batch_action_disable), {
                                pendingBatch = PendingRootBatch(kind, visible, false)
                            }, canDisable),
                            WorkspaceMenuAction(stringResource(R.string.root_batch_action_enable), {
                                pendingBatch = PendingRootBatch(kind, visible, true)
                            }, canEnable),
                            WorkspaceMenuAction(stringResource(R.string.root_batch_action_invert), {
                                pendingBatch = PendingRootBatch(kind, visible, null)
                            }, canInvert)
                        )
                    )
                    if (batchProgress != null) {
                        val current = requireNotNull(batchProgress)
                        AdaptiveLinearProgressIndicator(
                            progress = { current.completed.toFloat() / current.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AdaptiveText(stringResource(R.string.root_batch_processing),
                                style = MaterialTheme.typography.labelSmall)
                            AdaptiveTextButton(onClick = vm::cancelComponentBatch) {
                                AdaptiveText(stringResource(R.string.root_batch_stop_remaining))
                            }
                        }
                    } else if (busy) AdaptiveLinearProgressIndicator(Modifier.fillMaxWidth())
                    AdaptiveHorizontalDivider()
                }
            }
        }
        item(key = "summary") {
            Column(Modifier.fillMaxWidth()) {
                WorkspaceListSummary(
                    title = stringResource(R.string.root_summary, groups.size, visible.size),
                    onHelp = { detailsExpanded = !detailsExpanded },
                    helpExpanded = detailsExpanded
                )
                if (detailsExpanded) {
                AdaptiveText(
                    stringResource(R.string.root_filter_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveText(stringResource(R.string.root_selection_semantics), style = MaterialTheme.typography.bodySmall)
                AdaptiveText(
                    stringResource(R.string.root_change_semantics),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveText(
                    stringResource(
                        when (kind) {
                            null -> R.string.root_kind_all_help
                            CleanupKind.LAUNCHER -> R.string.root_kind_launcher_help
                            CleanupKind.TILE -> R.string.root_kind_tile_help
                            CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
                            CleanupKind.WIDGET -> R.string.root_kind_widget_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
                if (scan.warning.isNotBlank()) AdaptiveText(scan.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                message?.let { AdaptiveText(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        groups.forEach { (appKey, unsorted) ->
            val components = unsorted.sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }, { it.id }))
            val editableComponents = components.filter { it.blocked == null && it.enabled != null }
            val disabledCount = editableComponents.count { it.enabled == false }
            val selectionState = listSelectionState(disabledCount, editableComponents.size)
            val first = components.first()
            val expansionKey = "${kind?.name ?: "ALL"}|$appKey"
            val expanded = expandedAppKey == expansionKey
            val onExpand = { expandedAppKey = if (expandedAppKey == expansionKey) null else expansionKey }

            item(key = "app|$expansionKey") {
                val kindTitles = CleanupKind.entries.filter { category ->
                    components.any { it.kind == category }
                }.map { stringResource(it.titleRes()) }
                val lockState = vm.componentBulkLockState(
                    kind, appKey, lockItemsByApp[appKey].orEmpty()
                )
                WorkspaceAppRow(
                    icon = first.icon,
                    title = first.owner,
                    subtitle = stringResource(
                        R.string.root_disabled_summary,
                        components.count { it.enabled == false },
                        components.size,
                        kindTitles.joinToString(" · ")
                    ),
                    expanded = expanded,
                    lockState = lockState,
                    onExpand = onExpand,
                    onLock = {
                        vm.setComponentAppLocked(kind, appKey, lockItemsByApp[appKey].orEmpty(), true)
                    },
                    onUnlock = {
                        vm.setComponentAppLocked(kind, appKey, lockItemsByApp[appKey].orEmpty(), false)
                    },
                    selection = {
                        AdaptiveTriStateCheckbox(
                            state = selectionState,
                            enabled = !busy && editableComponents.isNotEmpty(),
                            onClick = {
                                pendingBatch = PendingRootBatch(
                                    kind, editableComponents, selectionState == ToggleableState.On
                                )
                            }
                        )
                    },
                    details = {
                        if (editableComponents.size < components.size) {
                            AdaptiveText(
                                stringResource(
                                    R.string.root_display_only_count,
                                    components.size - editableComponents.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                )
            }

            if (expanded) itemsIndexed(components, key = { _, item -> item.id }) { componentIndex, item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                val lockToggleEnabled = !vm.isComponentAppLockedForItem(kind, item)
                val itemLocked = vm.isComponentBulkProtected(kind, item)
                var lockMenuExpanded by remember { mutableStateOf(false) }
                AdaptiveGroupSurface(
                    Modifier.fillMaxWidth(),
                    position = if (componentIndex == components.lastIndex) MiuixRowPosition.LAST else MiuixRowPosition.MIDDLE
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                            .combinedClickable(
                                role = Role.Checkbox,
                                onClick = {
                                    if (editable) vm.changeComponent(item, item.enabled == false)
                                },
                                onLongClick = {
                                    if (lockToggleEnabled) lockMenuExpanded = true
                                }
                            )
                            .heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdaptiveCheckbox(
                            checked = item.enabled == false,
                            enabled = editable,
                            onCheckedChange = null,
                            modifier = Modifier.padding(12.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AdaptiveText(item.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                AdaptiveText(stringResource(item.kind.titleRes()), style = MaterialTheme.typography.labelMedium)
                            }
                            AdaptiveText(
                                item.component.flattenToShortString(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                            AdaptiveText(
                                item.blocked ?: stringResource(
                                    when (item.overrideState) {
                                        0 -> if (item.enabled == true) R.string.root_default_enabled else R.string.root_default_disabled
                                        1 -> R.string.root_explicit_enabled
                                        2, 3, 4 -> R.string.root_disabled_unknown_source
                                        else -> R.string.root_unknown_state
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (itemLocked) {
                            AdaptiveIcon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.bulk_lock_full),
                                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                            )
                        }
                    }
                    BulkLockContextMenu(
                        expanded = lockMenuExpanded,
                        onDismiss = { lockMenuExpanded = false },
                        canLock = lockToggleEnabled && !itemLocked,
                        canUnlock = lockToggleEnabled && itemLocked,
                        onLock = { vm.setComponentItemLocked(kind, item, true) },
                        onUnlock = { vm.setComponentItemLocked(kind, item, false) }
                    )
                }
            }
        }
        if (visible.isEmpty() && !busy) {
            item {
                WorkspaceEmptyState(
                    message = stringResource(
                        if (kindItems.isNotEmpty()) R.string.root_empty_filtered else R.string.root_no_components
                    ),
                    clearLabel = if (kind != null || viewFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL || state.query.isNotBlank()
                    ) stringResource(R.string.root_clear_filters) else null,
                    onClear = if (kind != null || viewFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL || state.query.isNotBlank()
                    ) {
                        {
                            kind = null
                            viewFilter = UiFilter.ALL
                            appTypeFilter = AppTypeFilter.ALL
                            vm.setQuery("")
                        }
                    } else null
                )
            }
        }
    }
}