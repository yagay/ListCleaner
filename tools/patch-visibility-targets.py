from pathlib import Path

# ModuleConfig
p=Path('app/src/main/java/com/yagay/ListCleaner/domain/ModuleConfig.kt')
s=p.read_text()
s=s.replace('''    // Apps in this list are callers from which selected target packages will be hidden at system_server.\n    // They do NOT need to be added to the LSPosed module scope.\n    val hiddenFromApps: Set<String> = emptySet()\n''','''    // Caller apps whose package view is restricted in system_server. They do not need LSPosed scope.\n    val hiddenFromApps: Set<String> = emptySet(),\n    // Explicit package targets hidden from callers above. Resolver component rules are intentionally separate.\n    val visibilityHiddenTargets: Set<String> = emptySet()\n''')
s=s.replace('''        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(::validPackageName))\n        return this\n''','''        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(::validPackageName))\n        require(visibilityHiddenTargets.size <= 2_000 && visibilityHiddenTargets.all(::validPackageName))\n        return this\n''')
p.write_text(s)

# Rule backup
p=Path('app/src/main/java/com/yagay/ListCleaner/domain/Rule.kt')
s=p.read_text().replace('''    val tiles: TileConfig = TileConfig(),\n    val hiddenFromApps: Set<String> = emptySet()\n)''','''    val tiles: TileConfig = TileConfig(),\n    val hiddenFromApps: Set<String> = emptySet(),\n    val visibilityHiddenTargets: Set<String> = emptySet()\n)''')
p.write_text(s)

# Repository
p=Path('app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt')
s=p.read_text()
s=s.replace('''    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())\n    val tiles: StateFlow<TileConfig> = mutableTiles.asStateFlow()\n    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()\n''','''    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())\n    private val mutableVisibilityHiddenTargets = MutableStateFlow(prefs.getStringSet(KEY_VISIBILITY_HIDDEN_TARGETS, emptySet()).orEmpty().toSet())\n    val tiles: StateFlow<TileConfig> = mutableTiles.asStateFlow()\n    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()\n    val visibilityHiddenTargets: StateFlow<Set<String>> = mutableVisibilityHiddenTargets.asStateFlow()\n''')
s=s.replace('''prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) || prefs.contains(KEY_TILES) || prefs.contains(KEY_HIDDEN_FROM_APPS)''','''prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) || prefs.contains(KEY_TILES) || prefs.contains(KEY_HIDDEN_FROM_APPS) || prefs.contains(KEY_VISIBILITY_HIDDEN_TARGETS)''')
s=s.replace('''        setHiddenFromApps(config.hiddenFromApps)\n        setDiagnosticMode(config.diagnostic)''','''        setHiddenFromApps(config.hiddenFromApps)\n        setVisibilityHiddenTargets(config.visibilityHiddenTargets)\n        setDiagnosticMode(config.diagnostic)''')
s=s.replace('''        mutableDiagnostic.value, android.os.Process.myUid() % 100_000, mutableTiles.value, mutableHiddenFromApps.value.toSet()\n    )''','''        mutableDiagnostic.value, android.os.Process.myUid() % 100_000, mutableTiles.value,\n        mutableHiddenFromApps.value.toSet(), mutableVisibilityHiddenTargets.value.toSet()\n    )''')
insert='''\n    @Synchronized fun setVisibilityHiddenTargets(packages: Set<String>) {\n        val self = "com.yagay.ListCleaner"\n        val valid = packages.asSequence()\n            .map(String::trim)\n            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }\n            .take(2_001).toSet()\n        require(valid.size <= 2_000) { "隐藏目标应用数量过多" }\n        mutableVisibilityHiddenTargets.value = valid\n        prefs.edit().putStringSet(KEY_VISIBILITY_HIDDEN_TARGETS, valid).apply()\n        mutableRevision.value++\n    }\n'''
s=s.replace('''    @Synchronized\n    fun setDiagnosticMode(enabled: Boolean) {''',insert+'''\n    @Synchronized\n    fun setDiagnosticMode(enabled: Boolean) {''')
s=s.replace('''RuleBackup(version = 5, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value, priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value, hiddenFromApps = mutableHiddenFromApps.value)''','''RuleBackup(version = 6, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value, priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value, hiddenFromApps = mutableHiddenFromApps.value, visibilityHiddenTargets = mutableVisibilityHiddenTargets.value)''')
s=s.replace('''require(backup.version in 1..5)''','''require(backup.version in 1..6)''')
s=s.replace('''        setHiddenFromApps(if (backup.version >= 5) backup.hiddenFromApps else emptySet())''','''        setHiddenFromApps(if (backup.version >= 5) backup.hiddenFromApps else emptySet())\n        setVisibilityHiddenTargets(if (backup.version >= 6) backup.visibilityHiddenTargets else emptySet())''')
s=s.replace('''        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"\n        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC, KEY_HIDDEN_FROM_APPS)''','''        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"\n        const val KEY_VISIBILITY_HIDDEN_TARGETS = "visibility_hidden_targets"\n        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC, KEY_HIDDEN_FROM_APPS, KEY_VISIBILITY_HIDDEN_TARGETS)''')
p.write_text(s)

