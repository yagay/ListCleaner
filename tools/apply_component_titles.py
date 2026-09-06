from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f'missing pattern in {path}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1))

# PriorityConfig: component title storage + validation.
p = Path('app/src/main/java/com/yagay/ListCleaner/domain/PriorityConfig.kt')
p.write_text('''package com.yagay.ListCleaner.domain\n\nimport kotlinx.serialization.Serializable\n\n@Serializable\ndata class PriorityConfig(\n    val apps: Map<IntentKind, List<String>> = emptyMap(),\n    val titles: Map<String, String> = emptyMap()\n) {\n    fun validated(): PriorityConfig {\n        require(apps.values.all { packages ->\n            packages.size <= 200 && packages.distinct().size == packages.size &&\n                packages.all { it.isNotBlank() && it.length <= 255 && '|' !in it }\n        }) { "优先排序配置无效：每类最多 200 个应用，且不能重复" }\n        require(titles.size <= 2_000 && titles.all { (key, value) ->\n            val parsed = ComponentRule.fromId(key)\n            parsed != null && parsed.id == key && value.isNotBlank() && value.length <= 64 &&\n                value.none { it.isISOControl() }\n        }) { "自定义显示名称配置无效：最多 2000 项，每项最多 64 字符" }\n        return this\n    }\n}\n\n/** Stable promotion only: never inject targets or move targets between user/profile slots. */\nfun <T> prioritizeApps(\n    candidates: List<T>, packages: List<String>, packageOf: (T) -> String, profileOf: (T) -> Int\n): List<T> {\n    if (packages.isEmpty() || candidates.size < 2) return candidates\n    val ranks = packages.withIndex().associate { it.value to it.index }\n    val result = candidates.toMutableList()\n    candidates.indices.groupBy { profileOf(candidates[it]) }.values.forEach { positions ->\n        val ordered = positions.map { candidates[it] }.sortedBy { ranks[packageOf(it)] ?: Int.MAX_VALUE }\n        positions.forEachIndexed { index, position -> result[position] = ordered[index] }\n    }\n    return result\n}\n''')

# Repository preserves titles when sorting and edits titles atomically.
replace('app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt',
'''        val next = PriorityConfig(mutablePriorities.value.apps.toMutableMap().apply {\n            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())\n        }).validated()''',
'''        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {\n            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())\n        }).validated()''')
replace('app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt',
'''    fun encodePriorities(value: PriorityConfig = mutablePriorities.value): String =\n        json.encodeToString(PriorityConfig.serializer(), value)\n''',
'''    @Synchronized\n    fun setComponentTitle(ruleId: String, title: String?) {\n        val parsed = requireNotNull(ComponentRule.fromId(ruleId)) { "无效的组件标识" }\n        require(parsed.id == ruleId) { "组件标识必须使用规范化类名" }\n        val trimmed = title?.trim().orEmpty()\n        val titles = mutablePriorities.value.titles.toMutableMap().apply {\n            if (trimmed.isEmpty()) remove(ruleId) else put(ruleId, trimmed)\n        }\n        val next = mutablePriorities.value.copy(titles = titles).validated()\n        if (next == mutablePriorities.value) return\n        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()\n        mutablePriorities.value = next\n        mutableRevision.value++\n    }\n\n    fun encodePriorities(value: PriorityConfig = mutablePriorities.value): String =\n        json.encodeToString(PriorityConfig.serializer(), value)\n''')

# ViewModel edit entry point.
replace('app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt',
'''    fun importJson(content: String) = app.rules.importJson(content)\n\n    fun selectPriorityApps''',
'''    fun importJson(content: String) = app.rules.importJson(content)\n    fun setComponentTitle(ruleId: String, title: String?) {\n        if (canEdit()) app.rules.setComponentTitle(ruleId, title)\n    }\n\n    fun selectPriorityApps''')

