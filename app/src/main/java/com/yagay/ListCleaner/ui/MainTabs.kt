package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.BuildConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel) {
    var editingTitle by remember { mutableStateOf<com.yagay.ListCleaner.domain.ComponentCandidate?>(null) }
    editingTitle?.let { item ->
        ComponentTitleDialog(item, state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null })
    }
    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }
    LaunchedEffect(state.filter) { if (state.filter != IntentKind.OPEN) openPreset = null }
    val typedSelected = openPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitTypedSelected = openPreset?.let { state.openTypesExplicit.selectedRules(it) }.orEmpty()
    val shownGroups = if (state.filter == IntentKind.OPEN && openPreset != null) {
        groupCandidates(state.candidates.filter { it.matchesOpenPreset(openPreset!!) }, typedSelected,
            IntentKind.OPEN, state.query, state.uiFilter)
    } else state.groups
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
                    ListControls(state, vm::setFilter, vm::setUiFilter,
                        onSelectAll = { if (openPreset != null && state.filter == IntentKind.OPEN) vm.selectOpenTypeRules(openPreset!!, visibleRules) else vm.selectRules(visibleRules) },
                        onInvert = { if (openPreset != null && state.filter == IntentKind.OPEN) vm.invertOpenTypeRules(openPreset!!, visibleRules) else vm.invertRules(visibleRules) })
                    if (state.filter == IntentKind.OPEN) OpenPresetFilterRow(openPreset, { openPreset = it })
                }
            }
        }
        item(key = "list-summary") {
            SummaryRow(state, shownGroups.size, openPreset)
            if (openPreset == null && state.uiFilter != UiFilter.SHOW_SELECTED && state.candidates.any {
                    it.rule in state.selected && (it.unavailable || it.restricted)
                }) {
                TextButton(onClick = {
                    vm.setFilter(null)
                    vm.setQuery("")
                    vm.setUiFilter(UiFilter.SHOW_SELECTED)
                }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("查看未匹配的已选规则") }
            }
        }
        shownGroups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${openPreset?.name ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(group, activeSelected, expanded,
                    { vm.toggleExpandedApp(key) },
                    { selected -> if (openPreset != null && state.filter == IntentKind.OPEN) vm.setOpenTypeGroupSelected(openPreset!!, group, selected) else vm.setGroupSelected(group, selected) })
            }
            if (expanded) {
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { component ->
                    val sourceNote = if (openPreset != null && state.filter == IntentKind.OPEN && component.rule in activeSelected) {
                        when {
                            component.rule in state.selected -> "继承自“打开方式 · 全部”"
                            component.rule in explicitTypedSelected -> "${openPreset!!.title} 专用规则"
                            else -> null
                        }
                    } else null
                    ComponentRow(component, component.rule in activeSelected, state.priorities.titles[component.rule.id],
                        selectionNote = sourceNote,
                        onToggle = { if (openPreset != null && state.filter == IntentKind.OPEN) vm.toggleOpenType(openPreset!!, component.rule) else vm.toggle(component.rule) },
                        onEditTitle = { editingTitle = component })
                }
            }
        }
        if (!state.loading && shownGroups.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("没有匹配的组件") } }
        }
    }
}

