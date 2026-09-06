package com.yagay.ListCleaner.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.GridView
import com.yagay.ListCleaner.domain.TileConfig
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RootComponentCatalog
import com.yagay.ListCleaner.data.RootComponentScan
import com.yagay.ListCleaner.data.ComponentRootCommand
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.data.ResolverScopeDetector
import com.yagay.ListCleaner.data.ScopeDetection
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.PriorityConfig
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RunningTargetStatus(val processName: String, val state: String, val version: Long)

data class ModuleStatus(
    val connected: Boolean = false,
    val apiVersion: Int? = null,
    val grantedScope: Set<String> = emptySet(),
    val runningTargets: List<RunningTargetStatus> = emptyList(),
    val detection: ScopeDetection = ScopeDetection(),
    val scopeKnown: Boolean = false,
    val requesting: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val missingScope: Set<String> get() = detection.recommended - grantedScope
    val extraScope: Set<String> get() = grantedScope - detection.hosts.map { it.packageName }.toSet()
    val resolverLoaded: Boolean get() = runningTargets.any { target ->
        RuntimeProtocol.current(target.state, target.version, BuildConfig.VERSION_CODE.toLong()) && detection.hosts.any { it.processName == target.processName }
    }
    val outdated: Boolean get() = runningTargets.any { !RuntimeProtocol.current(it.state, it.version, BuildConfig.VERSION_CODE.toLong()) }
}

enum class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RULES("规则", Icons.Rounded.List),
    PRIORITY("排序", Icons.Rounded.Sort),
    TILES("组件", Icons.Rounded.GridView),
    DASHBOARD("状态", Icons.Rounded.Dashboard)
}

enum class UiFilter(val title: String) {
    ALL("全部"),
    HIDE_SELECTED("未选规则"),
    SHOW_SELECTED("已选规则")
}

