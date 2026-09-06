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
import com.yagay.ListCleaner.BuildConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel) {

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "module-indicator") {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        stickyHeader(key = "list-controls") {
            Surface(tonalElevation = 2.dp) {
                ListControls(state, vm::setFilter, vm::setUiFilter)
            }
        }
        item(key = "list-summary") {
            SummaryRow(state)
            if (state.uiFilter != UiFilter.SHOW_SELECTED && state.candidates.any {
                    it.rule in state.selected && (it.unavailable || it.restricted)
                }) {
                TextButton(onClick = {
                    vm.setFilter(null)
                    vm.setQuery("")
                    vm.setUiFilter(UiFilter.SHOW_SELECTED)
                }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("查看未匹配的已选规则") }
            }
        }
        state.groups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(group, state.selected, expanded,
                    { vm.toggleExpandedApp(key) },
                    { selected -> vm.setGroupSelected(group, selected) })
            }
            if (expanded) {
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { component ->
                    ComponentRow(component, component.rule in state.selected) { vm.toggle(component.rule) }
                }
            }
        }
        if (!state.loading && state.groups.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("没有匹配的组件") } }
        }
    }
}

@Composable
private fun SummaryRow(state: MainState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("应用列表 · ${state.groups.size}", style = MaterialTheme.typography.labelLarge)
        Text("本页用于清理分享、多文件分享、打开方式、浏览器和文本处理等候选列表中的应用入口，不会停用或卸载应用。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when (state.displayMode) {
            DisplayMode.HIDE_SELECTED -> "当前为“隐藏选中”：规则生效后，勾选的组件从对应候选列表中隐藏；取消勾选恢复默认显示。"
            DisplayMode.SHOW_SELECTED -> "当前为“只显示选中”：规则生效后，对应分类保留勾选的组件，隐藏其他组件。"
            DisplayMode.SHOW_ALL -> "当前为“全部显示”：暂停清理系统候选列表，勾选只保存配置，恢复清理模式后生效。"
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("勾选应用可批量选择当前分类及搜索条件下显示的组件；展开可逐项选择。“查看”只筛选本页列表，不改变清理规则。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            Text(if (state.runtime.ready) "system 已确认暂停过滤，勾选及排序保留" else "本地已选择暂停，尚未确认系统已应用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    var pendingHookApps by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    if (showScopeDetails) ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    if (showAppScopePicker) AppScopePickerDialog(
        status = state.module,
        selected = pendingHookApps,
        onSelectedChange = { pendingHookApps = it },
        refresh = vm::refreshModuleStatus,
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
                Text("应用内列表 Hook", fontWeight = FontWeight.Bold)
                Text("某些文件管理器、分享面板会自己查询并绘制应用列表。可把这类应用加入 LSPosed 作用域，让 List Cleaner 在目标应用进程内处理候选。", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        vm.refreshModuleStatus()
                        showAppScopePicker = true
                    },
                    enabled = state.module.connected,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (pendingHookApps.isEmpty()) "选择应用加入 Hook" else "继续选择 / 加入 Hook（待提交 ${pendingHookApps.size}）") }
                Text("勾选后必须在选择页面底部点击“加入 LSPosed Hook 列表”才会真正申请作用域。返回不会清空未提交选择。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("运行版本由 LSPosed 提供；配置摘要由 system Hook 确认，不代表所有选择器行为均已验证。首次从 1.4.4 或更早版本迁移需重启；以后可尝试热更新，框架不支持或失败时仍需重启。", style = MaterialTheme.typography.bodySmall)

        Text("诊断工具", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("扫描只用于发现可管理组件，不保证等同于每次实际菜单。非文本分类保留空列表保护；文本允许隐藏全部候选，不处理应用硬编码菜单。", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onInspectFile, enabled = !checkingFile) {
                    Text(if (checkingFile) "正在检查文件…" else "用实际文件检查打开方式")
                }
                fileCheckStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断模式", Modifier.weight(1f))
                    Switch(state.diagnosticMode, vm::setDiagnosticMode)
                }
                Text("开启后记录查询分类、规则与跳过原因；按分类和调用UID记录首个有效查询栈。管理扫描不占用调用栈记录。每进程每5秒关键记录最多200条，候选明细另限40条，不记录正文和完整URI。",
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
                Text("先开启诊断，再复现问题，最后导出。包含最近24小时内最多12份 LSPosed 日志；大文件保留开头与最新结尾。系统原始日志可能包含隐私，分享前请检查。需要 Root 授权，读取失败会记录在包内。", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text("${androidx.compose.ui.res.stringResource(com.yagay.ListCleaner.R.string.app_name)} v${BuildConfig.VERSION_NAME}", modifier = Modifier.align(Alignment.CenterHorizontally),
             style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}