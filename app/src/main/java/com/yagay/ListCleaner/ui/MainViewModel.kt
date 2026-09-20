package com.yagay.ListCleaner.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Sort
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.BrowserLinkDiscovery
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenSelectionSource
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Destination(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RULES(Icons.Rounded.List),
    PRIORITY(Icons.Rounded.Sort),
    TILES(Icons.Rounded.GridView),
    DASHBOARD(Icons.Rounded.Dashboard)
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
    /** Effective per-type config after generic OPEN inheritance is projected for display. */
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    /** Raw persisted per-type config used to distinguish inherited values from explicit values. */
    val openTypesExplicit: OpenTypeConfig = OpenTypeConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    val browserAvailableHosts: Set<String> = emptySet(),
    val hiddenFromApps: Set<String> = emptySet(),
    val groups: List<AppGroup> = emptyList(),
    val destination: Destination = Destination.RULES,
    val expandedAppKey: String? = null,
    val runtime: RuntimeStatus = RuntimeStatus()
)

data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val appType: AppType,
    val components: List<ComponentCandidate>
)

private fun appSelectionRank(group: AppGroup, selected: Set<ComponentRule>): Int {
    val selectedCount = group.components.count { it.rule in selected }
    return when {
        group.components.isNotEmpty() && selectedCount == group.components.size -> 0
        selectedCount > 0 -> 1
        else -> 2
    }
}

private fun baseAppGroups(candidates: List<ComponentCandidate>): List<AppGroup> =
    candidates.groupBy { it.rule.packageName }.map { (_, all) ->
        val first = all.first()
        AppGroup(
            first.rule.packageName,
            first.appLabel,
            first.appIcon,
            first.appType,
            all.sortedBy { it.rule.kind.ordinal }
        )
    }

