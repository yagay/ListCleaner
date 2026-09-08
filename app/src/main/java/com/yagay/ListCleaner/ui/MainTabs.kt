package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.matchesOpenPreset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel) {
    var editingTitle by remember { mutableStateOf<com.yagay.ListCleaner.domain.ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
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

    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }
    LaunchedEffect(state.filter) { if (state.filter != IntentKind.OPEN) openPreset = null }
    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) openPreset = null
    }

    val typedSelected = openPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitTypedSelected = openPreset?.let { state.openTypesExplicit.selectedRules(it) }.orEmpty()
    val shownGroups = if (state.filter == IntentKind.OPEN && openPreset != null) {
        groupCandidates(
            state.candidates.filter { it.matchesOpenPreset(openPreset!!, state.openTypesExplicit.customDefinitions) },
            typedSelected,
            IntentKind.OPEN,
            state.query,
            state.uiFilter
        )
    } else {
        state.groups
    }
    val activeSelected = if (openPreset != null && state.filter == IntentKind.OPEN) typedSelected else state.selected
    val visibleRules = shownGroups.flatMap { it.components }.map { it.rule }.distinct()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "module-indicator") {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        stickyHeader(key = "list-controls") {
            Surface(tonalElevation = 2.dp) {
                Column {
                    ListControls(
                        state,
                        vm::setFilter,
                        vm::setUiFilter,
                        onSelectAll = {
                            if (openPreset != null && state.filter == IntentKind.OPEN) {
                                vm.selectOpenTypeRules(openPreset!!, visibleRules)
                            } else {
                                vm.selectRules(visibleRules)
                            }
                        },
                        onInvert = {
                            if (openPreset != null && state.filter == IntentKind.OPEN) {
                                vm.invertOpenTypeRules(openPreset!!, visibleRules)
                            } else {
                                vm.invertRules(visibleRules)
                            }
                        }
                    )
                    if (state.filter == IntentKind.OPEN) {
                        OpenPresetFilterRow(
                            selected = openPreset,
                            config = state.openTypesExplicit,
                            onSelected = { openPreset = it },
                            onManageCustom = { showCustomTypes = true }
                        )
                    }
                }
            }
        }
        item(key = "list-summary") {
            SummaryRow(state, shownGroups.size, openPreset)
            if (openPreset == null && state.uiFilter != UiFilter.SHOW_SELECTED && state.candidates.any {
                    it.rule in state.selected && (it.unavailable || it.restricted)
                }) {
                TextButton(
                    onClick = {
                        vm.setFilter(null)
                        vm.setQuery("")
                        vm.setUiFilter(UiFilter.SHOW_SELECTED)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.rules_view_unmatched))
                }
            }
        }
        shownGroups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${openPreset?.name ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(
                    group,
                    activeSelected,
                    expanded,
                    { vm.toggleExpandedApp(key) },
                    { selected ->
                        if (openPreset != null && state.filter == IntentKind.OPEN) {
                            vm.setOpenTypeGroupSelected(openPreset!!, group, selected)
                        } else {
                            vm.setGroupSelected(group, selected)
                        }
                    }
                )
            }
            if (expanded) {
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { component ->
                    val sourceNote = if (openPreset != null && state.filter == IntentKind.OPEN && component.rule in activeSelected) {
                        when {
                            component.rule in state.selected -> stringResource(R.string.rules_inherited_from_all)
                            component.rule in explicitTypedSelected -> stringResource(
                                R.string.rules_dedicated_rule,
                                state.openTypes.localizedTitle(openPreset!!)
                            )
                            else -> null
                        }
                    } else {
                        null
                    }
                    ComponentRow(
                        component,
                        component.rule in activeSelected,
                        state.priorities.titles[component.rule.id],
                        selectionNote = sourceNote,
                        onToggle = {
                            if (openPreset != null && state.filter == IntentKind.OPEN) vm.toggleOpenType(openPreset!!, component.rule)
                            else vm.toggle(component.rule)
                        },
                        onEditTitle = { editingTitle = component }
                    )
                }
            }
        }
        if (!state.loading && shownGroups.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_matching_components))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(state: MainState, groupCount: Int = state.groups.size, openPreset: OpenPreset? = null) {
    val presetTitle = if (openPreset != null) state.openTypes.localizedTitle(openPreset) else null
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (presetTitle == null) stringResource(R.string.app_list_count, groupCount)
            else stringResource(R.string.app_list_count_type, groupCount, presetTitle),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            stringResource(R.string.rules_page_intro),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
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
        Text(
            if (presetTitle == null) stringResource(R.string.rules_usage_help)
            else stringResource(R.string.rules_typed_help, presetTitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            Text(
                stringResource(if (state.runtime.ready) R.string.rules_pause_confirmed else R.string.rules_pause_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (state.displayMode == DisplayMode.SHOW_SELECTED) {
            Text(
                stringResource(R.string.rules_show_selected_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        state.error?.let {
            Text(stringResource(R.string.rules_refresh_incomplete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun PriorityTab(state: MainState, vm: MainViewModel) {
    PriorityDialogContent(state, vm)
}

@Composable
fun DashboardTabContent(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit
) {
    val fileCheckStatus by vm.fileCheckStatus.collectAsState()
    val checkingFile by vm.checkingFile.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var showScopeDetails by remember { mutableStateOf(false) }
    var showAppScopePicker by remember { mutableStateOf(false) }
    if (showScopeDetails) ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    if (showAppScopePicker) {
        AppScopePickerDialog(
            selected = state.hiddenFromApps,
            onSelectedChange = vm::setHiddenFromApps
        ) { showAppScopePicker = false }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RuntimePanel(state, vm)

        Text(stringResource(R.string.dashboard_global_mode), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(state.displayMode.titleRes()), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.dashboard_global_mode_help), style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text(stringResource(R.string.dashboard_switch))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.titleRes())) },
                                leadingIcon = { if (mode == state.displayMode) Icon(Icons.Rounded.Check, null) },
                                onClick = { menu = false; vm.setDisplayMode(mode) }
                            )
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.dashboard_sync_status), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(state.syncStatus, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.dashboard_sync_help), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(stringResource(R.string.dashboard_runtime_hits), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (state.runtime.ready) R.string.dashboard_ack_confirmed else R.string.dashboard_ack_missing),
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.dashboard_query_hits, state.runtime.queryHits), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.dashboard_visibility_hits, state.runtime.visibilityHits), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.dashboard_ordering_hits, state.runtime.orderingHits), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.dashboard_hits_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = vm::refreshModuleStatus, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_refresh_runtime))
                }
            }
        }

        Text(stringResource(R.string.dashboard_data_backup), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_restore_json))
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_export_json))
            }
        }

        Text(stringResource(R.string.dashboard_module_status), style = MaterialTheme.typography.titleMedium)
        ModuleStatusRow(state) { showScopeDetails = true; vm.refreshModuleStatus() }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dashboard_app_visibility), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.dashboard_app_visibility_help), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showAppScopePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_manage_app_visibility, state.hiddenFromApps.size))
                }
                Text(
                    stringResource(R.string.dashboard_app_visibility_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(stringResource(R.string.dashboard_diagnostics), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.dashboard_scan_disclaimer), style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = onInspectFile,
                    enabled = !checkingFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (checkingFile) R.string.dashboard_inspecting_file else R.string.dashboard_inspect_file))
                }
                fileCheckStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diagnostic_mode), Modifier.weight(1f))
                    Switch(state.diagnosticMode, vm::setDiagnosticMode)
                }
                Text(
                    stringResource(R.string.diagnostic_mode_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.diagnosticMode) {
                    Text(stringResource(R.string.diagnostic_enabled_help), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onCollectDiagnostics,
                    enabled = !collectingDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (collectingDiagnostics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (collectingDiagnostics) R.string.dashboard_collecting else R.string.dashboard_export_diagnostics))
                }
                Text(stringResource(R.string.diagnostic_export_help), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.app_version_format, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
