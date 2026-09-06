from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise RuntimeError(f'target not found in {path}: {old[:80]!r}')
    p.write_text(s.replace(old, new, 1))

# 1) Shared ListControls: optional bulk actions at the end of the "查看 / 全部" row.
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainControls.kt',
    '''internal fun ListControls(state: MainState, onFilter: (IntentKind?) -> Unit, onUiFilter: (UiFilter) -> Unit,\n                          includeAllKinds: Boolean = true, viewTitle: (UiFilter) -> String = { it.title }) {''',
    '''internal fun ListControls(state: MainState, onFilter: (IntentKind?) -> Unit, onUiFilter: (UiFilter) -> Unit,\n                          includeAllKinds: Boolean = true, viewTitle: (UiFilter) -> String = { it.title },\n                          onSelectAll: (() -> Unit)? = null, onInvert: (() -> Unit)? = null) {'''
)
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainControls.kt',
    '''                }\n            }\n        }\n        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())''',
    '''                }\n            }\n            if (onSelectAll != null || onInvert != null) {\n                Spacer(Modifier.weight(1f))\n                onSelectAll?.let { action ->\n                    TextButton(onClick = action) { Text("全选") }\n                }\n                onInvert?.let { action ->\n                    TextButton(onClick = action) { Text("反选") }\n                }\n            }\n        }\n        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())'''
)

# 2) Rules page: bulk actions affect only currently visible components (kind / search / view filter respected).
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt',
    '''fun RulesTab(state: MainState, vm: MainViewModel) {\n\n    LazyColumn''',
    '''fun RulesTab(state: MainState, vm: MainViewModel) {\n    val visibleRules = state.groups.flatMap { it.components }.map { it.rule }.distinct()\n\n    LazyColumn'''
)
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt',
    '''                ListControls(state, vm::setFilter, vm::setUiFilter)''',
    '''                ListControls(state, vm::setFilter, vm::setUiFilter,\n                    onSelectAll = { vm.selectRules(visibleRules) },\n                    onInvert = { vm.invertRules(visibleRules) })'''
)

# 3) Priority page: bulk actions affect the currently visible app list; preserve existing order and 200-item cap.
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
    '''                    viewTitle = { when (it) {\n                        UiFilter.ALL -> "全部"\n                        UiFilter.HIDE_SELECTED -> "未优先"\n                        UiFilter.SHOW_SELECTED -> "已优先"\n                    } })''',
    '''                    viewTitle = { when (it) {\n                        UiFilter.ALL -> "全部"\n                        UiFilter.HIDE_SELECTED -> "未优先"\n                        UiFilter.SHOW_SELECTED -> "已优先"\n                    } },\n                    onSelectAll = { vm.selectPriorityApps(kind, groups.map { it.packageName }) },\n                    onInvert = { vm.invertPriorityApps(kind, groups.map { it.packageName }) })'''
)

# 4) Root component page: add full-select/invert next to the view selector, before refresh.
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/RootComponentsScreen.kt',
    '''                        Spacer(Modifier.weight(1f))\n                        TextButton(onClick = vm::refreshComponents, enabled = !busy) { Text(if (busy) "处理中…" else "刷新") }''',
    '''                        Spacer(Modifier.weight(1f))\n                        TextButton(\n                            onClick = { vm.changeComponents(visible, enable = false) },\n                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null && it.enabled == true }\n                        ) { Text("全选") }\n                        TextButton(\n                            onClick = { vm.invertComponents(visible) },\n                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null }\n                        ) { Text("反选") }\n                        TextButton(onClick = vm::refreshComponents, enabled = !busy) { Text(if (busy) "处理中…" else "刷新") }'''
)