# MainViewModel
p=Path('app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt')
s=p.read_text()
s=s.replace('''    val hiddenFromApps: Set<String> = emptySet(),\n    val groups:''','''    val hiddenFromApps: Set<String> = emptySet(),\n    val visibilityHiddenTargets: Set<String> = emptySet(),\n    val groups:''')
s=s.replace('''expandedAppKey, app.rules.tiles, app.rules.hiddenFromApps\n    )''','''expandedAppKey, app.rules.tiles, app.rules.hiddenFromApps, app.rules.visibilityHiddenTargets\n    )''')
s=s.replace('''            hiddenFromApps = values[12] as Set<String>,\n            uiFilter''','''            hiddenFromApps = values[12] as Set<String>,\n            visibilityHiddenTargets = values[13] as Set<String>,\n            uiFilter''')
s=s.replace('''    fun setHiddenFromApps(packages: Set<String>) {\n        app.rules.setHiddenFromApps(packages)\n        viewModelScope.launch { app.synchronize() }\n    }\n''','''    fun setHiddenFromApps(packages: Set<String>) {\n        app.rules.setHiddenFromApps(packages)\n        viewModelScope.launch { app.synchronize() }\n    }\n\n    fun setVisibilityHiddenTargets(packages: Set<String>) {\n        app.rules.setVisibilityHiddenTargets(packages)\n        viewModelScope.launch { app.synchronize() }\n    }\n''')
p.write_text(s)

# Generic picker
p=Path('app/src/main/java/com/yagay/ListCleaner/ui/AppScopePicker.kt')
s=p.read_text()
s=s.replace('''internal fun AppScopePickerDialog(\n    selected: Set<String>,\n    onSelectedChange: (Set<String>) -> Unit,\n    dismiss: () -> Unit,\n) {''','''internal fun AppScopePickerDialog(\n    selected: Set<String>,\n    onSelectedChange: (Set<String>) -> Unit,\n    title: String = "应用隐藏列表",\n    description: String = "这里选择的是“从哪些应用中隐藏目标”。这些应用不需要加入 LSPosed Hook 作用域；List Cleaner 只在 system/system_server 侧应用隐藏。勾选立即保存。",\n    selectedLabel: String = "已选择",\n    dismiss: () -> Unit,\n) {''')
s=s.replace('''title = { Text("应用隐藏列表") }''','''title = { Text(title) }''')
s=s.replace('''                    "这里选择的是“从哪些应用中隐藏规则目标”。这些应用不需要加入 LSPosed Hook 作用域；List Cleaner 只在 system/system_server 侧应用隐藏。勾选立即保存。",''','''                    description,''')
s=s.replace('''                    "已加入隐藏列表 ${selected.size} 个应用",''','''                    "$selectedLabel ${selected.size} 个应用",''')
p.write_text(s)