private fun filterAppGroups(
    groups: List<AppGroup>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = groups.mapNotNull { group ->
    val matching = group.components.filter {
        val isSelected = it.rule in selected
        val matchesUiFilter = when (uiFilter) {
            UiFilter.ALL -> true
            UiFilter.HIDE_SELECTED -> !isSelected
            UiFilter.SHOW_SELECTED -> isSelected
            UiFilter.LOCKED -> true
        }
        catalogVisible(it, isSelected, uiFilter) && matchesUiFilter &&
            (filter == null || it.rule.kind == filter) && it.matchesQuery(query)
    }
    if (matching.isEmpty()) null else group.copy(components = matching)
}.sortedWith(
    compareBy<AppGroup> { appSelectionRank(it, selected) }
        .thenBy { it.components.firstOrNull()?.normalizedAppLabel ?: it.appLabel.lowercase() }
        .thenBy { it.packageName }
)

fun groupCandidates(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = filterAppGroups(baseAppGroups(candidates), selected, filter, query, uiFilter)

fun retainConfiguredCandidates(
    items: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    unavailableEvidence: String = "Configured but not observed during this scan; this does not mean the app is uninstalled"
): List<ComponentCandidate> {
    val kept = items.filter { !it.unavailable || it.rule in selected }
    val ids = kept.map { it.rule.id }.toSet()
    return kept + selected.filter { it.id !in ids }.map { rule ->
        ComponentCandidate(
            rule,
            rule.packageName,
            rule.className.substringAfterLast('.'),
            evidence = listOf(unavailableEvidence),
            unavailable = true
        )
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ListCleanerApp
    private val bulkLocks = BulkLockStore(app)
    private val rootComponents = RootComponentsController(app, viewModelScope)
    private val browserLinkDiscovery = BrowserLinkDiscovery()
    private val moduleRuntime = ModuleRuntimeController(app, viewModelScope)

    val componentScan = rootComponents.scan
    val componentBusy = rootComponents.busy
    val componentMessage = rootComponents.message
    val componentRootNotice = rootComponents.rootNotice
    val bulkLockRevision: StateFlow<Long> = bulkLocks.revision

    fun dismissComponentRootNotice() = rootComponents.dismissRootNotice()
    fun refreshComponents() = rootComponents.refresh()
    fun changeComponent(target: RootComponent, enable: Boolean) = rootComponents.change(target, enable)
    fun changeComponents(targets: List<RootComponent>, enable: Boolean) = rootComponents.change(targets, enable)
    fun invertComponents(targets: List<RootComponent>) = rootComponents.invert(targets)

    private fun componentScopes(kind: CleanupKind?, item: RootComponent): List<String> =
        if (kind != null) {
            listOf(componentBulkLockScope(kind))
        } else {
            listOf(
                componentBulkLockScope(null),
                componentBulkLockScope(item.kind)
            ).distinct()
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
            componentScopes(kind, item).any { scope ->
                bulkLocks.isProtected(scope, appId, item.id)
            }
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
            bulkLocks.setAppLocked(
                componentBulkLockScope(kind),
                appId,
                distinct.map { it.id },
                locked
            )
            return
        }
        bulkLocks.setAppLocked(
            componentBulkLockScope(null),
            appId,
            distinct.map { it.id },
            false
        )
        distinct.groupBy { it.kind }.forEach { (entryKind, scopedItems) ->
            bulkLocks.setAppLocked(
                componentBulkLockScope(entryKind),
                appId,
                scopedItems.map { it.id },
                locked
            )
        }
    }

    internal fun setComponentItemLocked(
        kind: CleanupKind?,
        item: RootComponent,
        locked: Boolean
    ) {
        if (kind != null) {
            bulkLocks.setItemLocked(componentBulkLockScope(kind), item.id, locked)
            return
        }
        // Migrate old All-page item locks so a left swipe can always unlock them.
        bulkLocks.setItemLocked(componentBulkLockScope(null), item.id, false)
        bulkLocks.setItemLocked(componentBulkLockScope(item.kind), item.id, locked)
    }

    fun changeComponentsBulk(kind: CleanupKind?, targets: List<RootComponent>, enable: Boolean) {
        val editable = targets.filterNot { isComponentBulkProtected(kind, it) }
        if (editable.isNotEmpty()) rootComponents.change(editable, enable)
    }

    fun invertComponentsBulk(kind: CleanupKind?, targets: List<RootComponent>) {
        val editable = targets.filterNot { isComponentBulkProtected(kind, it) }
        if (editable.isNotEmpty()) rootComponents.invert(editable)
    }

    val updating: StateFlow<Boolean> = moduleRuntime.updating
    val updateMessage: StateFlow<String?> = moduleRuntime.updateMessage

    private val candidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    private val discoveredBrowserHosts = MutableStateFlow<Set<String>>(emptySet())
    private val mutableFileCheckStatus = MutableStateFlow<String?>(null)
    val fileCheckStatus: StateFlow<String?> = mutableFileCheckStatus
    private val mutableCheckingFile = MutableStateFlow(false)
    val checkingFile: StateFlow<Boolean> = mutableCheckingFile

    private fun openPresetTitle(config: OpenTypeConfig, preset: OpenPreset): String =
        config.customDefinitions[preset]?.title ?: app.getString(preset.titleRes())

    private fun selectionSourceTitle(source: OpenSelectionSource): String = app.getString(
        when (source) {
            OpenSelectionSource.GENERIC -> R.string.file_preview_source_generic
            OpenSelectionSource.TYPED -> R.string.file_preview_source_typed
            OpenSelectionSource.GENERIC_AND_TYPED -> R.string.file_preview_source_generic_and_typed
        }
    )

    fun inspectFile(uri: Uri) {
        if (mutableCheckingFile.value) return
        mutableCheckingFile.value = true
        viewModelScope.launch {
            mutableFileCheckStatus.value = app.getString(R.string.file_preview_checking)
            try {
                // File inspection is a local PackageManager/catalog operation. Runtime readiness
                // only determines whether the current rules are already active in hooked processes.
                val mime = app.contentResolver.getType(uri)
                val found = app.catalog.inspectFile(uri)
                val config = app.rules.remoteSnapshot()
                val preview = com.yagay.ListCleaner.domain.previewOpenEffect(
                    found,
                    config.rules,
                    config.mode,
                    config.priorities,
                    config.openTypes,
                    mime,
                    uri.scheme,
                    uri.lastPathSegment ?: uri.path
                )
                mutableFileCheckStatus.value = buildString {
                    val typeTitle = preview.preset?.let { openPresetTitle(config.openTypes, it) }
                        ?: app.getString(R.string.file_preview_generic_open)
                    append(app.getString(
                        R.string.file_preview_header,
                        typeTitle,
                        mime ?: app.getString(R.string.common_unknown)
                    ))
                    append(app.getString(R.string.file_preview_counts, preview.rawCount, preview.finalCount))
                    if (preview.restoredEmpty) append(app.getString(R.string.file_preview_empty_restored))
                    append(app.getString(R.string.file_preview_disclaimer))
                    val details = preview.items.take(12)
                    if (details.isNotEmpty()) append('\n')
                    details.forEachIndexed { index, item ->
                        if (index > 0) append('\n')
                        append(if (item.included) "✓ " else "✕ ")
                        append(item.candidate.appLabel)
                        item.rank?.let { append(app.getString(R.string.file_preview_rank, it)) }
                        item.selectedBy?.let {
                            append(app.getString(R.string.file_preview_source, selectionSourceTitle(it)))
                        }
                    }
                    if (preview.items.size > details.size) {
                        append(app.getString(R.string.file_preview_more, preview.items.size - details.size))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "File preview failed", failure)
                mutableFileCheckStatus.value = app.getString(R.string.file_preview_failed)
            } finally {
                mutableCheckingFile.value = false
            }
        }
    }

    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow<IntentKind?>(null)
    private val query = MutableStateFlow("")
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedQuery = query.debounce(120).distinctUntilChanged()
    private val uiFilter = MutableStateFlow(UiFilter.ALL)
    private val moduleStatus: StateFlow<ModuleStatus> = moduleRuntime.status
    private val destination = MutableStateFlow(Destination.RULES)
    private val expandedAppKey = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    private val mutableCollectingDiagnostics = MutableStateFlow(false)
    val collectingDiagnostics: StateFlow<Boolean> = mutableCollectingDiagnostics
    private val mutableExportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = mutableExportMessage

    fun clearExportMessage() { mutableExportMessage.value = null }

    fun exportDiagnostics(uri: Uri) {
        if (mutableCollectingDiagnostics.value) return
        mutableCollectingDiagnostics.value = true
        viewModelScope.launch {
            var report: File? = null
            try {
                val config = app.rules.remoteSnapshot()
                report = DiagnosticCollector.collect(
                    app,
                    state.value.copy(
                        module = moduleStatus.value,
                        selected = config.rules,
                        displayMode = config.mode,
                        priorities = config.priorities,
                        diagnosticMode = config.diagnostic,
                        openTypes = config.openTypes,
                        openTypesExplicit = config.openTypes,
                        runtime = app.runtime.value,
                        syncStatus = app.syncStatus.value
                    ),
                    rootComponents.scan.value,
                    rootComponents.lastOperation
                )
                val ready = requireNotNull(report)
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt")
                        ?: error(app.getString(R.string.diagnostic_create_failed))
                    output.use { destination -> ready.inputStream().use { it.copyTo(destination) } }
                }
                mutableExportMessage.value = app.getString(R.string.diagnostic_exported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Diagnostic export failed", failure)
                mutableExportMessage.value = app.getString(
                    R.string.diagnostic_export_failed,
                    app.getString(R.string.diagnostic_create_failed)
                )
            } finally {
                report?.delete()
                mutableCollectingDiagnostics.value = false
            }
        }
    }

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

    private val preparedCandidates = combine(candidates, app.rules.rules) { scanned, selected ->
        val items = retainConfiguredCandidates(
            scanned,
            selected,
            app.getString(R.string.candidate_configured_not_observed)
        )
        PreparedCandidates(items, selected, baseAppGroups(items))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PreparedCandidates(emptyList(), emptySet(), emptyList())
    )

    private val grouped = combine(preparedCandidates, filter, debouncedQuery, uiFilter) { prepared, kind, text, ui ->
        ListContent(
            prepared.candidates,
            kind,
            filterAppGroups(prepared.groups, prepared.selected, kind, text, ui),
            prepared.selected,
            ui
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListContent(emptyList(), null, emptyList())
    )

    val state: StateFlow<MainState> = combine(
        moduleStatus,
        loading,
        error,
        grouped,
        app.runtime,
        app.rules.displayMode,
        app.rules.priorities,
        app.rules.diagnosticMode,
        app.syncStatus,
        destination,
        expandedAppKey,
        app.rules.hiddenFromApps,
        app.rules.openTypes,
        app.rules.browserLinks,
        discoveredBrowserHosts,
        query
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val content = values[3] as ListContent
        val priorityConfig = values[6] as PriorityConfig
        val rawOpenTypes = values[12] as OpenTypeConfig
        val browserLinks = values[13] as BrowserLinkConfig
        @Suppress("UNCHECKED_CAST")
        val discoveredHosts = values[14] as Set<String>
        MainState(
            module = values[0] as ModuleStatus,
            loading = values[1] as Boolean,
            error = values[2] as String?,
            candidates = content.candidates,
            selected = content.selected,
            runtime = values[4] as RuntimeStatus,
            displayMode = values[5] as DisplayMode,
            filter = content.filter,
            query = values[15] as String,
            priorities = priorityConfig,
            groups = content.groups,
            diagnosticMode = values[7] as Boolean,
            syncStatus = values[8] as String,
            destination = values[9] as Destination,
            expandedAppKey = values[10] as String?,
            hiddenFromApps = values[11] as Set<String>,
            openTypes = effectiveOpenTypes(rawOpenTypes, content.selected, priorityConfig),
            openTypesExplicit = rawOpenTypes,
            browserLinks = browserLinks,
            browserAvailableHosts = browserLinks.hosts + discoveredHosts,
            uiFilter = content.uiFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    init {
        viewModelScope.launch {
            app.serviceSession.collectLatest { session ->
                val status = moduleRuntime.readStatus(session)
                refresh()
                // Updating the APK does not restart system_server/Resolver. Try the supported
                // hot-update path automatically, but never make local catalog availability depend
                // on whether every running Xposed entry can reload in place.
                if (session != null && status.outdated) {
                    moduleRuntime.applyUpdate(::refresh)
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

    fun refresh(forceCatalog: Boolean = false) {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val configured = app.rules.rules.value +
                    app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId) +
                    app.rules.browserLinks.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                candidates.value = app.catalog.completeConfigured(candidates.value, configured)

                // Candidate discovery belongs to the manager app and must stay usable even when
                // system_server/Resolver still has the previous module version after an APK update.
                // Runtime synchronization continues independently through ListCleanerApp and the
                // status controller; it gates system-side effect, not local list visibility.
                val autoHosts = browserLinkDiscovery.discover(forceCatalog)
                if (generation == refreshGeneration) discoveredBrowserHosts.value = autoHosts
                val result = app.catalog.scan(
                    app.rules.openTypes.value.customDefinitions,
                    app.rules.browserLinks.value.hosts + autoHosts,
                    force = forceCatalog
                )
                if (generation == refreshGeneration) {
                    val updatedConfigured = app.rules.rules.value +
                        app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId) +
                        app.rules.browserLinks.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                    candidates.value = app.catalog.completeConfigured(result, updatedConfigured)
                    error.value = app.catalog.scanWarning
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Log.e(TAG, "Candidate scan failed", failure)
                if (generation == refreshGeneration) error.value = app.getString(R.string.scan_failed)
            } finally {
                if (generation == refreshGeneration) loading.value = false
            }
        }
        refreshModuleStatus()
    }

    fun refreshModuleStatus() = moduleRuntime.refresh()

    fun resolveRecovery(restore: Boolean) {
        viewModelScope.launch {
            app.resolveRecovery(restore)
            refresh()
        }
    }

    fun applyModuleUpdate() = moduleRuntime.applyUpdate(::refresh)

    private fun canEdit(): Boolean = app.rules.hasLocalConfiguration().also {
        if (!it) error.value = app.getString(R.string.editing_requires_recovery)
    }

    private fun genericOpenSelected(): Set<ComponentRule> =
        app.rules.rules.value.filterTo(linkedSetOf()) { it.kind == IntentKind.OPEN }

    private fun genericDeepLinkSelected(): Set<ComponentRule> =
        app.rules.rules.value.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }

    private fun deepLinkHostPriorityBase(host: String): List<String> {
        val normalized = normalizeBrowserHost(host) ?: return emptyList()
        val explicit = app.rules.browserLinks.value.priorities[normalized].orEmpty()
        return if (explicit.isNotEmpty()) explicit
        else app.rules.priorities.value.apps[IntentKind.DEEP_LINK].orEmpty()
    }

    private fun openTypePriorityBase(preset: OpenPreset): List<String> {
        val explicit = app.rules.openTypes.value.priorities[preset].orEmpty()
        return if (explicit.isNotEmpty()) explicit
        else app.rules.priorities.value.apps[IntentKind.OPEN].orEmpty()
    }

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

    fun setGroupSelected(group: AppGroup, selected: Boolean) {
        if (canEdit()) app.rules.setSelected(group.components.map { it.rule }, selected)
    }

    fun toggleOpenType(preset: OpenPreset, rule: ComponentRule) {
        if (canEdit() && rule !in genericOpenSelected()) app.rules.toggleOpenType(preset, rule)
    }

    fun setOpenTypeGroupSelected(preset: OpenPreset, group: AppGroup, selected: Boolean) {
        if (!canEdit()) return
        val editable = group.components.map { it.rule }.filterNot { it in genericOpenSelected() }
        if (editable.isNotEmpty()) app.rules.setOpenTypeSelected(preset, editable, selected)
    }

    fun toggleBrowserHost(host: String, rule: ComponentRule) {
        if (!canEdit() || rule in genericDeepLinkSelected()) return
        val normalized = ensureBrowserHostConfigured(host) ?: return
        app.rules.toggleBrowserHost(normalized, rule)
    }

    fun setBrowserHostGroupSelected(host: String, group: AppGroup, selected: Boolean) {
        if (!canEdit()) return
        val editable = group.components.map { it.rule }.filterNot { it in genericDeepLinkSelected() }
        if (editable.isEmpty()) return
        val normalized = ensureBrowserHostConfigured(host) ?: return
        app.rules.setBrowserHostSelected(normalized, editable, selected)
    }

    fun selectBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericDeepLinkSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostSelected(normalized, editable, true)
        }
    }

    fun deselectBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericDeepLinkSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) {
            val normalized = normalizeBrowserHost(host) ?: return
            if (normalized !in app.rules.browserLinks.value.hosts) return
            app.rules.setBrowserHostSelected(normalized, editable, false)
        }
    }

    fun invertBrowserHostRules(host: String, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericDeepLinkSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.invertBrowserHostSelected(normalized, editable)
        }
    }

    fun selectOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericOpenSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) app.rules.setOpenTypeSelected(preset, editable, true)
    }

    fun deselectOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericOpenSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) app.rules.setOpenTypeSelected(preset, editable, false)
    }

    fun invertOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>, lockScope: String) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct()
            .filterNot { it in genericOpenSelected() }
            .filterNot { bulkLocks.isProtected(lockScope, it.packageName, it.id) }
        if (editable.isNotEmpty()) app.rules.invertOpenTypeSelected(preset, editable)
    }

    fun selectRules(
        rules: Collection<ComponentRule>,
        filter: IntentKind?,
        preset: OpenPreset?
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { isRuleBulkProtected(filter, preset, it) }
        if (editable.isNotEmpty()) app.rules.setSelected(editable, true)
    }

    fun deselectRules(
        rules: Collection<ComponentRule>,
        filter: IntentKind?,
        preset: OpenPreset?
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { isRuleBulkProtected(filter, preset, it) }
        if (editable.isNotEmpty()) app.rules.setSelected(editable, false)
    }

    fun invertRules(
        rules: Collection<ComponentRule>,
        filter: IntentKind?,
        preset: OpenPreset?
    ) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { isRuleBulkProtected(filter, preset, it) }
        if (editable.isNotEmpty()) app.rules.invertSelected(editable)
    }

    internal fun bulkLockState(scope: String, appId: String, itemIds: Collection<String>): BulkLockState =
        bulkLocks.state(scope, appId, itemIds)

    internal fun isBulkItemLocked(scope: String, itemId: String): Boolean =
        bulkLocks.isItemLocked(scope, itemId)

    internal fun isBulkAppLocked(scope: String, appId: String): Boolean =
        bulkLocks.isAppLocked(scope, appId)

    internal fun isBulkProtected(scope: String, appId: String, itemId: String): Boolean =
        bulkLocks.isProtected(scope, appId, itemId)

    internal fun setBulkAppLocked(
        scope: String,
        appId: String,
        itemIds: Collection<String>,
        locked: Boolean
    ) = bulkLocks.setAppLocked(scope, appId, itemIds, locked)

    internal fun setBulkItemLocked(scope: String, itemId: String, locked: Boolean) =
        bulkLocks.setItemLocked(scope, itemId, locked)

    private fun ruleScopes(filter: IntentKind?, preset: OpenPreset?, rule: ComponentRule): List<String> {
        if (filter != null) return listOf(ruleBulkLockScope(filter, preset))

        val scopes = linkedSetOf(
            ruleBulkLockScope(null, null),
            ruleBulkLockScope(rule.kind, null)
        )
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
            ruleScopes(filter, preset, rule).any { scope ->
                bulkLocks.isProtected(scope, appId, rule.id)
            }
        }
        return when {
            protectedCount == 0 -> BulkLockState.NONE
            protectedCount == distinct.size -> BulkLockState.FULL
            else -> BulkLockState.PARTIAL
        }
    }

    internal fun isRuleBulkProtected(
        filter: IntentKind?,
        preset: OpenPreset?,
        rule: ComponentRule
    ): Boolean = ruleScopes(filter, preset, rule).any { scope ->
        bulkLocks.isProtected(scope, rule.packageName, rule.id)
    }

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

    internal fun isRuleAppLockedForRule(
        filter: IntentKind?,
        preset: OpenPreset?,
        rule: ComponentRule
    ): Boolean = ruleScopes(filter, preset, rule).any { scope ->
        bulkLocks.isAppLocked(scope, rule.packageName)
    }

    internal fun setRuleAppLocked(
        filter: IntentKind?,
        preset: OpenPreset?,
        appId: String,
        rules: Collection<ComponentRule>,
        locked: Boolean
    ) {
        val distinct = rules.distinctBy { it.id }
        if (filter != null) {
            bulkLocks.setAppLocked(
                ruleBulkLockScope(filter, preset),
                appId,
                distinct.map { it.id },
                locked
            )
            return
        }
        // The All page is an aggregate view. Right swipe creates normal category-level locks.
        // Left swipe clears every exact scope represented by these rules, including typed Open
        // scopes, so a lock created on PDF/Image/etc. cannot be bypassed from All.
        val ids = distinct.map { it.id }
        if (!locked) {
            distinct.flatMap { ruleScopes(null, null, it) }.distinct().forEach { scope ->
                bulkLocks.setAppLocked(scope, appId, ids, false)
            }
            return
        }

        bulkLocks.setAppLocked(ruleBulkLockScope(null, null), appId, ids, false)
        distinct.groupBy { it.kind }.forEach { (kind, scopedRules) ->
            bulkLocks.setAppLocked(
                ruleBulkLockScope(kind, null),
                appId,
                scopedRules.map { it.id },
                true
            )
        }
    }

    internal fun setRuleItemLocked(
        filter: IntentKind?,
        preset: OpenPreset?,
        rule: ComponentRule,
        locked: Boolean
    ) {
        if (filter != null) {
            bulkLocks.setItemLocked(ruleBulkLockScope(filter, preset), rule.id, locked)
            return
        }
        // All aggregates every exact scope for this candidate. Unlock clears typed Open locks
        // too; locking from All still creates the normal category-level lock.
        if (!locked) {
            ruleScopes(null, null, rule).forEach { scope ->
                bulkLocks.setItemLocked(scope, rule.id, false)
            }
            return
        }
        bulkLocks.setItemLocked(ruleBulkLockScope(null, null), rule.id, false)
        bulkLocks.setItemLocked(ruleBulkLockScope(rule.kind, null), rule.id, true)
    }

    internal fun toggleBulkAppLock(scope: String, appId: String, itemIds: Collection<String>) =
        bulkLocks.toggleApp(scope, appId, itemIds)

    internal fun toggleBulkItemLock(scope: String, itemId: String) = bulkLocks.toggleItem(scope, itemId)

    fun setDisplayMode(value: DisplayMode) { if (canEdit()) app.rules.setDisplayMode(value) }
    fun setFilter(value: IntentKind?) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setUiFilter(value: UiFilter) { uiFilter.value = value }
    fun setDestination(value: Destination) { destination.value = value }
    fun toggleExpandedApp(key: String) { expandedAppKey.value = if (expandedAppKey.value == key) null else key }
    fun exportJson(): String = app.rules.exportJson()
    fun importJson(content: String) = app.rules.importJson(content)
    fun setComponentTitle(ruleId: String, title: String?) {
        if (canEdit()) app.rules.setComponentTitle(ruleId, title)
    }

    fun selectPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(200)
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun deselectPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val next = current.filterNot { it in editable }
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun invertPriorityApps(kind: IntentKind, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(200)
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun pinApp(kind: IntentKind, packageName: String) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        if (packageName !in current && current.size < 200) app.rules.setPriority(kind, current + packageName)
    }

    fun removePriority(kind: IntentKind, packageName: String) {
        if (canEdit()) app.rules.setPriority(kind, app.rules.priorities.value.apps[kind].orEmpty() - packageName)
    }

    fun movePriority(kind: IntentKind, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        app.rules.setPriority(
            kind,
            com.yagay.ListCleaner.domain.moveVisiblePriority(
                app.rules.priorities.value.apps[kind].orEmpty(), visible, packageName, offset
            )
        )
    }

    fun movePriorityTo(
        kind: IntentKind,
        packageName: String,
        target: String,
        visible: List<String>,
        expected: List<String>
    ) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        if (current != expected) return
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) app.rules.setPriority(kind, updated)
    }

    fun selectBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(200)
        if (next != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, next)
        }
    }

    fun deselectBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = deepLinkHostPriorityBase(host)
        val next = current.filterNot { it in editable }
        if (next != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, next)
        }
    }

    fun invertBrowserHostPriorityApps(host: String, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = deepLinkHostPriorityBase(host)
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(200)
        if (next != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, next)
        }
    }

    fun pinBrowserHostApp(host: String, packageName: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        if (packageName !in current && current.size < 200) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, current + packageName)
        }
    }

    fun removeBrowserHostPriority(host: String, packageName: String) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val next = current - packageName
        if (next != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, next)
        }
    }

    fun moveBrowserHostPriority(host: String, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriority(current, visible, packageName, offset)
        if (updated != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, updated)
        }
    }

    fun moveBrowserHostPriorityTo(
        host: String,
        packageName: String,
        target: String,
        visible: List<String>,
        expected: List<String>
    ) {
        if (!canEdit()) return
        val current = deepLinkHostPriorityBase(host)
        if (current != expected) return
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) {
            val normalized = ensureBrowserHostConfigured(host) ?: return
            app.rules.setBrowserHostPriority(normalized, updated)
        }
    }

    fun resetBrowserHostPriority(host: String) {
        if (!canEdit()) return
        val normalized = normalizeBrowserHost(host) ?: return
        if (normalized !in app.rules.browserLinks.value.hosts) return
        app.rules.setBrowserHostPriority(normalized, emptyList())
    }

    fun selectOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        val next = (current + editable.filter { it !in current }).take(200)
        if (next != current) app.rules.setOpenTypePriority(preset, next)
    }

    fun deselectOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val editable = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }.toSet()
        if (editable.isEmpty()) return
        val current = openTypePriorityBase(preset)
        val next = current.filterNot { it in editable }
        if (next != current) app.rules.setOpenTypePriority(preset, next)
    }

    fun invertOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>, lockScope: String) {
        if (!canEdit()) return
        val visible = packageNames.distinct().filterNot { bulkLocks.isAppLocked(lockScope, it) }
        if (visible.isEmpty()) return
        val current = openTypePriorityBase(preset)
        val next = (current.filterNot { it in visible.toSet() } + visible.filter { it !in current }).take(200)
        if (next != current) app.rules.setOpenTypePriority(preset, next)
    }

    fun pinOpenTypeApp(preset: OpenPreset, packageName: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        if (packageName !in current && current.size < 200) app.rules.setOpenTypePriority(preset, current + packageName)
    }

    fun removeOpenTypePriority(preset: OpenPreset, packageName: String) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val next = current - packageName
        if (next != current) app.rules.setOpenTypePriority(preset, next)
    }

    fun moveOpenTypePriority(preset: OpenPreset, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriority(current, visible, packageName, offset)
        if (updated != current) app.rules.setOpenTypePriority(preset, updated)
    }

    fun moveOpenTypePriorityTo(
        preset: OpenPreset,
        packageName: String,
        target: String,
        visible: List<String>,
        expected: List<String>
    ) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        if (current != expected) return
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) app.rules.setOpenTypePriority(preset, updated)
    }

    fun resetOpenTypePriority(preset: OpenPreset) {
        if (canEdit()) app.rules.setOpenTypePriority(preset, emptyList())
    }

    fun requestScope() = moduleRuntime.requestScope()

    companion object {
        const val MAX_BACKUP_CHARS = RuleRepository.MAX_BACKUP_CHARS
        private const val TAG = "ListCleaner.ViewModel"
    }
}

internal fun catalogVisible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter): Boolean =
    item.isCatalogCandidate || (selected && (uiFilter == UiFilter.SHOW_SELECTED || uiFilter == UiFilter.LOCKED))