# Shared editor dialog.
Path('app/src/main/java/com/yagay/ListCleaner/ui/ComponentTitleDialog.kt').write_text('''package com.yagay.ListCleaner.ui\n\nimport androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.height\nimport androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.Text\nimport androidx.compose.material3.TextButton\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\nimport androidx.compose.ui.unit.dp\nimport com.yagay.ListCleaner.domain.ComponentCandidate\n\n@Composable\ninternal fun ComponentTitleDialog(\n    item: ComponentCandidate,\n    currentTitle: String?,\n    onSave: (String?) -> Unit,\n    onDismiss: () -> Unit\n) {\n    var text by remember(item.rule.id) { mutableStateOf(currentTitle.orEmpty()) }\n    LaunchedEffect(currentTitle, item.rule.id) { text = currentTitle.orEmpty() }\n    val trimmed = text.trim()\n    val normalizedCurrent = currentTitle.orEmpty()\n    val changed = trimmed != normalizedCurrent\n    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text("自定义显示名称") },\n        text = {\n            Column {\n                Text("原名称：${item.activityLabel}", style = MaterialTheme.typography.bodyMedium)\n                Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                Spacer(androidx.compose.ui.Modifier.height(12.dp))\n                OutlinedTextField(\n                    value = text,\n                    onValueChange = { if (it.length <= 64 && it.none(Char::isISOControl)) text = it },\n                    label = { Text("菜单显示名称") },\n                    supportingText = { Text("${text.length}/64 · 留空保存可恢复原名称") },\n                    singleLine = true\n                )\n            }\n        },\n        confirmButton = {\n            TextButton(onClick = { onSave(trimmed.ifEmpty { null }); onDismiss() }, enabled = changed) { Text("保存") }\n        },\n        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }\n    )\n}\n''')

