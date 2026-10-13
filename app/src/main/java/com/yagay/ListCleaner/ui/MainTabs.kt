package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.extra.SuperArrow as NativeSuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown as NativeSuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch as NativeSuperSwitch
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel) {
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    var editingTitle by remember { mutableStateOf<com.yagay.ListCleaner.domain.ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
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
    val lockScopeCandidates = when {
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
    val lockRulesByPackage = remember(lockScopeCandidates, bulkLockRevision) {
        lockScopeCandidates.groupBy { it.rule.packageName }
            .mapValues { (_, items) -> items.map { it.rule } }
    }
    val baseShownGroups = when {
        typedOpenPreset != null -> groupCandidates(
            state.candidates.filter { it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions) },
            typedSelected,
            IntentKind.OPEN,
            state.query,
            state.uiFilter
        )
        deepLinkHost != null -> groupCandidates(
            state.candidates.filter {
                it.rule.kind == IntentKind.DEEP_LINK &&
                    (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
            },
            deepLinkSelected,
            IntentKind.DEEP_LINK,
            state.query,
            state.uiFilter
        )
        else -> state.groups
    }
    val lockFilteredGroups = if (state.uiFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseShownGroups.mapNotNull { group ->
            val locked = group.components.filter { item ->
                if (deepLinkScoped) vm.isBulkProtected(lockScope, item.rule.packageName, item.rule.id)
                else vm.isRuleBulkProtected(state.filter, openPreset, item.rule)
            }
            if (locked.isEmpty()) null else group.copy(components = locked)
        }
    } else baseShownGroups
    val shownGroups = lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    val activeSelected = when {
        typedOpenPreset != null -> typedSelected
        deepLinkHost != null -> deepLinkSelected
        else -> state.selected
    }
    val visibleRules = shownGroups.flatMap { it.components }.map { it.rule }.distinct()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "module-indicator") {
            PageOverviewSection(stringResource(R.string.nav_rules)) {
                ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
                AdaptiveHorizontalDivider()
                AdaptiveTextButton(
                    onClick = { vm.setDestination(Destination.TILES) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    AdaptiveText(
                        stringResource(R.string.root_screen_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AdaptiveIcon(Icons.Rounded.ExpandMore, null)
                }
            }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        stickyHeader(key = "list-controls") {
            PageFilterSection {
                Column {
                    ListControls(
                        state,
                        vm::setFilter,
                        vm::setUiFilter,
                        appTypeFilter = appTypeFilter,
                        onAppTypeFilter = { appTypeFilter = it },
                        extraFilter = {
                            when (state.filter) {
                                IntentKind.OPEN -> OpenPresetFilterMenu(
                                    selected = openPreset,
                                    config = state.openTypesExplicit,
                                    onSelected = vm::setRuleOpenPreset,
                                    onManageCustom = { showCustomTypes = true }
                                )
                                IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                    selected = browserHost,
                                    config = state.browserLinks,
                                    availableHosts = state.browserAvailableHosts,
                                    onSelected = vm::setRuleBrowserHost,
                                    onManage = { showBrowserHosts = true }
                                )
                                else -> Unit
                            }
                        },
                        batchCount = visibleRules.size,
                        onSelectAll = {
                            when {
                                typedOpenPreset != null ->
                                    vm.selectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.selectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.selectRules(visibleRules, state.filter, openPreset)
                            }
                        },
                        onSelectNone = {
                            when {
                                typedOpenPreset != null ->
                                    vm.deselectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.deselectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.deselectRules(visibleRules, state.filter, openPreset)
                            }
                        },
                        onInvert = {
                            when {
                                typedOpenPreset != null ->
                                    vm.invertOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.invertBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.invertRules(visibleRules, state.filter, openPreset)
                            }
                        }
                    )
                }
            }
        }
        item(key = "list-summary") {
            PageResultsSection(stringResource(R.string.nav_rules))
            SummaryRow(state, shownGroups.size)
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
                    activeSelected,
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
                        when {
                            typedOpenPreset != null ->
                                vm.setOpenTypeGroupSelected(typedOpenPreset, group, selected)
                            deepLinkHost != null ->
                                vm.setBrowserHostGroupSelected(deepLinkHost, group, selected)
                            else -> vm.setGroupSelected(group, selected, state.filter, openPreset)
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
                        typedOpenPreset != null && component.rule in activeSelected -> when {
                            component.rule in state.selected -> stringResource(R.string.rules_inherited_from_all)
                            component.rule in explicitTypedSelected -> stringResource(
                                R.string.rules_dedicated_rule,
                                state.openTypes.localizedTitle(typedOpenPreset)
                            )
                            else -> null
                        }
                        deepLinkHost != null && component.rule in activeSelected -> when {
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
                        component.rule in activeSelected,
                        state.priorities.titles[component.rule.id],
                        selectionNote = sourceNote,
                        position = if (componentIndex == group.components.lastIndex) MiuixRowPosition.LAST else MiuixRowPosition.MIDDLE,
                        locked = if (deepLinkScoped) vm.isBulkProtected(
                            lockScope, component.rule.packageName, component.rule.id
                        ) else vm.isRuleBulkProtected(state.filter, openPreset, component.rule),
                        lockToggleEnabled = if (deepLinkScoped) {
                            !vm.isBulkAppLocked(lockScope, component.rule.packageName)
                        } else !vm.isRuleAppLockedForRule(state.filter, openPreset, component.rule),
                        onToggle = {
                            when {
                                typedOpenPreset != null ->
                                    vm.toggleOpenType(typedOpenPreset, component.rule)
                                deepLinkHost != null -> vm.toggleBrowserHost(deepLinkHost, component.rule)
                                else -> vm.toggle(component.rule)
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
                        onEditTitle = { editingTitle = component }
                    )
                }
            }
        }
        if (!state.loading && shownGroups.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AdaptiveText(stringResource(when {
                        state.error != null -> R.string.rules_empty_scan_failed
                        state.candidates.isEmpty() -> R.string.rules_empty_unscanned
                        state.query.isNotBlank() || state.uiFilter != UiFilter.ALL -> R.string.rules_empty_filtered
                        state.filter == IntentKind.DIRECT_SHARE || state.filter == IntentKind.SHORTCUT_ITEM ->
                            R.string.observed_entry_empty_help
                        else -> R.string.no_matching_components
                    }))
                    if (state.query.isNotBlank() || state.uiFilter != UiFilter.ALL) {
                        AdaptiveTextButton(onClick = {
                            vm.setQuery("")
                            vm.setUiFilter(UiFilter.ALL)
                        }) { AdaptiveText(stringResource(R.string.rules_empty_clear_filters)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(state: MainState, displayedApps: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AdaptiveText(
            stringResource(R.string.app_list_count, displayedApps),
            style = MaterialTheme.typography.labelLarge
        )
        AdaptiveText(
            stringResource(
                when (state.displayMode) {
                    DisplayMode.HIDE_SELECTED -> R.string.rules_mode_hide_selected
                    DisplayMode.SHOW_SELECTED -> R.string.rules_mode_show_selected
                    DisplayMode.SHOW_ALL -> R.string.rules_mode_show_all
                }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            AdaptiveText(
                stringResource(if (state.runtime.ready) R.string.rules_pause_confirmed else R.string.rules_pause_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (state.displayMode == DisplayMode.SHOW_SELECTED) {
            AdaptiveText(
                stringResource(R.string.rules_show_selected_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        state.error?.let {
            AdaptiveText(stringResource(R.string.rules_refresh_incomplete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun PriorityTab(state: MainState, vm: MainViewModel) {
    PriorityDialogContent(state, vm)
}

@Composable
internal fun DashboardTabContent(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit,
    themeStyle: UiThemeStyle,
    onThemeStyleChange: (UiThemeStyle) -> Unit,
) {
    val fileCheckStatus by vm.fileCheckStatus.collectAsStateWithLifecycle()
    val checkingFile by vm.checkingFile.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var showScopeDetails by rememberSaveable { mutableStateOf(false) }
    var showAppScopePicker by rememberSaveable { mutableStateOf(false) }
    if (showScopeDetails) ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    if (showAppScopePicker) {
        AppScopePickerDialog(
            selected = state.hiddenFromApps,
            onSelectedChange = vm::setHiddenFromApps
        ) { showAppScopePicker = false }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (themeStyle == UiThemeStyle.MIUIX_STYLE) 14.dp else 16.dp,
                vertical = if (themeStyle == UiThemeStyle.MIUIX_STYLE) 4.dp else 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (themeStyle == UiThemeStyle.MIUIX_STYLE) 8.dp else 16.dp
        )
    ) {
        RuntimePanel(state, vm)

        PageSectionTitle(stringResource(R.string.ui_appearance_title))
        AdaptiveCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (themeStyle == UiThemeStyle.MIUIX_STYLE) {
                    NativeSuperDropdown(
                        items = UiThemeStyle.entries.map { option ->
                            stringResource(if (option == UiThemeStyle.MATERIAL)
                                R.string.ui_appearance_material else R.string.ui_appearance_miuix)
                        },
                        selectedIndex = UiThemeStyle.entries.indexOf(themeStyle),
                        title = stringResource(R.string.ui_appearance_theme_choice),
                        summary = stringResource(R.string.ui_appearance_miuix_help),
                        onSelectedIndexChange = { selectedIndex ->
                            UiThemeStyle.entries.getOrNull(selectedIndex)?.let(onThemeStyleChange)
                        }
                    )
                } else {
                    AdaptiveText(stringResource(R.string.ui_appearance_theme_choice), fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UiThemeStyle.entries.forEach { option ->
                            AdaptiveFilterChip(
                                selected = themeStyle == option,
                                onClick = { onThemeStyleChange(option) },
                                label = {
                                    AdaptiveText(stringResource(
                                        if (option == UiThemeStyle.MATERIAL) R.string.ui_appearance_material
                                        else R.string.ui_appearance_miuix
                                    ))
                                },
                                leadingIcon = if (themeStyle == option) {
                                    { AdaptiveIcon(Icons.Rounded.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                    AdaptiveText(
                        stringResource(
                            if (themeStyle == UiThemeStyle.MATERIAL) R.string.ui_appearance_material_help
                            else R.string.ui_appearance_miuix_help
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_global_mode))
        AdaptiveCard(Modifier.fillMaxWidth()) {
            if (themeStyle == UiThemeStyle.MIUIX_STYLE) {
                NativeSuperDropdown(
                    items = DisplayMode.entries.map { mode -> stringResource(mode.titleRes()) },
                    selectedIndex = DisplayMode.entries.indexOf(state.displayMode),
                    title = stringResource(R.string.dashboard_global_mode),
                    summary = stringResource(R.string.dashboard_global_mode_help),
                    onSelectedIndexChange = { index ->
                        DisplayMode.entries.getOrNull(index)?.let(vm::setDisplayMode)
                    }
                )
            } else {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AdaptiveText(stringResource(state.displayMode.titleRes()), fontWeight = FontWeight.Bold)
                        AdaptiveText(stringResource(R.string.dashboard_global_mode_help), style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        AdaptiveTextButton(onClick = { menu = true }) {
                            AdaptiveText(stringResource(R.string.dashboard_switch))
                            AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                        }
                        AdaptiveDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DisplayMode.entries.forEach { mode ->
                                AdaptiveDropdownMenuItem(
                                    text = { AdaptiveText(stringResource(mode.titleRes())) },
                                    leadingIcon = { if (mode == state.displayMode) AdaptiveIcon(Icons.Rounded.Check, null) },
                                    onClick = { menu = false; vm.setDisplayMode(mode) }
                                )
                            }
                        }
                    }
                }
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_sync_status))
        AdaptiveCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                AdaptiveText(state.syncStatus, fontWeight = FontWeight.Bold)
                AdaptiveText(stringResource(R.string.dashboard_sync_help), style = MaterialTheme.typography.bodySmall)
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_runtime_hits))
        AdaptiveCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveText(
                    stringResource(if (state.runtime.ready) R.string.dashboard_ack_confirmed else R.string.dashboard_ack_missing),
                    fontWeight = FontWeight.Bold
                )
                AdaptiveText(stringResource(R.string.dashboard_query_hits, state.runtime.queryHits), style = MaterialTheme.typography.bodySmall)
                AdaptiveText(stringResource(R.string.dashboard_visibility_hits, state.runtime.visibilityHits), style = MaterialTheme.typography.bodySmall)
                AdaptiveText(stringResource(R.string.dashboard_ordering_hits, state.runtime.orderingHits), style = MaterialTheme.typography.bodySmall)
                AdaptiveText(
                    stringResource(R.string.dashboard_hits_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveOutlinedButton(onClick = vm::refreshModuleStatus, modifier = Modifier.fillMaxWidth()) {
                    AdaptiveText(stringResource(R.string.dashboard_refresh_runtime))
                }
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_data_backup))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdaptiveButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                AdaptiveText(stringResource(R.string.dashboard_restore_json))
            }
            AdaptiveOutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                AdaptiveText(stringResource(R.string.dashboard_export_json))
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_module_status))
        ModuleStatusRow(state) { showScopeDetails = true; vm.refreshModuleStatus() }
        AdaptiveCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (themeStyle == UiThemeStyle.MIUIX_STYLE) {
                    NativeSuperArrow(
                        title = stringResource(R.string.dashboard_manage_app_visibility_plain),
                        summary = stringResource(R.string.dashboard_app_visibility_help),
                        onClick = { showAppScopePicker = true }
                    )
                } else {
                    AdaptiveText(stringResource(R.string.dashboard_app_visibility), fontWeight = FontWeight.Bold)
                    AdaptiveText(stringResource(R.string.dashboard_app_visibility_help), style = MaterialTheme.typography.bodySmall)
                    AdaptiveButton(onClick = { showAppScopePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        AdaptiveText(stringResource(R.string.dashboard_manage_app_visibility_plain))
                    }
                }
                AdaptiveText(
                    stringResource(R.string.dashboard_app_visibility_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PageSectionTitle(stringResource(R.string.dashboard_diagnostics))
        AdaptiveCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveText(stringResource(R.string.dashboard_scan_disclaimer), style = MaterialTheme.typography.labelSmall)
                AdaptiveOutlinedButton(
                    onClick = onInspectFile,
                    enabled = !checkingFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AdaptiveText(stringResource(if (checkingFile) R.string.dashboard_inspecting_file else R.string.dashboard_inspect_file))
                }
                fileCheckStatus?.let { AdaptiveText(it, style = MaterialTheme.typography.bodySmall) }
                if (themeStyle == UiThemeStyle.MIUIX_STYLE) {
                    NativeSuperSwitch(
                        checked = state.diagnosticMode,
                        onCheckedChange = vm::setDiagnosticMode,
                        title = stringResource(R.string.diagnostic_mode),
                        summary = stringResource(R.string.diagnostic_mode_help)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdaptiveText(stringResource(R.string.diagnostic_mode), Modifier.weight(1f))
                        AdaptiveSwitch(state.diagnosticMode, vm::setDiagnosticMode)
                    }
                    AdaptiveText(
                        stringResource(R.string.diagnostic_mode_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.diagnosticMode) {
                    AdaptiveText(stringResource(R.string.diagnostic_enabled_help), style = MaterialTheme.typography.bodySmall)
                }
                AdaptiveButton(
                    onClick = onCollectDiagnostics,
                    enabled = !collectingDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (collectingDiagnostics) {
                        AdaptiveCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    AdaptiveText(stringResource(if (collectingDiagnostics) R.string.dashboard_collecting else R.string.dashboard_export_diagnostics))
                }
                AdaptiveText(stringResource(R.string.diagnostic_export_help), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(32.dp))
        AdaptiveText(
            stringResource(R.string.app_version_format, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
