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
import com.yagay.ListCleaner.domain.requiresSystemEntrySafetyNote
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppType
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
    // One continuous editor; category navigation stays in the pinned selector.
    var browseAllComponents by rememberSaveable { mutableStateOf(false) }
    var savedRuleQuery by rememberSaveable { mutableStateOf("") }
    var savedRuleKind by rememberSaveable { mutableStateOf<IntentKind?>(null) }
    var savedComponentQuery by rememberSaveable { mutableStateOf("") }
    val workspaceStateHolder = rememberSaveableStateHolder()
    var overviewPackage by remember { mutableStateOf<String?>(null) }
    overviewPackage?.let { packageName ->
        ApplicationOverviewDialog(packageName, state, componentScan, vm.selectedSpace.value, onDismiss = { overviewPackage = null })
    }
    var systemProbeRequest by remember { mutableStateOf<SystemProbeRequest?>(null) }
    systemProbeRequest?.let { request ->
        SystemProbeDialog(request, onDismiss = { systemProbeRequest = null })
    }
    val showingComponents = browseAllComponents
    DisposableEffect(showingComponents) {
        onComponentModeChanged(showingComponents)
        onDispose { if (showingComponents) onComponentModeChanged(false) }
    }
    if (showingComponents) {
        workspaceStateHolder.SaveableStateProvider(key = "root-component-workspace") {
            RootComponentsScreen(
            state = state,
            vm = vm,
            initialKind = null,
            embedded = true,
            backEnabled = !searchExpanded,
            pendingNavigation = pendingNavigation,
            onExit = {
                browseAllComponents = false
                savedComponentQuery = state.query
                vm.setQuery(savedRuleQuery)
                vm.setFilter(savedRuleKind)
            }
            )
        }
        return
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
    // Preserve staged choices until repository state confirms each applied rule.
    // Shortcut entries are synthetic rules, so a premature draft reset makes
    // their checkboxes appear to revert while the state flow is still catching up.
    val draftChanges = selectionDraft.changes
    LaunchedEffect(activeSelected, draftChanges) {
        selectionDraft.acknowledge(draftChanges.filter { (rule, desired) ->
            (rule in activeSelected) == desired
        }.keys)
    }
    val displayedSelected = remember(activeSelected, draftChanges) {
        if (draftChanges.isEmpty()) activeSelected else activeSelected.toMutableSet().apply {
            draftChanges.forEach { (rule, selected) ->
                if (selected) add(rule) else remove(rule)
            }
        }.toSet()
    }
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
    // Candidate discovery and metadata are stable across checkbox edits, search,
    // filter changes and unrelated runtime updates. Build the expensive app groups
    // once per catalog snapshot, then filter/sort only the visible groups.
    val catalogAppGroups = remember(state.candidates) {
        baseAppGroups(state.candidates)
    }
    val scopedAppGroups = remember(
        catalogAppGroups, typedOpenPreset, deepLinkHost,
        state.openTypesExplicit.customDefinitions, deepLinkSelected
    ) {
        val eligible: (com.yagay.ListCleaner.domain.ComponentCandidate) -> Boolean = when {
            typedOpenPreset != null -> { item ->
                item.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions)
            }
            deepLinkHost != null -> { item ->
                item.rule.kind == IntentKind.DEEP_LINK &&
                    (item.matchesBrowserHost(deepLinkHost) || item.rule in deepLinkSelected)
            }
            else -> { _ -> true }
        }
        if (typedOpenPreset == null && deepLinkHost == null) catalogAppGroups
        else catalogAppGroups.mapNotNull { group ->
            val matching = group.components.filter(eligible)
            if (matching.isEmpty()) null else {
                val first = matching.first()
                group.copy(
                    appLabel = first.appLabel,
                    appIcon = first.appIcon,
                    appType = first.appType,
                    components = matching
                )
            }
        }
    }
    // Staged edits change checkboxes only. Sorting and selected/unselected filters
    // depend on confirmed selection, and must not rebuild the catalog grouping.
    val baseShownGroups = remember(
        scopedAppGroups, activeSelected, state.filter, typedOpenPreset, deepLinkHost,
        state.query, state.uiFilter, UiPreferencesRuntime.current
    ) {
        filterAppGroups(
            scopedAppGroups, activeSelected,
            when {
                typedOpenPreset != null -> IntentKind.OPEN
                deepLinkHost != null -> IntentKind.DEEP_LINK
                else -> state.filter
            },
            state.query, state.uiFilter, UiPreferencesRuntime.current.appSort
        )
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
    val visibleSystemPackages = remember(shownGroups) {
        shownGroups.asSequence().filter { it.appType == AppType.SYSTEM }
            .map { it.packageName }.toSet()
    }
    val newlySelectedSystemAppCount = {
        visibleRules.asSequence().filter(canStageRule)
            .filter { it !in displayedSelected && it.packageName in visibleSystemPackages }
            .map { it.packageName }.distinct().count()
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
            // Shortcut Item keys are synthetic; verify their canonical persisted
            // rule IDs before clearing the draft, instead of silently losing ticks.
            val saved = state.filter != IntentKind.SHORTCUT_ITEM ||
                vm.entryRulesMatch(changes)
            if (saved) selectionDraft.discard()
            saved
        }
    RegisterPendingNavigation(
        pendingNavigation, selectionDraft, selectionDraft.count,
        applyChanges, selectionDraft::discard
    )

    val selectionActions = listOf(
                    WorkspaceMenuAction(stringResource(R.string.select_all), {
                        confirmation.request(
                            prefsContext.getString(R.string.select_all),
                            systemAppCount = newlySelectedSystemAppCount(),
                            risk = SystemSelectionRisk.ENTRY
                        ) {
                            visibleRules.filter(canStageRule).forEach { rule ->
                                selectionDraft.set(rule, rule in activeSelected, true)
                            }
                        }
                    }, visibleRules.isNotEmpty()),
                    WorkspaceMenuAction(stringResource(R.string.select_none), {
                        confirmation.request(prefsContext.getString(R.string.select_none)) {
                            visibleRules.filter(canStageRule).forEach { rule ->
                                selectionDraft.set(rule, rule in activeSelected, false)
                            }
                        }
                    }, visibleRules.isNotEmpty()),
                    WorkspaceMenuAction(stringResource(R.string.invert_selection), {
                        confirmation.request(
                            prefsContext.getString(R.string.invert_selection),
                            systemAppCount = newlySelectedSystemAppCount(),
                            risk = SystemSelectionRisk.ENTRY
                        ) {
                            visibleRules.filter(canStageRule).forEach { rule ->
                                val current = selectionDraft.selected(rule, rule in activeSelected)
                                selectionDraft.set(rule, rule in activeSelected, !current)
                            }
                        }
                    }, visibleRules.isNotEmpty())
                )

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionDraft.count > 0) 90.dp else 16.dp)
    ) {
        stickyHeader(key = "workspace-header") {
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
                        showEntryGroups = true,
                        selectionActions = selectionActions,
                        modeAction = { WorkspaceSortShortcut(state.filter) { navigate { vm.setDestination(Destination.PRIORITY) } } },
                        previewActions = (if (state.filter == null) IntentKind.entries.toList()
                            else listOf(requireNotNull(state.filter))).map { previewKind ->
                            WorkspaceMenuAction(
                                label = stringResource(previewKind.titleRes()),
                                onClick = {
                                    systemProbeRequest = SystemProbeRequest(
                                        entryKind = previewKind,
                                        userId = vm.selectedSpace.value
                                    )
                                }
                            )
                        },
                        subtypeTitle = when (state.filter) {
                            IntentKind.OPEN -> openPreset?.let { state.openTypesExplicit.localizedTitle(it) }
                            IntentKind.DEEP_LINK -> browserHost
                            else -> null
                        },
                        subtypeAvailable = when (state.filter) {
                            IntentKind.OPEN -> state.openTypesExplicit.configuredPresets().isNotEmpty()
                            IntentKind.DEEP_LINK -> state.browserAvailableHosts.isNotEmpty() ||
                                state.browserLinks.hosts.isNotEmpty() || browserHost != null
                            else -> false
                        },
                        extraFilter = {
                            when (state.filter) {
                                IntentKind.OPEN -> OpenPresetFilterMenu(
                                    selected = openPreset,
                                    config = state.openTypesExplicit,
                                    onSelected = { preset -> navigate { vm.setRuleOpenPreset(preset) } },
                                    onManageCustom = { showCustomTypes = true },
                                    iconOnly = true
                                )
                                IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                    selected = browserHost,
                                    config = state.browserLinks,
                                    availableHosts = state.browserAvailableHosts,
                                    onSelected = { host -> navigate { vm.setRuleBrowserHost(host) } },
                                    onManage = { showBrowserHosts = true },
                                    iconOnly = true
                                )
                                else -> Unit
                            }
                        }
                    )
                }
            }
        }
        item(key = "workspace-status") {
            ClassicListHeader(state = state, onOpenStatus = onOpenModuleStatus)
        }
        item(key = "list-summary") {
            SummaryRow(
                state, shownGroups.size,
                actions = emptyList(),
                helpExpanded = ruleHelpExpanded,
                onHelpToggle = { ruleHelpExpanded = !ruleHelpExpanded }
            )
            if (ruleHelpExpanded) WorkspaceHelpSection {
                AdaptiveText(
                    stringResource(R.string.system_preview_explanation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveText(
                    if (state.filter == null) stringResource(R.string.entry_rules_general_help)
                    else stringResource(state.filter.descriptionRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.filter?.requiresSystemEntrySafetyNote() == true) {
                    AdaptiveText(
                        stringResource(R.string.entry_system_safety_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Keep the unmatched-rule action reachable without giving it a
                // full-height always-visible row above the candidate list.
                if (openPreset == null && state.uiFilter != UiFilter.SHOW_SELECTED &&
                    state.candidates.any { it.rule in state.selected && (it.unavailable || it.restricted) }) {
                    AdaptiveTextButton(onClick = {
                        vm.setFilter(null)
                        vm.setQuery("")
                        vm.setUiFilter(UiFilter.SHOW_SELECTED)
                    }) { AdaptiveText(stringResource(R.string.rules_view_unmatched)) }
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
                        val editableRules = group.components.map { it.rule }.filter(canStageRule)
                        val warnCount = if (selected && group.appType == AppType.SYSTEM &&
                            editableRules.any { it !in displayedSelected }) 1 else 0
                        confirmation.requestForSystemSelection(
                            group.appLabel, warnCount, SystemSelectionRisk.ENTRY
                        ) {
                            editableRules.forEach { rule ->
                                selectionDraft.set(rule, rule in activeSelected, selected)
                            }
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
                    },
                    onOverview = { overviewPackage = group.packageName },
                    openWithDefinitions = if (state.filter == IntentKind.OPEN &&
                        typedOpenPreset == null) state.openTypesExplicit.customDefinitions else null
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
                                val selecting = rule !in displayedSelected
                                val warnCount = if (selecting && component.appType == AppType.SYSTEM) 1 else 0
                                confirmation.requestForSystemSelection(
                                    component.activityLabel, warnCount, SystemSelectionRisk.ENTRY
                                ) {
                                    selectionDraft.set(rule, rule in activeSelected, selecting)
                                }
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
private fun SummaryRow(
    state: MainState,
    displayedApps: Int,
    actions: List<WorkspaceMenuAction>,
    helpExpanded: Boolean,
    onHelpToggle: () -> Unit
) {
    WorkspaceListSummary(
        title = stringResource(R.string.app_list_count, displayedApps),
        secondary = stringResource(state.displayMode.titleRes()),
        showSelectionActions = false,
        actions = actions,
        helpExpanded = helpExpanded,
        onHelpToggle = onHelpToggle
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