# Rules row: show custom title and edit button.
replace('app/src/main/java/com/yagay/ListCleaner/ui/AppListRows.kt',
'import androidx.compose.material.icons.rounded.ExpandMore\n',
'import androidx.compose.material.icons.rounded.ExpandMore\nimport androidx.compose.material.icons.rounded.Edit\n')
replace('app/src/main/java/com/yagay/ListCleaner/ui/AppListRows.kt',
'''    internal fun ComponentRow(item: ComponentCandidate, checked: Boolean, onToggle: () -> Unit) {''',
'''    internal fun ComponentRow(item: ComponentCandidate, checked: Boolean, customTitle: String?, onToggle: () -> Unit, onEditTitle: () -> Unit) {''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/AppListRows.kt',
'''                    Text(\n                        item.rule.className,''',
'''                    if (!customTitle.isNullOrBlank()) Text(\n                        "显示为：$customTitle",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.primary,\n                        maxLines = 1,\n                        overflow = TextOverflow.Ellipsis\n                    )\n                    Text(\n                        item.rule.className,''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/AppListRows.kt',
'''                }\n            }\n        }\n\n\n    @Composable\n    private fun ComponentSelectionMark''',
'''                }\n                IconButton(onClick = onEditTitle) {\n                    Icon(Icons.Rounded.Edit, contentDescription = "修改显示名称")\n                }\n            }\n        }\n\n\n    @Composable\n    private fun ComponentSelectionMark''')

# Rules page editor state + wiring.
replace('app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt',
'''fun RulesTab(state: MainState, vm: MainViewModel) {\n    val visibleRules''',
'''fun RulesTab(state: MainState, vm: MainViewModel) {\n    var editingTitle by remember { mutableStateOf<com.yagay.ListCleaner.domain.ComponentCandidate?>(null) }\n    editingTitle?.let { item ->\n        ComponentTitleDialog(item, state.priorities.titles[item.rule.id],\n            onSave = { vm.setComponentTitle(item.rule.id, it) },\n            onDismiss = { editingTitle = null })\n    }\n    val visibleRules''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt',
'''                    ComponentRow(component, component.rule in state.selected) { vm.toggle(component.rule) }''',
'''                    ComponentRow(component, component.rule in state.selected, state.priorities.titles[component.rule.id],\n                        onToggle = { vm.toggle(component.rule) }, onEditTitle = { editingTitle = component })''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt',
'''        Text("勾选应用可批量选择当前分类及搜索条件下显示的组件；展开可逐项选择。“查看”只筛选本页列表，不改变清理规则。",''',
'''        Text("勾选应用可批量选择当前分类及搜索条件下显示的组件；展开可逐项选择，也可修改组件在系统候选菜单中的显示名称。“查看”只筛选本页列表，不改变清理规则。",''')

# Priority page editor state, text, and row wiring.
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'import androidx.compose.material.icons.rounded.ExpandMore\n',
'import androidx.compose.material.icons.rounded.ExpandMore\nimport androidx.compose.material.icons.rounded.Edit\n')
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'''fun PriorityDialogContent(state: MainState, vm: MainViewModel) {\n    var kind''',
'''fun PriorityDialogContent(state: MainState, vm: MainViewModel) {\n    var editingTitle by remember { mutableStateOf<ComponentCandidate?>(null) }\n    editingTitle?.let { item ->\n        ComponentTitleDialog(item, state.priorities.titles[item.rule.id],\n            onSave = { vm.setComponentTitle(item.rule.id, it) },\n            onDismiss = { editingTitle = null })\n    }\n    var kind''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'''                Text("长按已优先应用可拖动排序，松手保存；展开后也可上移、下移。",''',
'''                Text("长按已优先应用可拖动排序，松手保存；展开后也可上移、下移。展开组件后可修改其在系统候选菜单中的显示名称。",''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'''                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { ComponentInfoRow(it) }''',
'''                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { item ->\n                    ComponentInfoRow(item, state.priorities.titles[item.rule.id]) { editingTitle = item }\n                }''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'''private fun ComponentInfoRow(item: ComponentCandidate) {''',
'''private fun ComponentInfoRow(item: ComponentCandidate, customTitle: String?, onEditTitle: () -> Unit) {''')
replace('app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt',
'''        Column {\n            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)\n            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)\n        }\n    }''',
'''        Column(Modifier.weight(1f)) {\n            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)\n            if (!customTitle.isNullOrBlank()) Text("显示为：$customTitle", style = MaterialTheme.typography.labelSmall,\n                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)\n            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)\n        }\n        IconButton(onClick = onEditTitle) { Icon(Icons.Rounded.Edit, "修改显示名称") }\n    }''')

# Hook: clone ResolveInfo and override nonLocalizedLabel after filtering/sorting.
replace('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt',
'''        if (ordered !== filtered) changed = true\n        if (!changed) {\n            diagnostic("NO_CHANGE $layer $kind size=${values.size} hasSelection=${current.hasSelection(kind)}")\n            return null\n        }\n        diagnostic("$layer $kind: ${values.size} -> ${ordered.size}")\n        return ordered\n    }''',
'''        if (ordered !== filtered) changed = true\n        val (titled, titleCount) = runCatching { applyCustomTitles(kind, ordered, current) }.getOrElse { failure ->\n            diagnostic("TITLE_FAILED kind=$kind error=${failure.javaClass.name}")\n            ordered to 0\n        }\n        if (titleCount > 0) changed = true\n        if (!changed) {\n            diagnostic("NO_CHANGE $layer $kind size=${values.size} hasSelection=${current.hasSelection(kind)}")\n            return null\n        }\n        diagnostic("$layer $kind: ${values.size} -> ${titled.size}")\n        return titled\n    }\n\n    private fun applyCustomTitles(kind: IntentKind, values: List<*>, current: RuleSnapshot): Pair<List<*>, Int> {\n        if (current.priorities.titles.isEmpty()) return values to 0\n        var replaced = 0\n        val result = values.map { value ->\n            val info = value as? ResolveInfo ?: return@map value\n            val activity = info.activityInfo ?: return@map value\n            val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n                activity.packageName, activity.name, activity.targetActivity\n            )\n            val key = "${kind.name}|${activity.packageName}|$canonicalClass"\n            val title = current.priorities.titles[key] ?: return@map value\n            replaced++\n            ResolveInfo(info).apply { nonLocalizedLabel = title }\n        }\n        if (replaced > 0) diagnostic("TITLES kind=$kind replaced=$replaced")\n        return if (replaced > 0) result to replaced else values to 0\n    }''')
replace('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt',
'''priorities=${config.priorities.apps.mapValues { it.value.size }} hiddenFromApps''',
'''priorities=${config.priorities.apps.mapValues { it.value.size }} titles=${config.priorities.titles.size} hiddenFromApps''')

# Tests.
replace('app/src/test/java/com/yagay/ListCleaner/domain/PriorityConfigTest.kt',
'''    @Test fun versionOneBackupDefaultsToSystemOrder() {''',
'''    @Test fun componentTitlesRoundTripAndRequireCanonicalRuleIds() {\n        val rule = ComponentRule(IntentKind.OPEN, "com.example", "com.example.OpenActivity")\n        val value = PriorityConfig(titles = mapOf(rule.id to "PDF 阅读器")).validated()\n        val text = Json.encodeToString(PriorityConfig.serializer(), value)\n        assertEquals(value, Json.decodeFromString(PriorityConfig.serializer(), text).validated())\n        assertThrows(IllegalArgumentException::class.java) {\n            PriorityConfig(titles = mapOf("OPEN|com.example|.OpenActivity" to "bad")).validated()\n        }\n        assertThrows(IllegalArgumentException::class.java) {\n            PriorityConfig(titles = mapOf(rule.id to "x".repeat(65))).validated()\n        }\n    }\n\n    @Test fun oldPriorityJsonDefaultsTitlesToEmpty() {\n        val decoded = Json.decodeFromString(PriorityConfig.serializer(), """{"apps":{"SHARE":["com.a"]}}""")\n        assertTrue(decoded.titles.isEmpty())\n    }\n\n    @Test fun versionOneBackupDefaultsToSystemOrder() {''')

print('component title patch applied')
