from pathlib import Path

# 1) ModuleConfig: persist caller apps that should receive system-level package hiding.
p = Path('app/src/main/java/com/yagay/ListCleaner/domain/ModuleConfig.kt')
s = p.read_text()
s = s.replace('''    val managerAppId: Int = -1,\n    val tiles: TileConfig = TileConfig()\n''','''    val managerAppId: Int = -1,\n    val tiles: TileConfig = TileConfig(),\n    // Apps in this list are callers from which selected target packages will be hidden at system_server.\n    // They do NOT need to be added to the LSPosed module scope.\n    val hiddenFromApps: Set<String> = emptySet()\n''')
s = s.replace('''        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))\n        return this\n''','''        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))\n        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(::validPackageName))\n        return this\n''')
s = s.replace('''    fun validated(): ModuleConfig {\n''','''    fun validated(): ModuleConfig {\n''')
s = s.replace('''        return this\n    }\n}\n''','''        return this\n    }\n\n    private fun validPackageName(value: String): Boolean =\n        value.isNotBlank() && value.length <= 255 && value.none { it.isWhitespace() || it.isISOControl() || it == '|' }\n}\n''')
p.write_text(s)

# 2) Backup schema: include hidden-from app list, bump export format to v5.
p = Path('app/src/main/java/com/yagay/ListCleaner/domain/Rule.kt')
s = p.read_text()
s = s.replace('''    val displayMode: DisplayMode? = null,\n    val tiles: TileConfig = TileConfig()\n)''','''    val displayMode: DisplayMode? = null,\n    val tiles: TileConfig = TileConfig(),\n    val hiddenFromApps: Set<String> = emptySet()\n)''')
p.write_text(s)

# 3) Repository: local flow + remote config + JSON backup.
p = Path('app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt')
s = p.read_text()
s = s.replace('''    private val mutableTiles = MutableStateFlow(runCatching {\n        json.decodeFromString(TileConfig.serializer(), prefs.getString(KEY_TILES, null) ?: "{}").validated()\n    }.getOrDefault(TileConfig()))\n    val tiles: StateFlow<TileConfig> = mutableTiles.asStateFlow()\n''','''    private val mutableTiles = MutableStateFlow(runCatching {\n        json.decodeFromString(TileConfig.serializer(), prefs.getString(KEY_TILES, null) ?: "{}").validated()\n    }.getOrDefault(TileConfig()))\n    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())\n    val tiles: StateFlow<TileConfig> = mutableTiles.asStateFlow()\n    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()\n''')
s = s.replace('''        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) || prefs.contains(KEY_TILES)\n''','''        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) || prefs.contains(KEY_TILES) || prefs.contains(KEY_HIDDEN_FROM_APPS)\n''')
s = s.replace('''        replace(config.rules, config.mode != DisplayMode.SHOW_SELECTED, config.priorities, config.mode, config.tiles)\n        setDiagnosticMode(config.diagnostic)\n''','''        replace(config.rules, config.mode != DisplayMode.SHOW_SELECTED, config.priorities, config.mode, config.tiles)\n        setHiddenFromApps(config.hiddenFromApps)\n        setDiagnosticMode(config.diagnostic)\n''')
s = s.replace('''        mutableDiagnostic.value, android.os.Process.myUid() % 100_000, mutableTiles.value\n    )\n''','''        mutableDiagnostic.value, android.os.Process.myUid() % 100_000, mutableTiles.value, mutableHiddenFromApps.value.toSet()\n    )\n''')
insert = '''\n    @Synchronized fun setHiddenFromApps(packages: Set<String>) {\n        val self = "com.yagay.ListCleaner"\n        val valid = packages.asSequence()\n            .map(String::trim)\n            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }\n            .take(2_001).toSet()\n        require(valid.size <= 2_000) { "隐藏应用列表数量过多" }\n        mutableHiddenFromApps.value = valid\n        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()\n        mutableRevision.value++\n    }\n\n'''
s = s.replace('''    @Synchronized\n    fun setDiagnosticMode(enabled: Boolean) {\n''', insert + '''    @Synchronized\n    fun setDiagnosticMode(enabled: Boolean) {\n''')
s = s.replace('''        RuleBackup(version = 4, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value, priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value)\n''','''        RuleBackup(version = 5, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value, priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value, hiddenFromApps = mutableHiddenFromApps.value)\n''')
s = s.replace('''        require(backup.version in 1..4) { "不支持的备份版本：${backup.version}" }\n        replace(backup.rules, backup.blacklist, if (backup.version == 1) PriorityConfig() else backup.priorities, if (backup.version >= 3) requireNotNull(backup.displayMode) { "备份缺少显示模式" } else DisplayMode.fromStored(null, backup.blacklist), if (backup.version >= 4) backup.tiles else TileConfig())\n''','''        require(backup.version in 1..5) { "不支持的备份版本：${backup.version}" }\n        replace(backup.rules, backup.blacklist, if (backup.version == 1) PriorityConfig() else backup.priorities, if (backup.version >= 3) requireNotNull(backup.displayMode) { "备份缺少显示模式" } else DisplayMode.fromStored(null, backup.blacklist), if (backup.version >= 4) backup.tiles else TileConfig())\n        setHiddenFromApps(if (backup.version >= 5) backup.hiddenFromApps else emptySet())\n''')
s = s.replace('''        const val KEY_TILES = "tile_config"\n        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC)\n''','''        const val KEY_TILES = "tile_config"\n        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"\n        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC, KEY_HIDDEN_FROM_APPS)\n''')
p.write_text(s)

