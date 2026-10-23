package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(
    state: MainState,
    vm: MainViewModel,
    searchExpanded: Boolean = false,
    pendingNavigation: PendingNavigationGuard? = null,
    onComponentModeChanged: (Boolean) -> Unit = {},
    onOpenModuleStatus: () -> Unit = {}
) {
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    val componentScan by vm.componentScan.collectAsStateWithLifecycle()
    val spaceScanState by vm.spaceScanState.collectAsStateWithLifecycle()
    val componentBusy by vm.componentBusy.collectAsStateWithLifecycle()
    val prefsContext = LocalContext.current
    val navigate: (() -> Unit) -> Unit = { action ->
        if (pendingNavigation != null) pendingNavigation.request(action) else action()
    }
    val confirmation = rememberSelectionConfirmationController()
    SelectionConfirmationDialog(confirmation)
    var editingTitle by remember { mutableStateOf<com.yagay.ListCleaner.domain.ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
    var appTypeFilter by rememberSaveable { mutableStateOf(UiPreferencesStore.initialAppType(prefsContext, "rules")) }
    var ruleHelpExpanded by rememberSaveable { mutableStateOf(UiPreferencesRuntime.current.openHelpByDefault) }
    // Three-level navigation lives in the UI only. The existing IntentKind
    // and rule store remain the single source of truth for operations.
    var selectedGroup by rememberSaveable { mutableStateOf<EntryGroup?>(null) }
    var browseAll by rememberSaveable { mutableStateOf(false) }
    var selectedComponentKind by rememberSaveable { mutableStateOf<CleanupKind?>(null) }
    var browseAllComponents by rememberSaveable { mutableStateOf(false) }
    var savedRuleQuery by rememberSaveable { mutableStateOf("") }
    var savedRuleKind by rememberSaveable { mutableStateOf<IntentKind?>(null) }
    var savedComponentQuery by rememberSaveable { mutableStateOf("") }
    // Main-branch inspired list-first layout; the other mode keeps category navigation.
    val classicLayout = UiPreferencesRuntime.current.homeLayout == UiHomeLayout.CLASSIC_LIST
    LaunchedEffect(classicLayout) {
        selectedGroup = null
        browseAll = false
    }
    val workspaceStateHolder = rememberSaveableStateHolder()
    var systemProbeRequest by remember { mutableStateOf<SystemProbeRequest?>(null) }
    systemProbeRequest?.let { request ->
        SystemProbeDialog(request, onDismiss = { systemProbeRequest = null })
    }
    val showingComponents = selectedComponentKind != null || browseAllComponents
    DisposableEffect(showingComponents) {
        onComponentModeChanged(showingComponents)
        onDispose { if (showingComponents) onComponentModeChanged(false) }
    }
    if (showingComponents) {
        workspaceStateHolder.SaveableStateProvider(key = "root-component-workspace") {
            RootComponentsScreen(
            state = state,
            vm = vm,
            initialKind = selectedComponentKind,
            embedded = true,
            backEnabled = !searchExpanded,
            pendingNavigation = pendingNavigation,
            onExit = {
                selectedComponentKind = null
                browseAllComponents = false
                savedComponentQuery = state.query
                vm.setQuery(savedRuleQuery)
                if (classicLayout) vm.setFilter(savedRuleKind)
            }
            )
        }
        return
    }
    BackHandler(enabled = !classicLayout && state.filter == null && (selectedGroup != null || browseAll)) {
        navigate { if (browseAll) browseAll = false else selectedGroup = null }
    }

    editingTitle?.let { item ->
        ComponentTitleDialog(
            item,
            state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null }
        )
    }
    if (showCustomTypes) {
        CustomOpenTypeDialog(
            config = state.openTypesExplicit,
            onSave = vm::setCustomOpenDefinition,
            onDismiss = { showCustomTypes = false }
        )
    }
    if (showBrowserHosts) {
        BrowserHostDialog(
            config = state.browserLinks,
            onSave = vm::setBrowserHosts,
            onDismiss = { showBrowserHosts = false }
        )
    }

    // The four blocks are the homepage; there is no candidate list until
    // an entry is selected (or the explicit Browse all action is used).
    if (HomeLayoutPolicy.showCategoryHome(UiPreferencesRuntime.current.homeLayout, state.filter, state.query, browseAll)) {
        EntryWorkspaceHome(
            selectedGroup = selectedGroup,
            onGroup = { selectedGroup = it },
            onKind = { kind ->
                selectedGroup = kind.entryGroup()
                vm.setFilter(kind)
            },
            onPreviewKind = { kind ->
                systemProbeRequest = SystemProbeRequest(entryKind = kind, userId = vm.selectedSpace.value)
            },
            onBack = { selectedGroup = null },
            onAllApps = { browseAll = true },
            onComponentKind = { kind ->
                savedRuleQuery = state.query
                selectedComponentKind = kind
                browseAllComponents = false
                selectedGroup = null
                browseAll = false
                vm.setFilter(null)
                vm.setQuery(savedComponentQuery)
            },
            onAllComponents = {
                savedRuleQuery = state.query
                selectedComponentKind = null
                browseAllComponents = true
                selectedGroup = null
                browseAll = false
                vm.setFilter(null)
                vm.setQuery(savedComponentQuery)
            },
            componentScan = componentScan,
            componentScanning = componentBusy,
            onRefreshComponents = vm::refreshComponents,
            status = {
                if (spaceScanState == com.yagay.ListCleaner.data.SpacePackageScanState.FAILED) {
                    WorkspaceStatusMessage(
                        stringResource(R.string.space_package_scan_failed), isError = true
                    )
                }
                CleanupStatusAlert(state, onOpenStatus = onOpenModuleStatus)
            }
        )
        return
    }

    val openPreset by vm.ruleOpenPreset.collectAsStateWithLifecycle()
    val browserHost by vm.ruleBrowserHost.collectAsStateWithLifecycle()
    LaunchedEffect(state.filter) {
        if (state.filter != IntentKind.OPEN) vm.setRuleOpenPreset(null)
        if (state.filter != IntentKind.DEEP_LINK) vm.setRuleBrowserHost(null)
    }
    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) vm.setRuleOpenPreset(null)
    }
    LaunchedEffect(state.browserAvailableHosts, browserHost) {
        if (browserHost != null && browserHost !in state.browserAvailableHosts) vm.setRuleBrowserHost(null)
    }

    val typedOpenPreset = openPreset.takeIf { state.filter == IntentKind.OPEN }
    val deepLinkHost = browserHost.takeIf { state.filter == IntentKind.DEEP_LINK }

    val typedSelected = typedOpenPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitTypedSelected = typedOpenPreset?.let { state.openTypesExplicit.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = deepLinkHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = deepLinkHost != null
    val lockScope = deepLinkHost?.let(::browserRuleBulkLockScope) ?: ruleBulkLockScope(state.filter, openPreset)
    val activeSelected = when {
        typedOpenPreset != null -> typedSelected
        deepLinkHost != null -> deepLinkSelected
        else -> state.selected
    }
    val selectionDraft = remember(state.filter, typedOpenPreset, deepLinkHost, vm.selectedSpace.value) {
        PendingSelectionDraft<ComponentRule>()
    }
    val displayedSelected = activeSelected.toMutableSet().apply {
        selectionDraft.changes.forEach { (rule, selected) ->
            if (selected) add(rule) else remove(rule)
        }
    }.toSet()
    val canStageRule: (ComponentRule) -> Boolean = { rule ->
        when {
            typedOpenPreset != null -> rule !in state.selected &&
                !vm.isBulkProtected(lockScope, rule.packageName, rule.id)
            deepLinkHost != null -> rule !in genericDeepLinkSelected &&
                !vm.isBulkProtected(lockScope, rule.packageName, rule.id)
            else -> !vm.isRuleBulkProtected(state.filter, openPreset, rule)
        }
    }


    // Independent navigation and runtime heartbeat updates must not re-filter every app.
    val lockScopeCandidates = remember(
        typedOpenPreset, deepLinkHost, state.candidates,
        state.openTypesExplicit.customDefinitions, deepLinkSelected, state.filter
    ) {
        when {
            typedOpenPreset != null -> state.candidates.filter {
                it.rule.kind == IntentKind.OPEN &&
                    it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions)
            }
            deepLinkHost != null -> state.candidates.filter {
                it.rule.kind == IntentKind.DEEP_LINK &&
                    (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
            }
            else -> state.candidates.filter { state.filter == null || it.rule.kind == state.filter }
        }
    }
    val lockRulesByPackage = remember(lockScopeCandidates) {
        lockScopeCandidates.groupBy { it.rule.packageName }
            .mapValues { (_, items) -> items.map { it.rule } }
    }
    val baseShownGroups = remember(
        typedOpenPreset, deepLinkHost, state.candidates,
        state.openTypesExplicit.customDefinitions, typedSelected,
        deepLinkSelected, displayedSelected, state.query, state.uiFilter, state.groups, UiPreferencesRuntime.current
    ) {
        when {
            typedOpenPreset != null -> groupCandidates(
                state.candidates.filter { it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions) },
                displayedSelected,
                IntentKind.OPEN,
                state.query,
                state.uiFilter,
                UiPreferencesRuntime.current.appSort
            )
            deepLinkHost != null -> groupCandidates(
                state.candidates.filter {
                    it.rule.kind == IntentKind.DEEP_LINK &&
                        (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
                },
                displayedSelected,
                IntentKind.DEEP_LINK,
                state.query,
                state.uiFilter,
                UiPreferencesRuntime.current.appSort
            )
            else -> groupCandidates(
                state.candidates, displayedSelected, state.filter, state.query,
                state.uiFilter, UiPreferencesRuntime.current.appSort
            )
        }
    }
    val lockFilteredGroups = remember(
        baseShownGroups, state.uiFilter, bulkLockRevision,
        state.filter, openPreset, deepLinkScoped, lockScope
    ) {
        if (state.uiFilter == UiFilter.LOCKED) {
            baseShownGroups.mapNotNull { group ->
                val locked = group.components.filter { item ->
                    if (deepLinkScoped) vm.isBulkProtected(lockScope, item.rule.packageName, item.rule.id)
                    else vm.isRuleBulkProtected(state.filter, openPreset, item.rule)
                }
                if (locked.isEmpty()) null else group.copy(components = locked)
            }
        } else baseShownGroups
    }
    val shownGroups = remember(lockFilteredGroups, appTypeFilter) {
        lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    }
    LaunchedEffect(state.filter, openPreset, browserHost, shownGroups.firstOrNull()?.packageName,
        UiPreferencesRuntime.current.expandFirstApp) {
        if (UiPreferencesRuntime.current.expandFirstApp && state.expandedAppKey == null) {
            shownGroups.firstOrNull()?.let { group ->
                vm.toggleExpandedApp("${state.filter?.name ?: "ALL"}|${openPreset?.name ?: browserHost ?: "ALL"}|${group.packageName}")
            }
        }
    }
    val visibleRules = remember(shownGroups) {
        shownGroups.flatMap { it.components }.map { it.rule }.distinct()
    }

    val applyChanges: () -> Boolean = {
            val changes = selectionDraft.changes.filterKeys(canStageRule)
            val add = changes.filterValues { it }.keys
            val remove = changes.filterValues { !it }.keys
            when {
                typedOpenPreset != null -> {
                    vm.selectOpenTypeRules(typedOpenPreset, add, lockScope)
                    vm.deselectOpenTypeRules(typedOpenPreset, remove, lockScope)
                }
                deepLinkHost != null -> {
                    vm.selectBrowserHostRules(deepLinkHost, add, lockScope)
                    vm.deselectBrowserHostRules(deepLinkHost, remove, lockScope)
                }
                else -> {
                    vm.selectRules(add, state.filter, openPreset)
                    vm.deselectRules(remove, state.filter, openPreset)
                }
            }
            selectionDraft.discard()
            true
        }
    RegisterPendingNavigation(
        pendingNavigation, selectionDraft, selectionDraft.count,
        applyChanges, selectionDraft::discard
    )

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionDraft.count > 0) 90.dp else 16.dp)
    ) {
        if (classicLayout) {
            stickyHeader(key = "classic-mode-tabs") {
                ClassicCleanupTabs(
                    active = ClassicCleanupMode.ENTRIES,
                    onEntries = {},
                    onComponents = {
                        navigate {
                        savedRuleQuery = state.query
                        savedRuleKind = state.filter
                        selectedComponentKind = null
                        browseAllComponents = true
                        selectedGroup = null
                        browseAll = false
                        vm.setFilter(null)
                        vm.setQuery(savedComponentQuery)
                        }
                    }
                )
            }
        }
        item(key = "workspace-breadcrumb") {
            if (classicLayout) {
                ClassicListHeader(
                    state = state,
                    onOpenStatus = onOpenModuleStatus
                )
            } else {
                EntryWorkspaceBreadcrumb(
                    kind = state.filter,
                    onHome = {
                        navigate {
                        vm.setFilter(null)
                        selectedGroup = null
                        browseAll = false
                        vm.setQuery("")
                        }
                    },
                    onGroup = {
                        navigate {
                        selectedGroup = state.filter?.entryGroup()
                        browseAll = false
                        vm.setFilter(null)
                        vm.setQuery("")
                        }
                    }
                )
                if (state.runtime.needsDecision) RuntimePanel(
                    state, vm, showUpdateTools = false,
                    horizontalInset = MaterialUiTokens.groupHorizontal
                )
            }
        }
        stickyHeader(key = "list-controls") {
            PageFilterSection {
                Column {
                    ListControls(
                        state,
                        { filter -> navigate { vm.setFilter(filter) } },
                        vm::setUiFilter,
                        appTypeFilter = appTypeFilter,
                        onAppTypeFilter = {
                            appTypeFilter = it
                            UiPreferencesStore.rememberAppType(prefsContext, it, "rules")
                        },
                        showEntryGroups = classicLayout,
                        externalHelpExpanded = ruleHelpExpanded,
                        onExternalHelpChange = { ruleHelpExpanded = it },
                        extraFilter = {
                            when (state.filter) {
                                IntentKind.OPEN -> OpenPresetFilterMenu(
                                    selected = openPreset,
                                    config = state.openTypesExplicit,
                                    onSelected = { preset -> navigate { vm.setRuleOpenPreset(preset) } },
                                    onManageCustom = { showCustomTypes = true }
                                )
                                IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                    selected = browserHost,
                                    config = state.browserLinks,
                                    availableHosts = state.browserAvailableHosts,
                                    onSelected = { host -> navigate { vm.setRuleBrowserHost(host) } },
                                    onManage = { showBrowserHosts = true }
                                )
                                else -> Unit
                            }
                        },
                        batchCount = visibleRules.size,
                        onSelectAll = {
                            confirmation.request(prefsContext.getString(R.string.select_all)) {
                                visibleRules.filter(canStageRule).forEach { rule ->
                                    val current = selectionDraft.selected(rule, rule in activeSelected)
                                    selectionDraft.set(rule, rule in activeSelected, true)
                                }
                            }
                        },
                        onSelectNone = {
                            confirmation.request(prefsContext.getString(R.string.select_none)) {
                                visibleRules.filter(canStageRule).forEach { rule ->
                                    val current = selectionDraft.selected(rule, rule in activeSelected)
                                    selectionDraft.set(rule, rule in activeSelected, false)
                                }
                            }
                        },
                        onInvert = {
                            confirmation.request(prefsContext.getString(R.string.invert_selection)) {
                                visibleRules.filter(canStageRule).forEach { rule ->
                                    val current = selectionDraft.selected(rule, rule in activeSelected)
                                    selectionDraft.set(rule, rule in activeSelected, !current)
                                }
                            }
                        }
                    )
                }
            }
        }
        item(key = "list-summary") {
            SummaryRow(state, shownGroups.size)
            AdaptiveText(
                stringResource(R.string.system_preview_explanation),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ruleHelpExpanded) WorkspaceHelpSection {
                AdaptiveText(
                    if (state.filter == null) stringResource(R.string.entry_rules_general_help)
                    else stringResource(state.filter.descriptionRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.filter?.entryGroup() == EntryGroup.ADVANCED) {
                    AdaptiveText(
                        stringResource(R.string.entry_advanced_safety_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (openPreset == null && state.uiFilter != UiFilter.SHOW_SELECTED && state.candidates.any {
                    it.rule in state.selected && (it.unavailable || it.restricted)
                }) {
                AdaptiveTextButton(
                    onClick = {
                        vm.setFilter(null)
                        vm.setQuery("")
                        vm.setUiFilter(UiFilter.SHOW_SELECTED)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    AdaptiveText(stringResource(R.string.rules_view_unmatched))
                }
            }
        }
        shownGroups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${openPreset?.name ?: browserHost ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(
                    group,
                    displayedSelected,
                    expanded,
                    if (deepLinkScoped) vm.bulkLockState(
                        lockScope,
                        group.packageName,
                        lockRulesByPackage[group.packageName].orEmpty().map { it.id }
                    ) else vm.ruleBulkLockState(
                        state.filter,
                        openPreset,
                        group.packageName,
                        lockRulesByPackage[group.packageName].orEmpty()
                    ),
                    { vm.toggleExpandedApp(key) },
                    { selected ->
                        group.components.map { it.rule }.filter(canStageRule).forEach { rule ->
                            selectionDraft.set(rule, rule in activeSelected, selected)
                        }
                    },
                    {
                        if (deepLinkScoped) vm.setBulkAppLocked(
                            lockScope,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                            true
                        ) else vm.setRuleAppLocked(
                            state.filter,
                            openPreset,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty(),
                            true
                        )
                    },
                    {
                        if (deepLinkScoped) vm.setBulkAppLocked(
                            lockScope,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                            false
                        ) else vm.setRuleAppLocked(
                            state.filter,
                            openPreset,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty(),
                            false
                        )
                    }
                )
            }
            if (expanded) {
                itemsIndexed(group.components, key = { _, item -> "component|${item.rule.id}" }, contentType = { _, _ -> "component" }) { componentIndex, component ->
                    val sourceNote = when {
                        typedOpenPreset != null && component.rule in displayedSelected -> when {
                            component.rule in state.selected -> stringResource(R.string.rules_inherited_from_all)
                            component.rule in explicitTypedSelected -> stringResource(
                                R.string.rules_dedicated_rule,
                                state.openTypes.localizedTitle(typedOpenPreset)
                            )
                            else -> null
                        }
                        deepLinkHost != null && component.rule in displayedSelected -> when {
                            component.rule in genericDeepLinkSelected -> stringResource(R.string.rules_browser_inherited)
                            component.rule in explicitDeepLinkSelected -> stringResource(
                                R.string.rules_browser_dedicated,
                                deepLinkHost
                            )
                            else -> null
                        }
                        else -> null
                    }
                    ComponentRow(
                        component,
                        component.rule in displayedSelected,
                        state.priorities.titles[component.rule.id],
                        selectionNote = sourceNote,
                        position = if (componentIndex == group.components.lastIndex) ListRowPosition.LAST else ListRowPosition.MIDDLE,
                        locked = if (deepLinkScoped) vm.isBulkProtected(
                            lockScope, component.rule.packageName, component.rule.id
                        ) else vm.isRuleBulkProtected(state.filter, openPreset, component.rule),
                        lockToggleEnabled = if (deepLinkScoped) {
                            !vm.isBulkAppLocked(lockScope, component.rule.packageName)
                        } else !vm.isRuleAppLockedForRule(state.filter, openPreset, component.rule),
                        onToggle = {
                            val rule = component.rule
                            if (canStageRule(rule)) {
                                selectionDraft.set(rule, rule in activeSelected, rule !in displayedSelected)
                            }
                        },
                        onLock = {
                            if (deepLinkScoped) vm.setBulkItemLocked(lockScope, component.rule.id, true)
                            else vm.setRuleItemLocked(state.filter, openPreset, component.rule, true)
                        },
                        onUnlock = {
                            if (deepLinkScoped) vm.setBulkItemLocked(lockScope, component.rule.id, false)
                            else vm.setRuleItemLocked(state.filter, openPreset, component.rule, false)
                        },
                        onEditTitle = { editingTitle = component },
                        onPreview = {
                            systemProbeRequest = SystemProbeRequest(
                                entryKind = component.rule.kind,
                                userId = vm.selectedSpace.value,
                                focusPackage = component.rule.packageName,
                                focusClass = component.rule.className,
                            )
                        }
                    )
                }
            }
        }
        if (!state.loading && shownGroups.isEmpty()) {
            item {
                WorkspaceEmptyState(
                    message = stringResource(when {
                        state.error != null -> R.string.rules_empty_scan_failed
                        state.candidates.isEmpty() -> R.string.rules_empty_unscanned
                        state.query.isNotBlank() || state.uiFilter != UiFilter.ALL ||
                            appTypeFilter != AppTypeFilter.ALL -> R.string.rules_empty_filtered
                        state.filter == IntentKind.DIRECT_SHARE || state.filter == IntentKind.SHORTCUT_ITEM ->
                            R.string.observed_entry_empty_help
                        else -> R.string.no_matching_components
                    }),
                    clearLabel = if (state.query.isNotBlank() || state.uiFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL)
                        stringResource(R.string.rules_empty_clear_filters) else null,
                    onClear = if (state.query.isNotBlank() || state.uiFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL) {
                        {
                            vm.setQuery("")
                            vm.setUiFilter(UiFilter.ALL)
                            appTypeFilter = AppTypeFilter.ALL
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

@Composable
private fun SummaryRow(state: MainState, displayedApps: Int) {
    WorkspaceListSummary(
        title = stringResource(R.string.app_list_count, displayedApps),
        secondary = stringResource(state.displayMode.titleRes())
    )
    if (state.error != null) {
        WorkspaceStatusMessage(
            stringResource(R.string.rules_refresh_incomplete),
            isError = true
        )
    }
}

@Composable
fun PriorityTab(state: MainState, vm: MainViewModel, pendingNavigation: PendingNavigationGuard? = null) {
    PriorityDialogContent(state, vm, pendingNavigation)
}
