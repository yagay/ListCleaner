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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.yagay.ListCleaner.domain.AppType
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
    pendingNavigation: PendingNavigationGuard? = null,
    onExit: () -> Unit = {}
) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    val prefsContext = LocalContext.current
    val navigate: (() -> Unit) -> Unit = { action ->
        if (!busy) {
            if (pendingNavigation != null) pendingNavigation.request(action) else action()
        }
    }
    val confirmation = rememberSelectionConfirmationController()
    SelectionConfirmationDialog(confirmation)
    val message by vm.componentMessage.collectAsStateWithLifecycle()
    val batchProgress by vm.componentBatchProgress.collectAsStateWithLifecycle()
    val rootNotice by vm.componentRootNotice.collectAsStateWithLifecycle()
    var systemProbeRequest by remember { mutableStateOf<SystemProbeRequest?>(null) }
    systemProbeRequest?.let { request ->
        SystemProbeDialog(request, onDismiss = { systemProbeRequest = null })
    }
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
        mutableStateOf(
            initialKind == null && HomeLayoutPolicy.startWithComponentList(
                UiPreferencesRuntime.current.homeLayout,
                UiPreferencesRuntime.current.componentStart
            )
        )
    }
    // Draft is scoped to the current user space and component category.
    val selectionDraft = remember(kind, vm.selectedSpace.value) { PendingSelectionDraft<String>() }
    BackHandler(enabled = backEnabled && (embedded || kind != null || browseAll)) {
        navigate {
            if (embedded) {
                onExit()
            } else {
                kind = null
                browseAll = false
            }
        }
    }
    val applyChanges: () -> Boolean = {
        if (busy) false else {
            val currentItems = scan.items.associateBy { it.id }
            val desired = selectionDraft.changes.mapNotNull { (id, disabled) ->
                currentItems[id]
                    ?.takeIf { it.blocked == null && it.enabled != null &&
                        !vm.isComponentBulkProtected(kind, it) && it.enabled != !disabled }
                    ?.let { it to !disabled }
            }.toMap()
            if (desired.isEmpty()) false else {
                vm.changeComponentsDesired(kind, desired)
                selectionDraft.discard()
                true
            }
        }
    }
    RegisterPendingNavigation(
        pendingNavigation, selectionDraft, selectionDraft.count,
        applyChanges, selectionDraft::discard, rootEditor = true
    )

    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    var viewFilter by rememberSaveable { mutableStateOf(UiPreferencesStore.initialFilter(prefsContext, "components")) }
    var appTypeFilter by rememberSaveable { mutableStateOf(UiPreferencesStore.initialAppType(prefsContext, "components")) }
    var expandedAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingBatch by remember { mutableStateOf<PendingRootBatch?>(null) }
    pendingBatch?.let { batch ->
        val eligible = batch.targets.filter { item ->
            item.blocked == null && item.enabled != null &&
                !vm.isComponentBulkProtected(batch.kind, item) &&
                (batch.enable == null || selectionDraft.selected(item.id, item.enabled == false) == batch.enable)
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        val action = stringResource(when (batch.enable) {
            true -> R.string.root_batch_action_enable
            false -> R.string.root_batch_action_disable
            null -> R.string.root_batch_action_invert
        })
        val systemAppCount = eligible.asSequence()
            .filter { item ->
                item.appType == AppType.SYSTEM &&
                    (batch.enable == false || (batch.enable == null &&
                        !selectionDraft.selected(item.id, item.enabled == false)))
            }
            .map { it.component.packageName }.distinct().count()
        AdaptiveAlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = {
                AdaptiveText(stringResource(
                    if (systemAppCount > 0) R.string.lc_system_app_warning_title
                    else R.string.root_batch_confirm_title
                ))
            },
            text = {
                val base = stringResource(R.string.root_batch_confirmation_text, action)
                val warning = if (systemAppCount > 0) {
                    "\n\n" + stringResource(R.string.lc_system_app_warning_count, systemAppCount) +
                        "\n" + stringResource(R.string.lc_system_app_warning_root)
                } else ""
                AdaptiveText(base + warning)
            },
            confirmButton = {
                AdaptiveTextButton(enabled = eligible.isNotEmpty() && !busy, onClick = {
                    pendingBatch = null
                    eligible.forEach { item ->
                        val currentDisabled = selectionDraft.selected(item.id, item.enabled == false)
                        val desiredDisabled = when (batch.enable) {
                            true -> false
                            false -> true
                            null -> !currentDisabled
                        }
                        selectionDraft.set(item.id, item.enabled == false, desiredDisabled)
                    }
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { pendingBatch = null }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    LaunchedEffect(Unit) { vm.ensureComponentsFresh() }
    if (!embedded && kind == null && !browseAll && state.query.isBlank()) {
        RootWorkspaceHome(
            onChoose = { selected -> kind = selected },
            onAll = { browseAll = true },
            onRefresh = vm::refreshComponents,
            kinds = CleanupKind.entries,
            apps = scan.items.map { "${it.user}|${it.component.packageName}" }.distinct().size,
            components = scan.items.size,
            counts = scan.items.groupingBy { it.kind }.eachCount(),
            scanned = scan.observedAt > 0L,
            warning = scan.warning,
            scanning = busy
        )
        return
    }


    // Project source snapshots only when their inputs change. Progress updates, expanded
    // rows and navigation recomposition must not regroup the entire PackageManager catalog.
    val kindItems = remember(scan.items, kind) {
        scan.items.filter { ComponentCategoryFilterPolicy.matches(kind, it.kind) }
    }
    val lockItemsByApp = remember(kindItems) {
        kindItems.groupBy(::componentBulkLockAppId)
    }
    val appSort = UiPreferencesRuntime.current.appSort
    val groups = remember(kindItems, appTypeFilter, viewFilter, state.query, bulkLockRevision, appSort) {
        kindItems.asSequence()
            .filter { appTypeFilter.matches(it.appType) }
            .filter { item ->
                (when (viewFilter) {
                    UiFilter.ALL, UiFilter.LOCKED -> true
                    UiFilter.SHOW_SELECTED -> item.enabled == false
                    UiFilter.HIDE_SELECTED -> item.enabled == true
                }) &&
                    (state.query.isBlank() || sequenceOf(
                        item.label, item.owner, item.component.flattenToString()
                    ).any { it.contains(state.query, ignoreCase = true) })
            }
            .filter { viewFilter != UiFilter.LOCKED || vm.isComponentBulkProtected(kind, it) }
            .groupBy { "${it.user}|${it.component.packageName}" }
            .entries
            .sortedWith(when (appSort) {
                UiAppSort.SELECTED_FIRST -> compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                    .thenBy { it.value.first().owner.lowercase() }.thenBy { it.key }
                UiAppSort.NAME -> compareBy<Map.Entry<String, List<RootComponent>>> { it.value.first().owner.lowercase() }
                    .thenBy { it.key }
                UiAppSort.PACKAGE -> compareBy<Map.Entry<String, List<RootComponent>>> { it.value.first().component.packageName.lowercase() }
                    .thenBy { it.key }
            })
            .map { it.key to it.value }
    }
    LaunchedEffect(kind, groups.firstOrNull()?.first, UiPreferencesRuntime.current.expandFirstApp) {
        if (UiPreferencesRuntime.current.expandFirstApp && expandedAppKey == null) {
            groups.firstOrNull()?.let { (appKey, _) ->
                expandedAppKey = "${kind?.name ?: "ALL"}|$appKey"
            }
        }
    }
    val visible = remember(groups) { groups.flatMap { it.second } }
    // The checkbox reflects the staged disabled state of the same editable,
    // unlocked, currently visible components targeted by Root batch actions.
    // Locked/read-only entries do not skew the checked/indeterminate state.
    val editableVisibleComponents = visible.asSequence()
        .filter { it.blocked == null && it.enabled != null &&
            !vm.isComponentBulkProtected(kind, it) }
        .distinctBy { "${it.user}|${it.component.flattenToString()}" }
        .toList()
    val disabledVisibleCount = editableVisibleComponents.count {
        selectionDraft.selected(it.id, it.enabled == false)
    }
    val visibleBulkState = workspaceBulkSelectionState(
        disabledVisibleCount, editableVisibleComponents.size
    )


    val classicComponentMode = embedded &&
        UiPreferencesRuntime.current.homeLayout == UiHomeLayout.CLASSIC_LIST
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionDraft.count > 0) 90.dp else 16.dp)
    ) {
        if (classicComponentMode) {
            stickyHeader(key = "classic-mode-tabs") {
                ClassicCleanupTabs(
                    active = ClassicCleanupMode.COMPONENTS,
                    onEntries = { navigate(onExit) },
                    onComponents = {}
                )
            }
        } else {
            item(key = "root-breadcrumb") {
                WorkspaceBreadcrumb(
                    homeLabel = stringResource(R.string.workspace_groups),
                    onHome = {
                        navigate {
                            if (embedded) onExit()
                            else {
                                kind = null
                                browseAll = false
                                vm.setQuery("")
                            }
                        }
                    },
                    currentLabel = kind?.let { stringResource(it.titleRes()) }
                        ?: stringResource(R.string.workspace_browse_components)
                )
            }
        }
        stickyHeader(key = "controls") {
            PageFilterSection {
                Column(Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surface
                )) {
                    WorkspaceCategoryFilterRow(
                        category = {
                            ClassicComponentCategoryFilter(
                                selected = kind,
                                modifier = Modifier.fillMaxWidth(),
                                onSelected = { selected ->
                                    if (kind != selected) navigate {
                                        kind = selected
                                        browseAll = true
                                        expandedAppKey = null
                                    }
                                }
                            )
                        },
                        filter = {
                            WorkspaceFilterBar(
                                appTypeFilter = appTypeFilter,
                                onAppTypeFilter = {
                                    appTypeFilter = it
                                    UiPreferencesStore.rememberAppType(prefsContext, it, "components")
                                },
                                viewFilter = viewFilter,
                                onViewFilter = {
                                    viewFilter = it
                                    UiPreferencesStore.rememberFilter(prefsContext, it, "components")
                                },
                                viewTitle = { filter ->
                                    stringResource(when (filter) {
                                        UiFilter.ALL -> R.string.common_all
                                        UiFilter.SHOW_SELECTED -> R.string.root_filter_disabled
                                        UiFilter.HIDE_SELECTED -> R.string.root_filter_enabled
                                        UiFilter.LOCKED -> R.string.filter_locked
                                    })
                                }
                            )
                        }
                    )
                    if (batchProgress != null) {
                        val current = requireNotNull(batchProgress)
                        WorkspaceProgress(current.completed.toFloat() / current.total.coerceAtLeast(1))
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
                    } else if (busy) WorkspaceProgress()
                    AdaptiveHorizontalDivider()
                    WorkspaceListSummary(
                        // Match Entry exactly: a long "N apps · M components" label
                        // squeezes the same select-all checkbox on narrow screens.
                        title = stringResource(R.string.app_list_count, groups.size),
                        primarySelection = WorkspacePrimarySelection(
                            state = visibleBulkState,
                            enabled = !busy && editableVisibleComponents.isNotEmpty(),
                            onToggle = {
                                // The Root checkbox denotes DISABLED components.
                                // Checking selects them for disable; unchecking enables
                                // only the visible/unlocked subset. Confirmation and
                                // staged changes are handled by the existing batch flow.
                                pendingBatch = PendingRootBatch(
                                    kind, editableVisibleComponents,
                                    if (visibleBulkState == ToggleableState.On) true else false
                                )
                            }
                        ),
                        actions = listOf(
                            WorkspaceMenuAction(stringResource(R.string.root_batch_action_invert), {
                                pendingBatch = PendingRootBatch(kind, editableVisibleComponents, null)
                            }, !busy && editableVisibleComponents.isNotEmpty())
                        ),
                        helpExpanded = detailsExpanded,
                        onHelpToggle = { detailsExpanded = !detailsExpanded }
                    )
                }
            }
        }
        item(key = "summary") {
            if (detailsExpanded) WorkspaceHelpSection {
                // Preserve the full component count in the optional details.
                AdaptiveText(
                    stringResource(R.string.root_summary, groups.size, visible.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveText(
                    stringResource(R.string.root_filter_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveText(
                    stringResource(R.string.root_selection_semantics),
                    style = MaterialTheme.typography.bodySmall
                )
                AdaptiveText(
                    stringResource(R.string.root_change_semantics),
                    style = MaterialTheme.typography.bodySmall
                )
                AdaptiveText(
                    stringResource(when (kind) {
                        null -> R.string.root_kind_all_help
                        CleanupKind.LAUNCHER -> R.string.root_kind_launcher_help
                        CleanupKind.TILE -> R.string.root_kind_tile_help
                        CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
                        CleanupKind.WIDGET -> R.string.root_kind_widget_help
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WorkspaceStatusMessage(scan.warning)
            message?.let { WorkspaceStatusMessage(it, isError = true) }
        }

        groups.forEach { (appKey, unsorted) ->
            val components = unsorted.sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }, { it.id }))
            val editableComponents = components.filter { it.blocked == null && it.enabled != null }
            val disabledCount = editableComponents.count {
                selectionDraft.selected(it.id, it.enabled == false)
            }
            val selectionState = listSelectionState(disabledCount, editableComponents.size)
            val first = components.first()
            val expansionKey = "${kind?.name ?: "ALL"}|$appKey"
            val expanded = expandedAppKey == expansionKey
            val onExpand = { expandedAppKey = if (expandedAppKey == expansionKey) null else expansionKey }

            item(key = "app|$expansionKey", contentType = "app") {
                val kindTitles = CleanupKind.entries.filter { category ->
                    components.any { it.kind == category }
                }.map { stringResource(it.titleRes()) }
                val lockState = vm.componentBulkLockState(
                    kind, appKey, lockItemsByApp[appKey].orEmpty()
                )
                WorkspaceAppRow(
                    icon = first.icon,
                    title = first.owner,
                    packageName = first.component.packageName,
                    subtitle = WorkspaceAppSummaryText(
                        R.string.root_disabled_summary,
                        components.count { selectionDraft.selected(it.id, it.enabled == false) },
                        components.size,
                        kindTitles
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
                        WorkspaceAppSelectionCheckbox(
                            state = selectionState,
                            enabled = !busy && editableComponents.isNotEmpty(),
                            onClick = {
                                val disable = selectionState != ToggleableState.On
                                val editableTargets = editableComponents.filterNot {
                                    vm.isComponentBulkProtected(kind, it)
                                }
                                val warnCount = if (disable && first.appType == AppType.SYSTEM &&
                                    editableTargets.any {
                                        !selectionDraft.selected(it.id, it.enabled == false)
                                    }) 1 else 0
                                confirmation.requestForSystemSelection(
                                    first.owner, warnCount, SystemSelectionRisk.ROOT
                                ) {
                                    editableTargets.forEach { item ->
                                        selectionDraft.set(item.id, item.enabled == false, disable)
                                    }
                                }
                            }
                        )
                    },
                    details = {
                        if (editableComponents.size < components.size) {
                            WorkspaceAppNote(
                                stringResource(
                                    R.string.root_display_only_count,
                                    components.size - editableComponents.size
                                )
                            )
                        }
                    }
                )
            }

            if (expanded) itemsIndexed(
                components,
                key = { _, item -> item.id },
                contentType = { _, _ -> "component" }
            ) { componentIndex, item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                val lockToggleEnabled = !vm.isComponentAppLockedForItem(kind, item)
                val itemLocked = vm.isComponentBulkProtected(kind, item)
                WorkspaceDetailRow(
                    position = if (componentIndex == components.lastIndex)
                        ListRowPosition.LAST else ListRowPosition.MIDDLE,
                    checked = selectionDraft.selected(item.id, item.enabled == false),
                    enabled = editable && !itemLocked,
                    locked = itemLocked,
                    lockToggleEnabled = lockToggleEnabled,
                    onToggle = {
                        if (!busy && vm.selectedSpace.value == item.user) {
                            val currentDisabled = selectionDraft.selected(item.id, item.enabled == false)
                            val warnCount = if (!currentDisabled && item.appType == AppType.SYSTEM) 1 else 0
                            confirmation.requestForSystemSelection(
                                item.label, warnCount, SystemSelectionRisk.ROOT
                            ) {
                                selectionDraft.set(item.id, item.enabled == false, !currentDisabled)
                            }
                        }
                    },
                    onLock = { vm.setComponentItemLocked(kind, item, true) },
                    onUnlock = { vm.setComponentItemLocked(kind, item, false) },
                    content = {
                        WorkspaceDetailHeading(
                            item.label,
                            stringResource(item.kind.titleRes())
                        )
                        WorkspaceDetailCaption(
                            item.component.flattenToShortString()
                        )
                        WorkspaceDetailStatus(
                            item.blocked ?: stringResource(
                                when (item.overrideState) {
                                    0 -> if (item.enabled == true)
                                        R.string.root_default_enabled else R.string.root_default_disabled
                                    1 -> R.string.root_explicit_enabled
                                    2, 3, 4 -> R.string.root_disabled_unknown_source
                                    else -> R.string.root_unknown_state
                                }
                            ),
                            warning = item.blocked != null
                        )
                    },
                    trailing = {
                        WorkspaceDetailIconAction(
                            Icons.Rounded.Visibility,
                            stringResource(R.string.system_preview_action),
                            onClick = {
                                systemProbeRequest = SystemProbeRequest(
                                    componentKind = item.kind,
                                    userId = item.user,
                                    focusPackage = item.component.packageName,
                                    focusClass = item.component.className,
                                )
                            }
                        )
                    }
                )
            }
        }
        if (visible.isEmpty() && !busy) {
            item {
                WorkspaceEmptyState(
                    message = stringResource(
                        if (kindItems.isNotEmpty()) R.string.root_empty_filtered else R.string.root_no_components
                    ),
                    clearLabel = if (viewFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL || state.query.isNotBlank()
                    ) stringResource(R.string.root_clear_filters) else null,
                    onClear = if (viewFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL || state.query.isNotBlank()
                    ) {
                        {
                            viewFilter = UiFilter.ALL
                            appTypeFilter = AppTypeFilter.ALL
                            vm.setQuery("")
                        }
                    } else null
                )
            }
        }
    }
    PendingChangesBar(
        count = selectionDraft.count,
        onDiscard = selectionDraft::discard,
        onApply = { applyChanges() },
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
}