# Dashboard two independent lists
p=Path('app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt')
s=p.read_text()
s=s.replace('''    var showAppScopePicker by remember { mutableStateOf(false) }''','''    var showAppScopePicker by remember { mutableStateOf(false) }\n    var showVisibilityTargetPicker by remember { mutableStateOf(false) }''')
s=s.replace('''    if (showAppScopePicker) AppScopePickerDialog(\n        selected = state.hiddenFromApps,\n        onSelectedChange = vm::setHiddenFromApps,\n    ) { showAppScopePicker = false }''','''    if (showAppScopePicker) AppScopePickerDialog(\n        selected = state.hiddenFromApps,\n        onSelectedChange = vm::setHiddenFromApps,\n        title = "限制来源应用",\n        description = "选择“谁不能看到隐藏目标”，例如 ES 文件管理器。这里只保存 caller，不会 Hook 这些应用。",\n        selectedLabel = "已限制来源"\n    ) { showAppScopePicker = false }\n    if (showVisibilityTargetPicker) AppScopePickerDialog(\n        selected = state.visibilityHiddenTargets,\n        onSelectedChange = vm::setVisibilityHiddenTargets,\n        title = "隐藏目标应用",\n        description = "选择要对上面的来源应用隐藏的完整应用包。这里与 OPEN/SHARE/BROWSER 等组件规则完全独立，避免把所有规则涉及的应用都隐藏。",\n        selectedLabel = "已隐藏目标"\n    ) { showVisibilityTargetPicker = false }''')
old='''                Text("应用隐藏列表", fontWeight = FontWeight.Bold)\n                Text("用于指定从哪些应用中隐藏规则目标，例如文件管理器自己的打开方式列表。这里不会 Hook 这些应用，也不需要把它们加入 LSPosed 作用域。", style = MaterialTheme.typography.bodySmall)\n                Button(\n                    onClick = { showAppScopePicker = true },\n                    modifier = Modifier.fillMaxWidth()\n                ) { Text("管理应用隐藏列表（${state.hiddenFromApps.size}）") }\n                Text("勾选立即保存；List Cleaner 仅依赖 android/system_server 作用域执行系统级可见性过滤。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'''
new='''                Text("系统级应用隐藏", fontWeight = FontWeight.Bold)\n                Text("分开配置“从哪些应用中隐藏”和“隐藏哪些应用”。组件清理规则不会再自动变成整包隐藏目标。", style = MaterialTheme.typography.bodySmall)\n                Button(onClick = { showAppScopePicker = true }, modifier = Modifier.fillMaxWidth()) {\n                    Text("限制来源应用（${state.hiddenFromApps.size}）")\n                }\n                OutlinedButton(onClick = { showVisibilityTargetPicker = true }, modifier = Modifier.fillMaxWidth()) {\n                    Text("隐藏目标应用（${state.visibilityHiddenTargets.size}）")\n                }\n                Text("只有来源列表与目标列表都非空时，system_server 才执行 HMA 风格包可见性过滤。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'''
if old not in s: raise SystemExit('MainTabs block not found')
s=s.replace(old,new)
p.write_text(s)

# System visibility uses explicit targets only.
p=Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s=p.read_text()
s=s.replace('''        val hiddenFromApps: Set<String> = emptySet()\n''','''        val hiddenFromApps: Set<String> = emptySet(),\n        val visibilityHiddenTargets: Set<String> = emptySet()\n''',1)
s=s.replace('''record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} targets=${snapshot.allSelectedPackages.size} hotPath=memory_snapshot")''','''record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} targets=${snapshot.visibilityHiddenTargets.size} hotPath=memory_snapshot")''')
s=s.replace('''            current.hiddenFromApps.isNotEmpty() && current.allSelectedPackages.isNotEmpty()''','''            current.hiddenFromApps.isNotEmpty() && current.visibilityHiddenTargets.isNotEmpty()''')
s=s.replace('''        if (target !in current.allSelectedPackages) return false''','''        if (target !in current.visibilityHiddenTargets) return false''')
s=s.replace('''snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,\n                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps)''','''snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,\n                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps, config.visibilityHiddenTargets)''')
s=s.replace('''hiddenFromApps=${config.hiddenFromApps.size} digest=$digest''','''hiddenFromApps=${config.hiddenFromApps.size} visibilityTargets=${config.visibilityHiddenTargets.size} digest=$digest''')
p.write_text(s)

# Tests: add one regression without rewriting positional tests.
p=Path('app/src/test/java/com/yagay/ListCleaner/domain/ModuleConfigTest.kt')
s=p.read_text()
if 'visibilityTargetsRoundTrip' not in s:
    s=s.replace('\n}', '''\n    @Test fun visibilityTargetsRoundTrip() {\n        val config = ModuleConfig(emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715, TileConfig(), setOf("com.estrongs.android.pop"), setOf("com.openai.chatgpt"))\n        val decoded = Json.decodeFromString(ModuleConfig.serializer(), Json.encodeToString(ModuleConfig.serializer(), config)).validated()\n        assertEquals(setOf("com.estrongs.android.pop"), decoded.hiddenFromApps)\n        assertEquals(setOf("com.openai.chatgpt"), decoded.visibilityHiddenTargets)\n    }\n}''')
p.write_text(s)