# 5) ViewModel bulk helpers.
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt',
    '''    fun toggle(rule: ComponentRule) { if (canEdit()) app.rules.toggle(rule) }\n    fun setGroupSelected(group: AppGroup, selected: Boolean) { if (canEdit()) app.rules.setSelected(group.components.map { it.rule }, selected) }''',
    '''    fun toggle(rule: ComponentRule) { if (canEdit()) app.rules.toggle(rule) }\n    fun setGroupSelected(group: AppGroup, selected: Boolean) { if (canEdit()) app.rules.setSelected(group.components.map { it.rule }, selected) }\n    fun selectRules(rules: Collection<ComponentRule>) {\n        if (!canEdit() || rules.isEmpty()) return\n        app.rules.setSelected(rules.distinct(), true)\n    }\n    fun invertRules(rules: Collection<ComponentRule>) {\n        if (!canEdit() || rules.isEmpty()) return\n        val current = app.rules.rules.value\n        val unique = rules.distinct()\n        val select = unique.filter { it !in current }\n        val unselect = unique.filter { it in current }\n        if (select.isNotEmpty()) app.rules.setSelected(select, true)\n        if (unselect.isNotEmpty()) app.rules.setSelected(unselect, false)\n    }'''
)

replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt',
    '''    fun pinApp(kind: IntentKind, packageName: String) {\n        if (!canEdit()) return''',
    '''    fun selectPriorityApps(kind: IntentKind, packageNames: Collection<String>) {\n        if (!canEdit()) return\n        val current = app.rules.priorities.value.apps[kind].orEmpty()\n        val additions = packageNames.distinct().filter { it !in current }\n        val next = (current + additions).take(200)\n        if (next != current) app.rules.setPriority(kind, next)\n    }\n\n    fun invertPriorityApps(kind: IntentKind, packageNames: Collection<String>) {\n        if (!canEdit()) return\n        val visible = packageNames.distinct()\n        if (visible.isEmpty()) return\n        val visibleSet = visible.toSet()\n        val current = app.rules.priorities.value.apps[kind].orEmpty()\n        val retained = current.filterNot { it in visibleSet }\n        val added = visible.filter { it !in current }\n        val next = (retained + added).take(200)\n        if (next != current) app.rules.setPriority(kind, next)\n    }\n\n    fun pinApp(kind: IntentKind, packageName: String) {\n        if (!canEdit()) return'''
)

# Mixed-state root inversion in one Root session, so busy gating cannot drop half the operation.
replace_once(
    'app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt',
    '''    private val mutableUpdating = MutableStateFlow(false)''',
    '''    fun invertComponents(visibleTargets: List<RootComponent>) {\n        if (mutableComponentBusy.value) return\n        val targets = visibleTargets.filter { it.blocked == null && it.enabled != null }\n            .distinctBy { "${it.user}|${it.component.flattenToString()}" }\n        if (targets.isEmpty()) return\n        mutableComponentRootNotice.value = null\n        mutableComponentBusy.value = true\n        mutableComponentMessage.value = "正在请求 Root 并反选组件…"\n        viewModelScope.launch {\n            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {\n                var completed = 0\n                var operationStarted = false\n                try {\n                    rootCatalog.requireRoot()\n                    for (target in targets) {\n                        operationStarted = true\n                        val enable = target.enabled == false\n                        mutableComponentMessage.value = "正在反选 ${completed + 1}/${targets.size}：${target.label}"\n                        rootCatalog.change(target, enable)\n                        completed++\n                    }\n                    mutableComponentMessage.value = "已核验：$completed 个组件已反选；请重新打开目标选择器"\n                } catch (failure: ComponentRootCommand.RootAccessException) {\n                    mutableComponentMessage.value = failure.message\n                    mutableComponentRootNotice.value = failure.message\n                } catch (failure: Exception) {\n                    mutableComponentMessage.value = "已完成 $completed/${targets.size}，反选已停止：${failure.message ?: "操作失败"}。已完成项不回滚。"\n                } finally {\n                    if (operationStarted) runCatching { rootCatalog.scan() }.onSuccess { mutableComponentScan.value = it }\n                        .onFailure { mutableComponentScan.value = RootComponentScan(warning = "操作后扫描失败，请刷新；不使用旧状态") }\n                    mutableComponentBusy.value = false\n                }\n            }\n        }\n    }\n\n    private val mutableUpdating = MutableStateFlow(false)'''
)

print('bulk select controls applied')