# 4) Main state and ViewModel accessor.
p = Path('app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt')
s = p.read_text()
s = s.replace('''    val tiles: TileConfig = TileConfig(),\n    val groups: List<AppGroup> = emptyList(),\n''','''    val tiles: TileConfig = TileConfig(),\n    val hiddenFromApps: Set<String> = emptySet(),\n    val groups: List<AppGroup> = emptyList(),\n''')
s = s.replace('''        moduleStatus, loading, error, grouped, app.runtime, app.rules.displayMode, app.rules.priorities, app.rules.diagnosticMode, app.syncStatus, destination, expandedAppKey, app.rules.tiles\n''','''        moduleStatus, loading, error, grouped, app.runtime, app.rules.displayMode, app.rules.priorities, app.rules.diagnosticMode, app.syncStatus, destination, expandedAppKey, app.rules.tiles, app.rules.hiddenFromApps\n''')
s = s.replace('''            tiles = values[11] as TileConfig,\n            uiFilter = (values[3] as ListContent).uiFilter\n''','''            tiles = values[11] as TileConfig,\n            hiddenFromApps = values[12] as Set<String>,\n            uiFilter = (values[3] as ListContent).uiFilter\n''')
# Add simple mutation API next to diagnostic setting.
s = s.replace('''    fun setDiagnosticMode(enabled: Boolean) {\n        app.rules.setDiagnosticMode(enabled)\n        refreshModuleStatus()\n    }\n''','''    fun setDiagnosticMode(enabled: Boolean) {\n        app.rules.setDiagnosticMode(enabled)\n        refreshModuleStatus()\n    }\n\n    fun setHiddenFromApps(packages: Set<String>) {\n        app.rules.setHiddenFromApps(packages)\n        viewModelScope.launch { app.synchronize() }\n    }\n''')
p.write_text(s)

