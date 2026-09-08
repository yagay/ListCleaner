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
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenSelectionSource
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
    SHOW_SELECTED
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

fun groupCandidates(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = candidates.groupBy { it.rule.packageName }.mapNotNull { (_, all) ->
    val matching = all.filter {
        val isSelected = it.rule in selected
        val matchesUiFilter = when (uiFilter) {
            UiFilter.ALL -> true
            UiFilter.HIDE_SELECTED -> !isSelected
            UiFilter.SHOW_SELECTED -> isSelected
        }
        catalogVisible(it, isSelected, uiFilter) && matchesUiFilter &&
            (filter == null || it.rule.kind == filter) && it.matchesQuery(query)
    }.sortedBy { it.rule.kind.ordinal }
    if (matching.isEmpty()) null else AppGroup(
        all.first().rule.packageName,
        all.first().appLabel,
        all.first().appIcon,
        matching
    )
}.sortedWith(
    compareBy<AppGroup> { appSelectionRank(it, selected) }
        .thenBy { it.appLabel.lowercase() }
        .thenBy { it.packageName }
)

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
    private val rootComponents = RootComponentsController(app, viewModelScope)
    private val moduleRuntime = ModuleRuntimeController(app, viewModelScope)

    val componentScan = rootComponents.scan
    val componentBusy = rootComponents.busy
    val componentMessage = rootComponents.message
    val componentRootNotice = rootComponents.rootNotice

    fun dismissComponentRootNotice() = rootComponents.dismissRootNotice()
    fun refreshComponents() = rootComponents.refresh()
    fun changeComponent(target: RootComponent, enable: Boolean) = rootComponents.change(target, enable)
    fun changeComponents(targets: List<RootComponent>, enable: Boolean) = rootComponents.change(targets, enable)
    fun invertComponents(targets: List<RootComponent>) = rootComponents.invert(targets)

    val updating: StateFlow<Boolean> = moduleRuntime.updating
    val updateMessage: StateFlow<String?> = moduleRuntime.updateMessage

    private val candidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
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
                check(app.synchronize()) { app.runtime.value.message }
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
                check(app.synchronize()) { app.runtime.value.message }
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

    private data class ListContent(
        val candidates: List<ComponentCandidate>,
        val filter: IntentKind?,
        val query: String,
        val groups: List<AppGroup>,
        val selected: Set<ComponentRule> = emptySet(),
        val uiFilter: UiFilter = UiFilter.ALL
    )

    private val grouped = combine(candidates, app.rules.rules, filter, query, uiFilter) {
        scanned, selected, kind, text, ui ->
        val items = retainConfiguredCandidates(
            scanned,
            selected,
            app.getString(R.string.candidate_configured_not_observed)
        )
        ListContent(items, kind, text, groupCandidates(items, selected, kind, text, ui), selected, ui)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListContent(emptyList(), null, "", emptyList())
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
        app.rules.openTypes
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val content = values[3] as ListContent
        val priorityConfig = values[6] as PriorityConfig
        val rawOpenTypes = values[12] as OpenTypeConfig
        MainState(
            module = values[0] as ModuleStatus,
            loading = values[1] as Boolean,
            error = values[2] as String?,
            candidates = content.candidates,
            selected = content.selected,
            runtime = values[4] as RuntimeStatus,
            displayMode = values[5] as DisplayMode,
            filter = content.filter,
            query = content.query,
            priorities = priorityConfig,
            groups = content.groups,
            diagnosticMode = values[7] as Boolean,
            syncStatus = values[8] as String,
            destination = values[9] as Destination,
            expandedAppKey = values[10] as String?,
            hiddenFromApps = values[11] as Set<String>,
            openTypes = effectiveOpenTypes(rawOpenTypes, content.selected, priorityConfig),
            openTypesExplicit = rawOpenTypes,
            uiFilter = content.uiFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    init {
        viewModelScope.launch {
            app.serviceSession.collectLatest { session ->
                moduleRuntime.readStatus(session)
                refresh()
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

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val configured = app.rules.rules.value +
                    app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                candidates.value = app.catalog.completeConfigured(candidates.value, configured)
                check(app.synchronize()) { app.runtime.value.message }
                val session = app.currentSession()
                val result = app.catalog.scan(app.rules.openTypes.value.customDefinitions)
                check(app.isCurrent(session)) { app.getString(R.string.runtime_connection_changed) }
                check(app.synchronize()) { app.runtime.value.message }
                if (generation == refreshGeneration && app.isCurrent(session)) {
                    val updatedConfigured = app.rules.rules.value +
                        app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
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

    private fun openTypePriorityBase(preset: OpenPreset): List<String> {
        val explicit = app.rules.openTypes.value.priorities[preset].orEmpty()
        return if (explicit.isNotEmpty()) explicit
        else app.rules.priorities.value.apps[IntentKind.OPEN].orEmpty()
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

    fun selectOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { it in genericOpenSelected() }
        if (editable.isNotEmpty()) app.rules.setOpenTypeSelected(preset, editable, true)
    }

    fun invertOpenTypeRules(preset: OpenPreset, rules: Collection<ComponentRule>) {
        if (!canEdit() || rules.isEmpty()) return
        val editable = rules.distinct().filterNot { it in genericOpenSelected() }
        if (editable.isNotEmpty()) app.rules.invertOpenTypeSelected(preset, editable)
    }

    fun selectRules(rules: Collection<ComponentRule>) {
        if (canEdit() && rules.isNotEmpty()) app.rules.setSelected(rules.distinct(), true)
    }

    fun invertRules(rules: Collection<ComponentRule>) {
        if (canEdit() && rules.isNotEmpty()) app.rules.invertSelected(rules)
    }

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

    fun selectPriorityApps(kind: IntentKind, packageNames: Collection<String>) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val next = (current + packageNames.distinct().filter { it !in current }).take(200)
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun invertPriorityApps(kind: IntentKind, packageNames: Collection<String>) {
        if (!canEdit()) return
        val visible = packageNames.distinct()
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

    fun selectOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>) {
        if (!canEdit()) return
        val current = openTypePriorityBase(preset)
        val next = (current + packageNames.distinct().filter { it !in current }).take(200)
        if (next != current) app.rules.setOpenTypePriority(preset, next)
    }

    fun invertOpenTypePriorityApps(preset: OpenPreset, packageNames: Collection<String>) {
        if (!canEdit()) return
        val visible = packageNames.distinct()
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
    item.isCatalogCandidate || (selected && uiFilter == UiFilter.SHOW_SELECTED)