@Composable
private fun SummaryRow(state: MainState, groupCount: Int = state.groups.size, openPreset: OpenPreset? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("应用列表 · $groupCount" + (openPreset?.let { " · ${it.title}" } ?: ""), style = MaterialTheme.typography.labelLarge)
        Text("本页用于管理分享、多文件分享、打开方式、浏览器和文本处理等 Intent 候选入口：可按规则隐藏/保留组件，也可单独修改组件在系统候选菜单中的显示名称；不会修改实际 Intent、包名、组件名，也不会停用或卸载应用。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when (state.displayMode) {
            DisplayMode.HIDE_SELECTED -> "当前为“隐藏选中”：规则生效后，勾选的组件从对应候选列表中隐藏；取消勾选恢复默认显示。"
            DisplayMode.SHOW_SELECTED -> "当前为“只显示选中”：规则生效后，对应分类保留勾选的组件，隐藏其他组件。"
            DisplayMode.SHOW_ALL -> "当前为“全部显示”：暂停清理系统候选列表，勾选只保存配置，恢复清理模式后生效。"
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (openPreset == null) "勾选应用可批量选择当前分类及搜索条件下显示的组件；展开后可逐项选择，点铅笔可设置该组件在当前分类中的菜单显示名称。改名与是否勾选规则相互独立；留空保存恢复原名称。“查看”只筛选本页列表，不改变清理规则。" else "当前正在编辑“打开方式 · ${openPreset.title}”专用规则。有效勾选由“全部”中的 OPEN 通用规则与当前类型专用规则共同组成；继承自“全部”的项目会继续显示已勾选，并在展开项中标明来源。继承项不能在分类型里直接取消，需要回到“全部”取消；当前类型新增的规则只影响该 MIME / scheme 类型。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            Text(if (state.runtime.ready) "system 已确认暂停过滤、排序和自定义显示名称；相关配置仍保留" else "本地已选择暂停，尚未确认系统已应用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else if (state.displayMode == DisplayMode.SHOW_SELECTED) {
            Text("未设置勾选的分类暂时显示全部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let { Text("本次刷新未完整完成，详情见状态页", color = MaterialTheme.colorScheme.error) }
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
    if (showAppScopePicker) AppScopePickerDialog(
        selected = state.hiddenFromApps,
        onSelectedChange = vm::setHiddenFromApps,
    ) { showAppScopePicker = false }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RuntimePanel(state, vm)
        Text("全局清理模式", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.displayMode.title, fontWeight = FontWeight.Bold)
                    Text("控制系统选择器如何处理已选组件。", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text("切换")
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.title) },
                                leadingIcon = { if (mode == state.displayMode) Icon(Icons.Rounded.Check, null) },
                                onClick = { menu = false; vm.setDisplayMode(mode) })
                        }
                    }
                }
            }
        }

        Text("同步状态", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(state.syncStatus, fontWeight = FontWeight.Bold)
                Text("本地保存不等于系统生效；以配置确认状态为准，Resolver 侧效果仍需实际验证。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("运行能力与实际命中", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state.runtime.ready) "system_server 已确认当前配置" else "尚未取得当前配置 ACK", fontWeight = FontWeight.Bold)
                Text("Intent 查询 Hook：${state.runtime.queryHits} 次", style = MaterialTheme.typography.bodySmall)
                Text("应用可见性兼容过滤：${state.runtime.visibilityHits} 次", style = MaterialTheme.typography.bodySmall)
                Text("system 进程记录到的排序改写：${state.runtime.orderingHits} 次", style = MaterialTheme.typography.bodySmall)
                Text("查询和应用可见性计数来自 system_server 的真实执行。排序通常在独立 Resolver/Chooser 进程执行，因此这里的排序计数为 0 不能单独判定排序失效；诊断模式会记录 Resolver 侧 ORDER_DELIVERED，实际菜单仍以设备复现为准。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = vm::refreshModuleStatus, modifier = Modifier.fillMaxWidth()) { Text("刷新运行状态") }
            }
        }

        Text("数据备份", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("从 JSON 恢复") }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出为 JSON") }
        }

        Text("模块状态", style = MaterialTheme.typography.titleMedium)
        ModuleStatusRow(state) {
            showScopeDetails = true
            vm.refreshModuleStatus()
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("应用隐藏列表", fontWeight = FontWeight.Bold)
                Text("用于普通 Resolver 规则对某些文件管理器不起作用时的兼容方案，例如 ES 自己生成的打开方式列表。启用后，从指定来源应用可见的已安装应用列表中隐藏“打开方式”规则目标；只跟随 OPEN 通用规则和 PDF、APK、图片、视频等分类型 OPEN 规则，不会再因为分享或文本处理规则而隐藏目标包。", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { showAppScopePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("管理应用隐藏列表（${state.hiddenFromApps.size}）") }
                Text("勾选立即保存；仅在“隐藏选中”模式生效。实现位于 android/system_server，属于包级隐藏：命中后该目标包会整体对来源应用不可见，无法只隐藏其中一个 Activity。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("诊断工具", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("扫描只用于发现可管理组件，不保证等同于每次实际菜单。非文本分类保留空列表保护；文本允许隐藏全部候选，不处理应用硬编码菜单。", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onInspectFile, enabled = !checkingFile, modifier = Modifier.fillMaxWidth()) {
                    Text(if (checkingFile) "正在检查文件…" else "用实际文件预览最终打开方式")
                }
                fileCheckStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断模式", Modifier.weight(1f))
                    Switch(state.diagnosticMode, vm::setDiagnosticMode)
                }
                Text("开启后记录查询分类、规则与跳过原因；按分类和调用UID记录首个有效查询栈。管理扫描不占用调用栈记录。运行时只记录文件扩展名和识别类型，不记录完整文件名或 URI。每进程每5秒关键记录最多200条，候选明细另限40条。",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.diagnosticMode) Text("诊断已开启；请复现分享、打开或文本处理操作。", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onCollectDiagnostics,
                    enabled = !collectingDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (collectingDiagnostics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (collectingDiagnostics) "正在收集" else "一键导出诊断包")
                }
                Text("先开启诊断，再复现问题，最后导出。包含最近24小时内最多12份 LSPosed 日志；大文件保留开头与最新结尾。系统原始日志仍可能包含其他进程隐私，分享前请检查。需要 Root 授权，读取失败会记录在包内。", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("${androidx.compose.ui.res.stringResource(com.yagay.ListCleaner.R.string.app_name)} v${BuildConfig.VERSION_NAME}", modifier = Modifier.align(Alignment.CenterHorizontally),
             style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