# 5) Replace old LSPosed scope picker with a pure hidden-from caller list picker.
p = Path('app/src/main/java/com/yagay/ListCleaner/ui/AppScopePicker.kt')
s = p.read_text()
start = s.index('@OptIn(ExperimentalMaterial3Api::class)')
prefix = '''package com.yagay.ListCleaner.ui\n\nimport android.content.pm.ApplicationInfo\nimport android.content.pm.PackageManager\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.automirrored.rounded.ArrowBack\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.*\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n\ndata class ScopeAppEntry(\n    val packageName: String,\n    val label: String,\n    val system: Boolean,\n)\n\n@Suppress("DEPRECATION")\nprivate fun loadScopeApps(pm: PackageManager, selfPackage: String): List<ScopeAppEntry> {\n    val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {\n        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))\n    } else {\n        pm.getInstalledApplications(0)\n    }\n    return installed.asSequence()\n        .filter { it.packageName != selfPackage && it.packageName != "android" }\n        .map { info -> ScopeAppEntry(\n            packageName = info.packageName,\n            label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),\n            system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,\n        ) }\n        .distinctBy { it.packageName }\n        .sortedWith(compareBy<ScopeAppEntry> { it.label.lowercase() }.thenBy { it.packageName })\n        .toList()\n}\n\n'''
body = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScopePickerDialog(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.label.lowercase().contains(needle) || entry.packageName.lowercase().contains(needle))
        }
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("应用隐藏列表") },
                    navigationIcon = { IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Text(
                    "这里选择的是“从哪些应用中隐藏规则目标”。这些应用不需要加入 LSPosed Hook 作用域；List Cleaner 只在 system/system_server 侧应用隐藏。勾选立即保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示系统应用", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Text(
                    "已加入隐藏列表 ${selected.size} 个应用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { entry ->
                            val checked = entry.packageName in selected
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onSelectedChange(if (checked) selected - entry.packageName else selected + entry.packageName)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { value ->
                                        onSelectedChange(if (value) selected + entry.packageName else selected - entry.packageName)
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, fontWeight = FontWeight.Medium)
                                    Text(entry.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (checked) Text("已加入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
'''
p.write_text(prefix + body)

# 6) Dashboard: remove pending scope/request semantics; make hidden list independent of LSPosed connection.
p = Path('app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt')
s = p.read_text()
s = s.replace('import androidx.compose.runtime.saveable.rememberSaveable\n', '')
s = s.replace('''    var showAppScopePicker by remember { mutableStateOf(false) }\n    var pendingHookApps by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }\n''','''    var showAppScopePicker by remember { mutableStateOf(false) }\n''')
s = s.replace('''    if (showAppScopePicker) AppScopePickerDialog(\n        status = state.module,\n        selected = pendingHookApps,\n        onSelectedChange = { pendingHookApps = it },\n        refresh = vm::refreshModuleStatus,\n    ) { showAppScopePicker = false }\n''','''    if (showAppScopePicker) AppScopePickerDialog(\n        selected = state.hiddenFromApps,\n        onSelectedChange = vm::setHiddenFromApps,\n    ) { showAppScopePicker = false }\n''')
s = s.replace('''                Text("应用内列表 Hook", fontWeight = FontWeight.Bold)\n                Text("某些文件管理器、分享面板会自己查询并绘制应用列表。可把这类应用加入 LSPosed 作用域，让 List Cleaner 在目标应用进程内处理候选。", style = MaterialTheme.typography.bodySmall)\n                Button(\n                    onClick = {\n                        vm.refreshModuleStatus()\n                        showAppScopePicker = true\n                    },\n                    enabled = state.module.connected,\n                    modifier = Modifier.fillMaxWidth()\n                ) { Text(if (pendingHookApps.isEmpty()) "选择应用加入 Hook" else "继续选择 / 加入 Hook（待提交 ${pendingHookApps.size}）") }\n                Text("勾选后必须在选择页面底部点击“加入 LSPosed Hook 列表”才会真正申请作用域。返回不会清空未提交选择。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n''','''                Text("应用隐藏列表", fontWeight = FontWeight.Bold)\n                Text("用于指定从哪些应用中隐藏规则目标，例如文件管理器自己的打开方式列表。这里不会 Hook 这些应用，也不需要把它们加入 LSPosed 作用域。", style = MaterialTheme.typography.bodySmall)\n                Button(\n                    onClick = { showAppScopePicker = true },\n                    modifier = Modifier.fillMaxWidth()\n                ) { Text("管理应用隐藏列表（${state.hiddenFromApps.size}）") }\n                Text("勾选立即保存；List Cleaner 仅依赖 android/system_server 作用域执行系统级可见性过滤。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n''')
p.write_text(s)

# 7) Tests: add round-trip coverage while preserving positional constructors.
p = Path('app/src/test/java/com/yagay/ListCleaner/domain/ModuleConfigTest.kt')
s = p.read_text()
needle = 'class ModuleConfigTest {'
if needle in s and 'hiddenFromAppsRoundTrip' not in s:
    s = s.replace(needle, needle + '''\n    @Test fun hiddenFromAppsRoundTrip() {\n        val config = ModuleConfig(emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715, TileConfig(), setOf("com.estrongs.android.pop"))\n        val encoded = Json.encodeToString(ModuleConfig.serializer(), config)\n        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(), encoded).validated())\n    }\n''')
p.write_text(s)
