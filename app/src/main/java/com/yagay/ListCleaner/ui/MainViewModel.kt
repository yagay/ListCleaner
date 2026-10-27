package com.yagay.ListCleaner.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Destination(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RULES(Icons.AutoMirrored.Rounded.List),
    PRIORITY(Icons.AutoMirrored.Rounded.Sort),
    TILES(Icons.Rounded.GridView),
    SETTINGS(Icons.Rounded.Settings)
}

enum class UiFilter {
    ALL,
    HIDE_SELECTED,
    SHOW_SELECTED,
    LOCKED
}

data class MainState(
    val module: ModuleStatus = ModuleStatus(),
    val syncStatus: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val candidates: List<ComponentCandidate> = emptyList(),
    val selected: Set<ComponentRule> = emptySet(),
    val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val filter: IntentKind? = null,
    val query: String = "",
    val uiFilter: UiFilter = UiFilter.ALL,
    val diagnosticMode: Boolean = false,
    val priorities: PriorityConfig = PriorityConfig(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    val openTypesExplicit: OpenTypeConfig = OpenTypeConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    val browserAvailableHosts: Set<String> = emptySet(),
    val hiddenFromApps: Set<String> = emptySet(),
    val groups: List<AppGroup> = emptyList(),
    val destination: Destination = Destination.RULES,
    val expandedAppKey: String? = null,
    val runtime: RuntimeStatus = RuntimeStatus()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ListCleanerApp
    val spaces = app.spaces.spaces
    val selectedSpace = app.spaces.selected
    val spaceScanState = app.spaces.scanState

    fun selectSpace(userId: Int) {
        if (rootComponents.busy.value || !app.spaces.select(userId)) return
        candidateController.clearForSpaceChange()
        app.rules.switchSpace(userId)
        bulkLocks.notifySpaceChanged()
        rootComponents.switchSpace(userId)
        setQuery("")
        setFilter(null)
        viewModelScope.launch {
            app.spaces.refreshInstalledPackages(userId)
            app.synchronize()
            refresh(forceCatalog = true)
        }
    }

    private val bulkLocks = BulkLockStore(app)
    private val rootComponents = RootComponentsController(app, viewModelScope)
    private val moduleRuntime = ModuleRuntimeController(app, viewModelScope)
    private val candidateController = CandidateController(app, viewModelScope)
    private val priorityEditor = PriorityEditorController(
        rules = app.rules,
        bulkLocks = bulkLocks,
        canEdit = ::canEdit,
        ensureBrowserHostConfigured = ::ensureBrowserHostConfigured,
    )
    private val diagnostics by lazy(LazyThreadSafetyMode.NONE) {
        DiagnosticController(
            app = app,
            scope = viewModelScope,
            stateProvider = { state.value },
            rootScanProvider = { rootComponents.scan.value },
            rootLastOperationProvider = { rootComponents.lastOperation },
        )
    }

    val componentScan = rootComponents.scan
    val componentBusy = rootComponents.busy
    val componentMessage = rootComponents.message
    internal val componentBatchProgress = rootComponents.batchProgress
    fun cancelComponentBatch() = rootComponents.cancelBatch()
    val componentRootNotice = rootComponents.rootNotice
    val bulkLockRevision: StateFlow<Long> = bulkLocks.revision

    val updating: StateFlow<Boolean> = moduleRuntime.updating
    val updateMessage: StateFlow<String?> = moduleRuntime.updateMessage

    val fileCheckStatus: StateFlow<String?> get() = diagnostics.fileCheckStatus
    val checkingFile: StateFlow<Boolean> get() = diagnostics.checkingFile
    val collectingDiagnostics: StateFlow<Boolean> get() = diagnostics.collectingDiagnostics
    val exportMessage: StateFlow<String?> get() = diagnostics.exportMessage

    fun dismissComponentRootNotice() = rootComponents.dismissRootNotice()
    fun refreshComponents() = rootComponents.refresh()
    fun lastScanDetails(): String = app.catalog.lastReport.takeLast(8_000)

    fun refreshAllCategories() {
        refresh(forceCatalog = true, manualProactive = true)
        rootComponents.refresh()
    }

    fun ensureComponentsFresh() = rootComponents.refreshIfStale()
    fun changeComponent(target: RootComponent, enable: Boolean) = rootComponents.change(target, enable)
    fun changeComponents(targets: List<RootComponent>, enable: Boolean) = rootComponents.change(targets, enable)
    fun invertComponents(targets: List<RootComponent>) = rootComponents.invert(targets)
    fun inspectFile(uri: Uri) = diagnostics.inspectFile(uri)
    fun exportDiagnostics(uri: Uri) = diagnostics.exportDiagnostics(uri)
    fun clearExportMessage() = diagnostics.clearExportMessage()

    private fun componentScopes(kind: CleanupKind?, item: RootComponent): List<String> =
        if (kind != null) {
            listOf(componentBulkLockScope(kind))
        } else {
            listOf(componentBulkLockScope(null), componentBulkLockScope(item.kind)).distinct()
        }

    internal fun isComponentBulkProtected(kind: CleanupKind?, item: RootComponent): Boolean =
        componentScopes(kind, item).any { scope ->
            bulkLocks.isProtected(scope, componentBulkLockAppId(item), item.id)
        }

    internal fun componentBulkLockState(
        kind: CleanupKind?,
        appId: String,
        items: Collection<RootComponent>
    ): BulkLockState {
        val distinct = items.distinctBy { it.id }
        if (distinct.isEmpty()) return BulkLockState.NONE
        val protectedCount = distinct.count { item ->
            componentScopes(kind, item).any { scope -> bulkLocks.isProtected(scope, appId, item.id) }
        }
        return when {
            protectedCount == 0 -> BulkLockState.NONE
            protectedCount == distinct.size -> BulkLockState.FULL
            else -> BulkLockState.PARTIAL
        }
    }

    internal fun isComponentAppLockedForItem(kind: CleanupKind?, item: RootComponent): Boolean =
        componentScopes(kind, item).any { scope ->
            bulkLocks.isAppLocked(scope, componentBulkLockAppId(item))
        }

    internal fun setComponentAppLocked(
        kind: CleanupKind?,
        appId: String,
        items: Collection<RootComponent>,
        locked: Boolean
    ) {
        val distinct = items.distinctBy { it.id }
        if (kind != null) {
            bulkLocks.setAppLocked(componentBulkLockScope(kind), appId, distinct.map { it.id }, locked)
            return
        }
        bulkLocks.setAppLocked(componentBulkLockScope(null), appId, distinct.map { it.id }, false)
        distinct.groupBy { it.kind }.forEach { (entryKind, scopedItems) ->
            bulkLocks.setAppLocked(
                componentBulkLockScope(entryKind),
                appId,
                scopedItems.map { it.id },
                locked
            )
        }
    }

    internal fun setComponentItemLocked(kind: CleanupKind?, item: RootComponent, locked: Boolean) {
        if (kind != null) {
            bulkLocks.setItemLocked(componentBulkLockScope(kind), item.id, locked)
            return
        }
        bulkLocks.setItemLocked(componentBulkLockScope(null), item.id, false)
        bulkLocks.setItemLocked(componentBulkLockScope(item.kind), item.id, locked)
    }

    fun changeComponentsBulk(kind: CleanupKind?, targets: List<RootComponent>, enable: Boolean) {
        val editable = targets.filterNot { isComponentBulkProtected(kind, it) }
        if (editable.isNotEmpty()) rootComponents.change(editable, enable)
    }

    fun changeComponentsDesired(kind: CleanupKind?, desired: Map<RootComponent, Boolean>) {
        val editable = desired.filter { (item, _) -> !isComponentBulkProtected(kind, item) }
        if (editable.isNotEmpty()) rootComponents.changeDesired(editable)
    }

    fun invertComponentsBulk(kind: CleanupKind?, targets: List<RootComponent>) {
        val editable = targets.filterNot { isComponentBulkProtected(kind, it) }
        if (editable.isNotEmpty()) rootComponents.invert(editable)
    }

    private val candidates = candidateController.candidates
    private val discoveredBrowserHosts = candidateController.browserHosts
    private val loading = candidateController.loading
    private val error = candidateController.error

    private val entryContext = MutableStateFlow(EntryContext())
    private val filter = entryContext.map { it.kind }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val ruleOpenPreset: StateFlow<OpenPreset?> = entryContext.map { it.openPreset }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val ruleBrowserHost: StateFlow<String?> = entryContext.map { it.browserHost }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    private val query = MutableStateFlow("")
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedQuery = query.debounce(120).distinctUntilChanged()
    private val uiFilter = MutableStateFlow(UiPreferencesStore.initialFilter(app, "rules"))
    private val moduleStatus: StateFlow<ModuleStatus> = moduleRuntime.status
    private val destination = MutableStateFlow(UiPreferencesRuntime.current.startPage.destination())
    private val expandedAppKey = MutableStateFlow<String?>(null)
    // Keep independent navigation state for each top-level page.
    private val pageQueries = mutableMapOf<Destination, String>()
    private val pageExpandedApps = mutableMapOf<Destination, String?>()

    private data class PreparedCandidates(
        val candidates: List<ComponentCandidate>,
        val selected: Set<ComponentRule>,
        val groups: List<AppGroup>
    )

    private data class ListContent(
        val candidates: List<ComponentCandidate>,
        val filter: IntentKind?,
        val groups: List<AppGroup>,
        val selected: Set<ComponentRule> = emptySet(),
        val uiFilter: UiFilter = UiFilter.ALL
    )

    private data class RuntimeInputs(
        val module: ModuleStatus,
        val loading: Boolean,
        val error: String?,
        val content: ListContent,
        val runtime: RuntimeStatus,
    )

    private data class RuleInputs(
        val displayMode: DisplayMode,
        val priorities: PriorityConfig,
        val diagnosticMode: Boolean,
        val hiddenFromApps: Set<String>,
        val openTypes: OpenTypeConfig,
    )

    private data class ShellInputs(
        val syncStatus: String,
        val destination: Destination,
        val expandedAppKey: String?,
        val browserLinks: BrowserLinkConfig,
        val browserHosts: Set<String>,
    )

    private val preparedCandidates = combine(
        candidates, app.rules.rules, app.rules.activeUser
    ) { scanned, selected, _ ->
        val scopedCandidates = scanned
        val items = retainConfiguredCandidates(
            scopedCandidates,
            selected,
            app.getString(R.string.candidate_configured_not_observed)
        )
        PreparedCandidates(items, selected, baseAppGroups(items))
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PreparedCandidates(emptyList(), emptySet(), emptyList())
    )

    private val grouped = combine(
        preparedCandidates, filter, debouncedQuery, uiFilter, UiPreferencesRuntime.flow
    ) { prepared, kind, text, ui, preferences ->
        ListContent(
            prepared.candidates,
            kind,
            filterAppGroups(prepared.groups, prepared.selected, kind, text, ui, preferences.appSort, preferences),
            prepared.selected,
            ui
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListContent(emptyList(), null, emptyList())
    )

    private val runtimeInputs = combine(moduleStatus, loading, error, grouped, app.runtime) {
            module, isLoading, currentError, content, runtime ->
        RuntimeInputs(module, isLoading, currentError, content, runtime)
    }

    private val ruleInputs = combine(
        app.rules.displayMode,
        app.rules.priorities,
        app.rules.diagnosticMode,
        app.rules.hiddenFromApps,
        app.rules.openTypes,
    ) { displayMode, priorities, diagnosticMode, hiddenFromApps, openTypes ->
        RuleInputs(displayMode, priorities, diagnosticMode, hiddenFromApps, openTypes)
    }

    private val shellInputs = combine(
        app.syncStatus,
        destination,
        expandedAppKey,
        app.rules.browserLinks,
        discoveredBrowserHosts,
    ) { syncStatus, currentDestination, expanded, browserLinks, browserHosts ->
        ShellInputs(syncStatus, currentDestination, expanded, browserLinks, browserHosts)
    }

    val state: StateFlow<MainState> = combine(runtimeInputs, ruleInputs, shellInputs, query) {
            runtime, rules, shell, queryText ->
        val content = runtime.content
        MainState(
            module = runtime.module,
            loading = runtime.loading,
            error = runtime.error,
            candidates = content.candidates,
            selected = content.selected,
            runtime = runtime.runtime,
            displayMode = rules.displayMode,
            filter = content.filter,
            query = queryText,
            priorities = rules.priorities,
            groups = content.groups,
            diagnosticMode = rules.diagnosticMode,
            syncStatus = shell.syncStatus,
            destination = shell.destination,
            expandedAppKey = shell.expandedAppKey,
            hiddenFromApps = rules.hiddenFromApps,
            openTypes = effectiveOpenTypes(rules.openTypes, content.selected, rules.priorities),
            openTypesExplicit = rules.openTypes,
            browserLinks = shell.browserLinks,
            browserAvailableHosts = availableDeepLinkHosts(
                shell.browserLinks.hosts,
                shell.browserHosts,
                content.candidates
            ),
            uiFilter = content.uiFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    init {
        viewModelScope.launch {
            app.spaces.refresh()
            selectSpace(app.spaces.selected.value)
        }
        viewModelScope.launch {
            app.serviceSession.collectLatest { session ->
                val status = moduleRuntime.readStatus(session)
                refresh()
                if (session != null && status.outdated) {
                    Log.i(TAG, "Older hook generation is still running; waiting for user action or reboot")
                }
            }
        }
    }

    fun setDiagnosticMode(enabled: Boolean) {
        app.rules.setDiagnosticMode(enabled)
        refreshModuleStatus()
    }

    fun setHiddenFromApps(packages: Set<String>) {
        app.rules.setHiddenFromApps(packages)
        viewModelScope.launch { app.synchronize() }
    }

    fun setCustomOpenDefinition(preset: OpenPreset, definition: CustomOpenDefinition?) {
        if (!canEdit()) return
        app.rules.setCustomOpenDefinition(preset, definition)
        refresh()
    }

    fun refresh(forceCatalog: Boolean = false, manualProactive: Boolean = false) {
        candidateController.refresh(forceCatalog, manualProactive)
        refreshModuleStatus()
    }

    fun refreshModuleStatus() = moduleRuntime.refresh()

    fun resolveRecovery(restore: Boolean) {
        viewModelScope.launch {
            app.resolveRecovery(restore)
            refresh()
        }
    }

    fun applyModuleUpdate() = moduleRuntime.applyUpdate { refresh() }

    private fun canEdit(): Boolean = app.rules.hasLocalConfiguration().also {
        if (!it) candidateController.showError(app.getString(R.string.editing_requires_recovery))
    }

    private fun genericOpenSelected(): Set<ComponentRule> =
        app.rules.rules.value.filterTo(linkedSetOf()) { it.kind == IntentKind.OPEN }

    private fun genericDeepLinkSelected(): Set<ComponentRule> =
        app.rules.rules.value.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }

    fun setBrowserHosts(hosts: Set<String>) {
        if (!canEdit()) return
        app.rules.setBrowserHosts(hosts)
        refresh()
    }

    private fun ensureBrowserHostConfigured(host: String): String? {
        val normalized = normalizeBrowserHost(host) ?: return null
        val current = app.rules.browserLinks.value
        if (normalized in current.hosts) return normalized
        if (current.hosts.size >= BrowserLinkConfig.MAX_HOSTS) return null
        app.rules.setBrowserHosts(current.hosts + normalized)
        return normalized
    }

    fun toggle(rule: ComponentRule) { if (canEdit()) app.rules.toggle(rule) }
    /** Read the authoritative repository immediately after the synchronous Apply call. */
    fun entryRulesMatch(changes: Map<ComponentRule, Boolean>): Boolean {
        val saved = app.rules.rules.value
        return changes.all { (rule, selected) ->
            (ComponentRule.fromId(rule.id) in saved) == selected
        }
    }


    fun setGroupSelected(group: AppGroup, selected: Boolean, filter: IntentKind?, preset: OpenPreset?) {
        if (!canEdit()) return
        val editable = group.components.map { it.rule }
            .filterNot { isRuleBulkProtected(filter, preset, it) }
        if (editable.isNotEmpty()) app.rules.setSelected(editable, selected)
    }

    fun toggleOpenType(preset: OpenPreset, rule: ComponentRule) {
        if (canEdit() && rule !in genericOpenSelected()) app.rules.toggleOpenType(preset, rule)
    }

    fun setOpenTypeGroupSelected(preset: OpenPreset, group: AppGroup, selected: Boolean) {
        if (!canEdit()) return
        val editable = group.components.map { it.rule }
            .filterNot { it in genericOpenSelected() || isRuleBulkProtected(IntentKind.OPEN, preset, it) }
        if (editable.isNotEmpty()) app.rules.setOpenTypeSelected(preset, editable, selected)
    }

    fun toggleBrowserHost(host: String, rule: ComponentRule) {
        if (!canEdit() || rule in genericDeepLinkSelected()) return
        val normalized = ensureBrowserHostConfigured(host) ?: return
        app.rules.toggleBrowserHost(normalized, rule)
    }

    fun setBrowserHostGroupSelected(host: String, group: AppGroup, selected: Boolean) {
        if (!canEdit()) return
        val lockScope = browserRuleBulkLockScope(host)
        val editable = group.components.map { it.rule }.filterNot {
            it in genericDeepLinkSelected() || bulkLocks.isProtected(lockScope, it.packageName, it.id)
        }
        if (editable.isEmpty()) return
        val normalized = ensureBrowserHostConfigured(host) ?: return
        app.rules.setBrowserHostSelected(normalized, editable, selected)
    }

    fun selectBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) =
        mutateBrowserHostRules(host, rules, lockScope, SelectionOperation.SELECT)

    fun deselectBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) =
        mutateBrowserHostRules(host, rules, lockScope, SelectionOperation.DESELECT)

    fun invertBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) =
        mutateBrowserHostRules(host, rules, lockScope, SelectionOperation.INVERT)

    private fun mutateBrowserHostRules(
        host: String,
        rules: Collection<ComponentRule>,
        lockScope: String,
        operation: SelectionOperation,
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericDeepLinkSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isEmpty()) return
        val normalized = when (operation) {
            SelectionOperation.DESELECT -> normalizeBrowserHost(host)?.takeIf { it in app.rules.browserLinks.value.hosts }
            else -> ensureBrowserHostConfigured(host)
        } ?: return
        when (operation) {
            SelectionOperation.SELECT -> app.rules.setBrowserHostSelected(normalized, editable, true)
            SelectionOperation.DESELECT -> app.rules.setBrowserHostSelected(normalized, editable, false)
            SelectionOperation.INVERT -> app.rules.invertBrowserHostSelected(normalized, editable)
        }
    }

    fun selectOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) =
        mutateOpenTypeRules(preset, rules, lockScope, SelectionOperation.SELECT)

    fun deselectOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) =
        mutateOpenTypeRules(preset, rules, lockScope, SelectionOperation.DESELECT)

    fun invertOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) =
        mutateOpenTypeRules(preset, rules, lockScope, SelectionOperation.INVERT)

    private fun mutateOpenTypeRules(
        preset: OpenPreset,
        rules: Collection<ComponentRule>,
        lockScope: String,
        operation: SelectionOperation,
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericOpenSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isEmpty()) return
        when (operation) {
            SelectionOperation.SELECT -> app.rules.setOpenTypeSelected(preset, editable, true)
            SelectionOperation.DESELECT -> app.rules.setOpenTypeSelected(preset, editable, false)
            SelectionOperation.INVERT -> app.rules.invertOpenTypeSelected(preset, editable)
        }
    }

    fun selectRules(rules: Collection<ComponentRule>, filter: IntentKind?, preset: OpenPreset?) =
        mutateRules(rules, filter, preset, SelectionOperation.SELECT)

    fun deselectRules(rules: Collection<ComponentRule>, filter: IntentKind?, preset: OpenPreset?) =
        mutateRules(rules, filter, preset, SelectionOperation.DESELECT)

    fun invertRules(rules: Collection<ComponentRule>, filter: IntentKind?, preset: OpenPreset?) =
        mutateRules(rules, filter, preset, SelectionOperation.INVERT)

    private fun mutateRules(
        rules: Collection<ComponentRule>,
        filter: IntentKind?,
        preset: OpenPreset?,
        operation: SelectionOperation,
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { isRuleBulkProtected(filter, preset, it) }
        if (editable.isEmpty()) return
        when (operation) {
            SelectionOperation.SELECT -> app.rules.setSelected(editable, true)
            SelectionOperation.DESELECT -> app.rules.setSelected(editable, false)
            SelectionOperation.INVERT -> app.rules.invertSelected(editable)
        }
    }

    private enum class SelectionOperation { SELECT, DESELECT, INVERT }

    internal fun bulkLockState(scope: String, appId: String, itemIds: Collection<String>): BulkLockState =
        bulkLocks.state(scope, appId, itemIds)
    internal fun isBulkItemLocked(scope: String, itemId: String): Boolean = bulkLocks.isItemLocked(scope, itemId)
    internal fun isBulkAppLocked(scope: String, appId: String): Boolean = bulkLocks.isAppLocked(scope, appId)
    internal fun isBulkProtected(scope: String, appId: String, itemId: String): Boolean =
        bulkLocks.isProtected(scope, appId, itemId)
    internal fun setBulkAppLocked(scope: String, appId: String, itemIds: Collection<String>, locked: Boolean) =
        bulkLocks.setAppLocked(scope, appId, itemIds, locked)
    internal fun setBulkItemLocked(scope: String, itemId: String, locked: Boolean) =
        bulkLocks.setItemLocked(scope, itemId, locked)

    private fun ruleScopes(filter: IntentKind?, preset: OpenPreset?, rule: ComponentRule): List<String> {
        if (filter != null) return listOf(ruleBulkLockScope(filter, preset))

        val scopes = linkedSetOf(ruleBulkLockScope(null, null), ruleBulkLockScope(rule.kind, null))
        if (rule.kind == IntentKind.OPEN) {
            val candidate = candidates.value.firstOrNull { it.rule.id == rule.id }
            if (candidate != null) {
                val openTypes = app.rules.openTypes.value
                openTypes.configuredPresets().forEach { typedPreset ->
                    if (candidate.matchesOpenPreset(typedPreset, openTypes.customDefinitions)) {
                        scopes += ruleBulkLockScope(IntentKind.OPEN, typedPreset)
                    }
                }
            }
        }
        return scopes.toList()
    }

    internal fun ruleBulkLockState(
        filter: IntentKind?,
        preset: OpenPreset?,
        appId: String,
        rules: Collection<ComponentRule>
    ): BulkLockState {
        val distinct = rules.distinctBy { it.id }
        if (distinct.isEmpty()) return BulkLockState.NONE
        val protectedCount = distinct.count { rule ->
            ruleScopes(filter, preset, rule).any { scope -> bulkLocks.isProtected(scope, appId, rule.id) }
        }
        return when {
            protectedCount == 0 -> BulkLockState.NONE
            protectedCount == distinct.size -> BulkLockState.FULL
            else -> BulkLockState.PARTIAL
        }
    }

    internal fun isRuleBulkProtected(filter: IntentKind?, preset: OpenPreset?, rule: ComponentRule): Boolean =
        ruleScopes(filter, preset, rule).any { scope -> bulkLocks.isProtected(scope, rule.packageName, rule.id) }

    internal fun isRuleAppLocked(
        filter: IntentKind?,
        preset: OpenPreset?,
        appId: String,
        rules: Collection<ComponentRule>
    ): Boolean {
        if (filter != null) return bulkLocks.isAppLocked(ruleBulkLockScope(filter, preset), appId)
        return rules.distinctBy { it.kind }.all { rule ->
            bulkLocks.isAppLocked(ruleBulkLockScope(rule.kind, null), appId)
        } && rules.isNotEmpty()
    }

    internal fun isRuleAppLockedForRule(filter: IntentKind?, preset: OpenPreset?, rule: ComponentRule): Boolean =
        ruleScopes(filter, preset, rule).any { scope -> bulkLocks.isAppLocked(scope, rule.packageName) }

    internal fun setRuleAppLocked(
        filter: IntentKind?,
        preset: OpenPreset?,
        appId: String,
        rules: Collection<ComponentRule>,
        locked: Boolean
    ) {
        val distinct = rules.distinctBy { it.id }
        if (filter != null) {
            bulkLocks.setAppLocked(ruleBulkLockScope(filter, preset), appId, distinct.map { it.id }, locked)
            return
        }
        val ids = distinct.map { it.id }
        if (!locked) {
            distinct.flatMap { ruleScopes(null, null, it) }.distinct().forEach { scope ->
                bulkLocks.setAppLocked(scope, appId, ids, false)
            }
            return
        }

        bulkLocks.setAppLocked(ruleBulkLockScope(null, null), appId, ids, false)
        distinct.groupBy { it.kind }.forEach { (kind, scopedRules) ->
            bulkLocks.setAppLocked(ruleBulkLockScope(kind, null), appId, scopedRules.map { it.id }, true)
        }
    }

    internal fun setRuleItemLocked(filter: IntentKind?, preset: OpenPreset?, rule: ComponentRule, locked: Boolean) {
        if (filter != null) {
            bulkLocks.setItemLocked(ruleBulkLockScope(filter, preset), rule.id, locked)
            return
        }
        if (!locked) {
            ruleScopes(null, null, rule).forEach { scope -> bulkLocks.setItemLocked(scope, rule.id, false) }
            return
        }
        bulkLocks.setItemLocked(ruleBulkLockScope(null, null), rule.id, false)
        bulkLocks.setItemLocked(ruleBulkLockScope(rule.kind, null), rule.id, true)
    }

    internal fun toggleBulkAppLock(scope: String, appId: String, itemIds: Collection<String>) =
        bulkLocks.toggleApp(scope, appId, itemIds)
    internal fun toggleBulkItemLock(scope: String, itemId: String) = bulkLocks.toggleItem(scope, itemId)

    fun setDisplayMode(value: DisplayMode) { if (canEdit()) app.rules.setDisplayMode(value) }
    fun setFilter(value: IntentKind?) { entryContext.update { it.withKind(value) } }
    fun setRuleOpenPreset(value: OpenPreset?) { entryContext.update { it.copy(openPreset = value) } }
    fun setRuleBrowserHost(value: String?) { entryContext.update { it.copy(browserHost = value) } }
    fun setQuery(value: String) {
        query.value = value
        pageQueries[destination.value] = value
    }
    fun setUiFilter(value: UiFilter) {
        uiFilter.value = value
        UiPreferencesStore.rememberFilter(app, value)
    }
    fun setDestination(value: Destination) {
        // Legacy Component tab redirects into the merged Cleanup workspace.
        val target = if (value == Destination.TILES) Destination.RULES else value
        val previous = destination.value
        if (previous == target) return
        val rememberSearch = UiPreferencesRuntime.current.rememberPageSearch
        if (rememberSearch) pageQueries[previous] = query.value else pageQueries.clear()
        pageExpandedApps[previous] = expandedAppKey.value
        destination.value = target
        query.value = if (rememberSearch) pageQueries[target].orEmpty() else ""
        expandedAppKey.value = pageExpandedApps[target]
    }
    fun toggleExpandedApp(key: String) {
        val next = if (expandedAppKey.value == key) null else key
        expandedAppKey.value = next
        pageExpandedApps[destination.value] = next
    }
    /** Preserve the existing rule JSON format while adding optional manager-only preferences. */
    private fun backupExtras(content: String): Pair<org.json.JSONObject?, List<String>?> {
        val backup = org.json.JSONObject(content)
        val appearance = if (backup.has("uiPreferences")) {
            require(backup.opt("uiPreferences") is org.json.JSONObject)
            backup.getJSONObject("uiPreferences").also(UiPreferencesStore::validateSnapshot)
        } else null
        val locks = if (backup.has("bulkActionLocks")) {
            val array = backup.optJSONArray("bulkActionLocks")
                ?: throw IllegalArgumentException("Invalid bulk-action locks")
            require(array.length() <= 20_000)
            List(array.length()) { index -> array.getString(index) }
                .also(bulkLocks::validateSnapshot)
        } else null
        return appearance to locks
    }

    fun inspectSpaceBackup(content: String): com.yagay.ListCleaner.data.SpaceBackupInspection {
        require(content.length <= MAX_BACKUP_CHARS)
        return app.rules.inspectSpaces(content)
    }

    fun exportSpacesJson(userIds: Set<Int>, includeRootReference: Boolean = true): String {
        require(userIds.isNotEmpty())
        val detected = app.spaces.spaces.value
        require(userIds.all { id -> detected.any { it.userId == id } }) {
            "An Android user was removed during backup"
        }
        val names = detected.associate { it.userId to it.title }
        val rootKeys = if (includeRootReference)
            com.yagay.ListCleaner.data.PersistentComponentStore(app).disabledKeys() else emptySet()
        return app.rules.exportSpaces(userIds, names, rootKeys)
    }

    fun importMappedSpaces(content: String, targets: Map<Int, Int>) {
        require(content.length <= MAX_BACKUP_CHARS)
        require(targets.values.all { id -> app.spaces.spaces.value.any { it.userId == id } }) {
            "Target Android user is no longer available"
        }
        app.rules.importSpaces(content, targets)
        // The backup never imports executable Root actions or overwrites global UI preferences.
        refresh(forceCatalog = true)
    }

    fun backupCheckpoint(): String = app.rules.createSpaceCheckpoint()

    fun restoreBackupCheckpoint(checkpoint: String) {
        app.rules.restoreSpaceCheckpoint(checkpoint)
        refresh(forceCatalog = true)
    }

    fun previewImportJson(content: String): com.yagay.ListCleaner.data.BackupImportPreview {
        require(content.length <= MAX_BACKUP_CHARS)
        backupExtras(content)
        return app.rules.previewImportJson(content)
    }

    fun exportJson(): String {
        val backup = org.json.JSONObject(app.rules.exportJson())
        backup.put("uiPreferences", UiPreferencesStore.exportSnapshot(app))
        backup.put("bulkActionLocks", org.json.JSONArray().apply {
            bulkLocks.exportSnapshot().sorted().forEach { put(it) }
        })
        return backup.toString(2).also { require(it.length <= MAX_BACKUP_CHARS) }
    }

    fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS)
        val (appearance, locks) = backupExtras(content)
        app.rules.previewImportJson(content)
        // The legacy backup spans three stores. Keep a full checkpoint so a failure
        // in a later store does not leave rules, appearance and locks half-imported.
        val rulesBefore = app.rules.createSpaceCheckpoint()
        val appearanceBefore = UiPreferencesStore.exportSnapshot(app)
        val locksBefore = bulkLocks.exportSnapshot()
        try {
            app.rules.importJson(content)
            if (appearance != null) UiPreferencesStore.importSnapshot(app, appearance)
            if (locks != null) bulkLocks.restoreSnapshot(locks)
            else bulkLocks.reloadFromDisk()
        } catch (failure: Exception) {
            // Attempt every rollback even when an earlier rollback fails, and expose
            // the original failure with any rollback errors attached.
            listOf<() -> Unit>(
                { app.rules.restoreSpaceCheckpoint(rulesBefore) },
                { UiPreferencesStore.importSnapshot(app, appearanceBefore) },
                { bulkLocks.restoreSnapshot(locksBefore) },
            ).forEach { rollback ->
                try {
                    rollback()
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                }
            }
            throw failure
        }
    }
    fun setComponentTitle(ruleId: String, title: String?) {
        if (canEdit()) app.rules.setComponentTitle(ruleId, title)
    }

    fun applyPriorityDraft(kind: IntentKind, expected: List<String>, desired: List<String>, lockScope: String): Boolean =
        priorityEditor.applyKindDraft(kind, expected, desired, lockScope)

    fun applyOpenTypePriorityDraft(preset: OpenPreset, expected: List<String>, desired: List<String>, lockScope: String): Boolean =
        priorityEditor.applyOpenTypeDraft(preset, expected, desired, lockScope)

    fun applyBrowserHostPriorityDraft(host: String, expected: List<String>, desired: List<String>, lockScope: String): Boolean =
        priorityEditor.applyBrowserHostDraft(host, expected, desired, lockScope)

    fun selectPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.selectApps(kind, packageNames, lockScope)
    fun deselectPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.deselectApps(kind, packageNames, lockScope)
    fun invertPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.invertApps(kind, packageNames, lockScope)
    fun pinApp(kind: IntentKind, packageName: String) = priorityEditor.pin(kind, packageName)
    fun removePriority(kind: IntentKind, packageName: String) = priorityEditor.remove(kind, packageName)
    fun movePriority(kind: IntentKind, packageName: String, offset: Int, visible: List<String>) =
        priorityEditor.move(kind, packageName, offset, visible)
    fun movePriorityTo(kind: IntentKind, packageName: String, target: String, visible: List<String>, expected: List<String>) =
        priorityEditor.moveTo(kind, packageName, target, visible, expected)

    fun selectBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.selectBrowserHost(host, packageNames, lockScope)
    fun deselectBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.deselectBrowserHost(host, packageNames, lockScope)
    fun invertBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.invertBrowserHost(host, packageNames, lockScope)
    fun pinBrowserHostApp(host: String, packageName: String) = priorityEditor.pinBrowserHost(host, packageName)
    fun removeBrowserHostPriority(host: String, packageName: String) = priorityEditor.removeBrowserHost(host, packageName)
    fun moveBrowserHostPriority(host: String, packageName: String, offset: Int, visible: List<String>) =
        priorityEditor.moveBrowserHost(host, packageName, offset, visible)
    fun moveBrowserHostPriorityTo(
        host: String, packageName: String, target: String, visible: List<String>, expected: List<String>
    ) = priorityEditor.moveBrowserHostTo(host, packageName, target, visible, expected)
    fun resetBrowserHostPriority(host: String) = priorityEditor.resetBrowserHost(host)

    fun selectOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.selectOpenType(preset, packageNames, lockScope)
    fun deselectOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.deselectOpenType(preset, packageNames, lockScope)
    fun invertOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) =
        priorityEditor.invertOpenType(preset, packageNames, lockScope)
    fun pinOpenTypeApp(preset: OpenPreset, packageName: String) = priorityEditor.pinOpenType(preset, packageName)
    fun removeOpenTypePriority(preset: OpenPreset, packageName: String) = priorityEditor.removeOpenType(preset, packageName)
    fun moveOpenTypePriority(preset: OpenPreset, packageName: String, offset: Int, visible: List<String>) =
        priorityEditor.moveOpenType(preset, packageName, offset, visible)
    fun moveOpenTypePriorityTo(
        preset: OpenPreset, packageName: String, target: String, visible: List<String>, expected: List<String>
    ) = priorityEditor.moveOpenTypeTo(preset, packageName, target, visible, expected)
    fun resetOpenTypePriority(preset: OpenPreset) = priorityEditor.resetOpenType(preset)

    fun requestScope() = moduleRuntime.requestScope()

    companion object {
        const val MAX_BACKUP_CHARS = RuleRepository.MAX_BACKUP_CHARS
        private const val TAG = "ListCleaner.ViewModel"
    }
}