data class MainState(
    val module: ModuleStatus = ModuleStatus(),
    val syncStatus: String = "已保存，等待连接",
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
    val tiles: TileConfig = TileConfig(),
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

fun groupCandidates(candidates: List<ComponentCandidate>, selected: Set<ComponentRule>, filter: IntentKind?, query: String, uiFilter: UiFilter): List<AppGroup> =
    candidates.groupBy { it.rule.packageName }.mapNotNull { (pkg, all) ->
        val matching = all.filter {
            val isSelected = it.rule in selected
            val matchesUiFilter = when (uiFilter) {
                UiFilter.ALL -> true
                UiFilter.HIDE_SELECTED -> !isSelected
                UiFilter.SHOW_SELECTED -> isSelected
            }
            catalogVisible(it, isSelected, uiFilter) && matchesUiFilter && (filter == null || it.rule.kind == filter) &&
                it.matchesQuery(query)
        }.sortedBy { it.rule.kind.ordinal }
        if (matching.isEmpty()) null else AppGroup(pkg, all.first().appLabel, all.first().appIcon,
            matching)
    }.sortedBy { it.appLabel.lowercase() }

/** A rule must remain manageable even when a probe, permission or package update hides its target. */
fun retainConfiguredCandidates(items: List<ComponentCandidate>, selected: Set<ComponentRule>): List<ComponentCandidate> {
    val kept = items.filter { !it.unavailable || it.rule in selected }
    val ids = kept.map { it.rule.id }.toSet()
    return kept + selected.filter { it.id !in ids }.map { rule ->
        ComponentCandidate(rule, rule.packageName, rule.className.substringAfterLast('.'),
            evidence = listOf("已配置，但本次未扫描到；不代表已卸载"), unavailable = true)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ListCleanerApp
    private val rootCatalog = RootComponentCatalog(app)
    private val mutableComponentScan = MutableStateFlow(RootComponentScan())
    val componentScan: StateFlow<RootComponentScan> = mutableComponentScan
    private val mutableComponentBusy = MutableStateFlow(false)
    val componentBusy: StateFlow<Boolean> = mutableComponentBusy
    private val mutableComponentMessage = MutableStateFlow<String?>(null)
    val componentMessage: StateFlow<String?> = mutableComponentMessage
    private val mutableComponentRootNotice = MutableStateFlow<String?>(null)
    val componentRootNotice: StateFlow<String?> = mutableComponentRootNotice

    fun dismissComponentRootNotice() { mutableComponentRootNotice.value = null }

    fun refreshComponents() {
        if (mutableComponentBusy.value) return
        mutableComponentBusy.value = true
        viewModelScope.launch {
            try {
                mutableComponentScan.value = withContext(Dispatchers.IO) { rootCatalog.scan() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableComponentMessage.value = failure.message ?: "扫描失败" }
            finally { mutableComponentBusy.value = false }
        }
    }

    fun changeComponent(target: RootComponent, enable: Boolean) = changeComponents(listOf(target), enable)

    fun changeComponents(visibleTargets: List<RootComponent>, enable: Boolean) {
        if (mutableComponentBusy.value) return
        // Snapshot only the visible, editable components. A component can match multiple kinds.
        val targets = visibleTargets.filter { it.blocked == null && it.enabled != null && it.enabled != enable }
            .distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableComponentRootNotice.value = null
        mutableComponentBusy.value = true
        mutableComponentMessage.value = "正在请求 Root 并核验系统状态…"
        viewModelScope.launch {
            // Finish observation even if navigation/lifecycle cancels the awaiting UI.
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    rootCatalog.requireRoot()
                    var result = ""
                    for (target in targets) {
                        operationStarted = true
                        mutableComponentMessage.value = "正在${if (enable) "启用" else "禁用"} ${completed + 1}/${targets.size}：${target.label}"
                        result = rootCatalog.change(target, enable)
                        completed++
                    }
                    mutableComponentMessage.value = if (targets.size == 1) result
                        else "已核验：${completed} 个组件已${if (enable) "启用" else "禁用"}；请重新打开目标选择器"
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    mutableComponentMessage.value = failure.message
                    mutableComponentRootNotice.value = failure.message
                } catch (failure: Exception) {
                    // Stop on failure: do not repeatedly prompt for Root or claim an atomic batch.
                    mutableComponentMessage.value = "已完成 $completed/${targets.size}，操作已停止：${failure.message ?: "操作失败"}。失败项请核对系统状态；剩余项未执行，已完成项不回滚。"
                } finally {
                    if (operationStarted) runCatching { rootCatalog.scan() }.onSuccess { mutableComponentScan.value = it }
                        .onFailure {
                            mutableComponentScan.value = RootComponentScan(warning = "操作后扫描失败，请刷新；不使用旧状态")
                        }
                    mutableComponentBusy.value = false
                }
            }
        }
    }

    fun invertComponents(visibleTargets: List<RootComponent>) {
        if (mutableComponentBusy.value) return
        val targets = visibleTargets.filter { it.blocked == null && it.enabled != null }
            .distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableComponentRootNotice.value = null
        mutableComponentBusy.value = true
        mutableComponentMessage.value = "正在请求 Root 并反选组件…"
        viewModelScope.launch {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    rootCatalog.requireRoot()
                    for (target in targets) {
                        operationStarted = true
                        val enable = target.enabled == false
                        mutableComponentMessage.value = "正在反选 ${completed + 1}/${targets.size}：${target.label}"
                        rootCatalog.change(target, enable)
                        completed++
                    }
                    mutableComponentMessage.value = "已核验：$completed 个组件已反选；请重新打开目标选择器"
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    mutableComponentMessage.value = failure.message
                    mutableComponentRootNotice.value = failure.message
                } catch (failure: Exception) {
                    mutableComponentMessage.value = "已完成 $completed/${targets.size}，反选已停止：${failure.message ?: "操作失败"}。已完成项不回滚。"
                } finally {
                    if (operationStarted) runCatching { rootCatalog.scan() }.onSuccess { mutableComponentScan.value = it }
                        .onFailure { mutableComponentScan.value = RootComponentScan(warning = "操作后扫描失败，请刷新；不使用旧状态") }
                    mutableComponentBusy.value = false
                }
            }
        }
    }

    private val mutableUpdating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = mutableUpdating
    private val mutableUpdateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = mutableUpdateMessage
    private val candidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    private val mutableFileCheckStatus = MutableStateFlow<String?>(null)
    val fileCheckStatus: StateFlow<String?> = mutableFileCheckStatus
    private val mutableCheckingFile = MutableStateFlow(false)
    val checkingFile: StateFlow<Boolean> = mutableCheckingFile

    fun inspectFile(uri: Uri) {
        if (mutableCheckingFile.value) return
        mutableCheckingFile.value = true
        viewModelScope.launch {
            mutableFileCheckStatus.value = "正在检查实际文件的候选，不会打开文件…"
            try {
                check(app.synchronize()) { app.runtime.value.message }
                val found = app.catalog.inspectFile(uri)
                check(app.synchronize()) { app.runtime.value.message }
                mutableFileCheckStatus.value = "查询返回 ${found.size} 个组件，其中 ${found.count { it.isCatalogCandidate }} 个符合目录管理条件。实际启动还取决于来源应用权限；明细见诊断包，不改变规则列表。"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableFileCheckStatus.value = failure.message ?: "文件检查失败"
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
    private val moduleStatus = MutableStateFlow(ModuleStatus())
    private val destination = MutableStateFlow(Destination.RULES)
    private val expandedAppKey = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var statusGeneration = 0L
    private var scopeRequestInFlight = false
    private val scopeDetector = ResolverScopeDetector(application)
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
                // Rescue path: never wait for the sync mutex, PM probes or framework IPC.
                // Export the last observed status with its age, then collect raw logs independently.
                val freshStatus = moduleStatus.value
                val config = app.rules.remoteSnapshot()
                report = DiagnosticCollector.collect(app, state.value.copy(
                    module = freshStatus, selected = config.rules, displayMode = config.mode,
                    priorities = config.priorities, diagnosticMode = config.diagnostic, tiles = config.tiles,
                    runtime = app.runtime.value,
                    syncStatus = app.syncStatus.value
                ), mutableComponentScan.value, rootCatalog.lastOperation)
                val ready = requireNotNull(report)
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("无法创建诊断包")
                    output.use { destination -> ready.inputStream().use { it.copyTo(destination) } }
                }
                mutableExportMessage.value = "诊断包已导出"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableExportMessage.value = "导出失败：${failure.message}；目标文件可能不完整，请重新导出"
            } finally {
                report?.delete()
                mutableCollectingDiagnostics.value = false
            }
        }
    }

    private data class ListContent(val candidates: List<ComponentCandidate>, val filter: IntentKind?, val query: String, val groups: List<AppGroup>,
        val selected: Set<ComponentRule> = emptySet(), val uiFilter: UiFilter = UiFilter.ALL)

    private val grouped = combine(candidates, app.rules.rules, filter, query, uiFilter) { scanned, selected, kind, text, ui ->
        val items = retainConfiguredCandidates(scanned, selected)
        ListContent(items, kind, text, groupCandidates(items, selected, kind, text, ui), selected, ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListContent(emptyList(), null, "", emptyList()))
    
    val state: StateFlow<MainState> = combine(
        moduleStatus, loading, error, grouped, app.runtime, app.rules.displayMode, app.rules.priorities, app.rules.diagnosticMode, app.syncStatus, destination, expandedAppKey, app.rules.tiles, app.rules.hiddenFromApps
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MainState(
            module = values[0] as ModuleStatus,
            loading = values[1] as Boolean,
            error = values[2] as String?,
            candidates = (values[3] as ListContent).candidates,
            selected = (values[3] as ListContent).selected,
            runtime = values[4] as RuntimeStatus,
            displayMode = values[5] as DisplayMode,
            filter = (values[3] as ListContent).filter,
            query = (values[3] as ListContent).query,
            priorities = values[6] as PriorityConfig,
            groups = (values[3] as ListContent).groups,
            diagnosticMode = values[7] as Boolean,
            syncStatus = values[8] as String,
            destination = values[9] as Destination,
            expandedAppKey = values[10] as String?,
            tiles = values[11] as TileConfig,
            hiddenFromApps = values[12] as Set<String>,
            uiFilter = (values[3] as ListContent).uiFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    init {
        viewModelScope.launch {
            app.service.collectLatest {
                readModuleStatus(it)
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

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                candidates.value = app.catalog.completeConfigured(candidates.value, app.rules.rules.value)
                check(app.synchronize()) { app.runtime.value.message }
                val result = app.catalog.scan()
                check(app.synchronize()) { app.runtime.value.message }
                if (generation == refreshGeneration) {
                    // Replace the directory, do not accumulate historical scan results.
                    candidates.value = app.catalog.completeConfigured(result, app.rules.rules.value)
                    error.value = app.catalog.scanWarning
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == refreshGeneration) error.value = failure.message ?: "扫描失败"
            } finally {
                if (generation == refreshGeneration) loading.value = false
            }
        }
        refreshModuleStatus()
    }

    fun refreshModuleStatus() {
        viewModelScope.launch {
            readModuleStatus(app.service.value)
            app.synchronize()
        }
    }

    fun resolveRecovery(restore: Boolean) {
        viewModelScope.launch {
            app.resolveRecovery(restore)
            refresh()
        }
    }

    fun applyModuleUpdate() {
        if (mutableUpdating.value) return
        mutableUpdating.value = true
        mutableUpdateMessage.value = "正在检测运行目标…"
        viewModelScope.launch {
            try {
                val bound = app.service.value ?: error("未连接 LSPosed")
                val targets = withContext(Dispatchers.IO) { bound.runningTargets }
                val pending = targets.filter { !RuntimeProtocol.current(it.state.name, it.loadedVersionCode, BuildConfig.VERSION_CODE.toLong()) }
                val messages = mutableListOf<String>()
                for (target in pending) {
                    try {
                    if (target.state == HookedTarget.State.RELOADING) {
                        messages += "${target.processName}：框架正在重载，请稍后重新检测"
                        continue
                    }
                    if (target.loadedVersionCode < 19) {
                        messages += "${target.processName}：旧版本 ${target.loadedVersionCode} 不支持本模块热重载，请完整重启手机"
                        continue
                    }
                    val result = withTimeoutOrNull(15_000) {
                        withContext(Dispatchers.IO) {
                            suspendCancellableCoroutine<HotReloadResult> { continuation ->
                                bound.hotReloadModule(target, null) { _, reply ->
                                    if (continuation.isActive) continuation.resume(reply)
                                }
                            }
                        }
                    }
                    messages += "${target.processName}：" + when (result?.status()) {
                        HotReloadResult.Status.SUCCEEDED -> "框架报告更新成功，正在重新核实"
                        HotReloadResult.Status.UNSUPPORTED -> "框架不支持，请重启手机"
                        HotReloadResult.Status.FAILED -> "更新失败：${result?.message() ?: "旧模块拒绝"}；请重启手机"
                        HotReloadResult.Status.PROCESS_DIED -> "目标已退出，等待重新启动"
                        HotReloadResult.Status.IN_PROGRESS -> "框架正在重载，请稍后重新检测"
                        null -> "等待超时，不代表已取消；请重新检测，勿重复请求"
                    }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        messages += "${target.processName}：更新请求失败（${failure.javaClass.simpleName}）；继续检查其他目标"
                    }
                }
                mutableUpdateMessage.value = if (messages.isEmpty()) "没有需要热更新的运行目标；正在核实配置" else messages.joinToString("\n")
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableUpdateMessage.value = "更新检查失败：${failure.message}；未重启任何系统进程"
            } finally { mutableUpdating.value = false }
        }
    }

    private suspend fun readModuleStatus(service: XposedService?): ModuleStatus {
        val generation = ++statusGeneration
        val status = withContext(Dispatchers.IO) {
            val detection = scopeDetector.detect()
            var result = ModuleStatus(connected = service != null, detection = detection)
            if (service != null) {
                try {
                    result = result.copy(apiVersion = service.apiVersion)
                    result = result.copy(grantedScope = service.scope.toSet(), scopeKnown = true)
                    result = if ((result.apiVersion ?: 0) >= 102) {
                        result.copy(runningTargets = service.runningTargets.map {
                            RunningTargetStatus(it.processName, it.state.name, it.loadedVersionCode)
                        })
                    } else result.copy(error = "当前框架不支持运行目标检测")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    result = result.copy(error = failure.message ?: "无法读取 LSPosed 模块状态")
                }
            }
            result
        }
        if (generation == statusGeneration && app.service.value === service) {
            moduleStatus.value = status.copy(requesting = scopeRequestInFlight)
        }
        return status
    }

    private fun canEdit(): Boolean = app.rules.hasLocalConfiguration().also {
        if (!it) error.value = "请先完成远程配置恢复或重置，避免覆盖原有规则"
    }
    fun toggle(rule: ComponentRule) { if (canEdit()) app.rules.toggle(rule) }
    fun setGroupSelected(group: AppGroup, selected: Boolean) { if (canEdit()) app.rules.setSelected(group.components.map { it.rule }, selected) }
    fun selectRules(rules: Collection<ComponentRule>) {
        if (!canEdit() || rules.isEmpty()) return
        app.rules.setSelected(rules.distinct(), true)
    }
    fun invertRules(rules: Collection<ComponentRule>) {
        if (!canEdit() || rules.isEmpty()) return
        val current = app.rules.rules.value
        val unique = rules.distinct()
        val select = unique.filter { it !in current }
        val unselect = unique.filter { it in current }
        if (select.isNotEmpty()) app.rules.setSelected(select, true)
        if (unselect.isNotEmpty()) app.rules.setSelected(unselect, false)
    }
    fun setDisplayMode(value: DisplayMode) { if (canEdit()) app.rules.setDisplayMode(value) }
    fun setFilter(value: IntentKind?) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setUiFilter(value: UiFilter) { uiFilter.value = value }
    fun setDestination(value: Destination) { destination.value = value }
    fun toggleExpandedApp(key: String) { expandedAppKey.value = if (expandedAppKey.value == key) null else key }
    fun exportJson(): String = app.rules.exportJson()
    fun importJson(content: String) = app.rules.importJson(content)

    fun selectPriorityApps(kind: IntentKind, packageNames: Collection<String>) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val additions = packageNames.distinct().filter { it !in current }
        val next = (current + additions).take(200)
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun invertPriorityApps(kind: IntentKind, packageNames: Collection<String>) {
        if (!canEdit()) return
        val visible = packageNames.distinct()
        if (visible.isEmpty()) return
        val visibleSet = visible.toSet()
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        val retained = current.filterNot { it in visibleSet }
        val added = visible.filter { it !in current }
        val next = (retained + added).take(200)
        if (next != current) app.rules.setPriority(kind, next)
    }

    fun pinApp(kind: IntentKind, packageName: String) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        if (packageName !in current && current.size < 200) app.rules.setPriority(kind, current + packageName)
    }

    fun removePriority(kind: IntentKind, packageName: String) {
        if (!canEdit()) return
        app.rules.setPriority(kind, app.rules.priorities.value.apps[kind].orEmpty() - packageName)
    }

    fun movePriority(kind: IntentKind, packageName: String, offset: Int, visible: List<String>) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        app.rules.setPriority(kind, com.yagay.ListCleaner.domain.moveVisiblePriority(current, visible, packageName, offset))
    }


    fun movePriorityTo(kind: IntentKind, packageName: String, target: String, visible: List<String>, expected: List<String>) {
        if (!canEdit()) return
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        // A drag is a snapshot. Never overwrite a reset/import/edit that occurred during it.
        if (current != expected) return
        val updated = com.yagay.ListCleaner.domain.moveVisiblePriorityTo(current, visible, packageName, target)
        if (updated != current) app.rules.setPriority(kind, updated)
    }

    fun requestScope() {
        if (scopeRequestInFlight) return
        scopeRequestInFlight = true
        moduleStatus.value = moduleStatus.value.copy(requesting = true, message = null)
        viewModelScope.launch {
            try {
                val service = app.service.value ?: error("未连接 LSPosed，请先启用模块")
                val current = readModuleStatus(service)
                check(app.service.value === service) { "服务连接已变化，请重新检查" }
                check(current.scopeKnown) { current.error ?: "无法读取已授权作用域，未提交申请" }
                check(current.detection.recommended.isNotEmpty()) { "未确认可自动申请的选择器宿主，请查看检测详情并在 LSPosed 中手动核查" }
                val missing = current.missingScope.toList()
                if (missing.isEmpty()) {
                    moduleStatus.value = current.copy(requesting = true, message = "检测到的推荐作用域均已授权，无需重复申请")
                    return@launch
                }
                val approved = withTimeoutOrNull(120_000) {
                    suspendCancellableCoroutine<List<String>> { continuation ->
                        service.requestScope(missing, object : XposedService.OnScopeEventListener {
                            override fun onScopeRequestApproved(approved: List<String>) {
                                if (continuation.isActive) continuation.resume(approved)
                            }
                            override fun onScopeRequestFailed(message: String) {
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                            }
                        })
                    }
                }
                check(app.service.value === service) { "服务连接已变化，请重新检查授权结果" }
                val refreshed = readModuleStatus(service)
                check(app.service.value === service) { "服务连接已变化，请重新检查授权结果" }
                val message = when {
                    approved == null -> "等待授权超时，框架申请可能仍在处理；请先在 LSPosed 查看结果"
                    !refreshed.scopeKnown -> "框架已返回，暂时无法核实授权结果，请重新检查"
                    refreshed.missingScope.isNotEmpty() -> "仍缺少作用域：${refreshed.missingScope.joinToString()}"
                    else -> "推荐作用域已确认授权；若目标尚未加载，请重新启动相关选择器，必要时重启设备"
                }
                moduleStatus.value = refreshed.copy(message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                moduleStatus.value = moduleStatus.value.copy(error = failure.message ?: "申请作用域失败")
            } finally {
                scopeRequestInFlight = false
                moduleStatus.value = moduleStatus.value.copy(requesting = false)
            }
        }
    }

    companion object {
        const val MAX_BACKUP_CHARS = RuleRepository.MAX_BACKUP_CHARS
    }
}

internal fun catalogVisible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter): Boolean =
    item.isCatalogCandidate || (selected && uiFilter == UiFilter.SHOW_SELECTED)
