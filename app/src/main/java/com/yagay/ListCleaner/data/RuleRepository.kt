package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.RuleBackup
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.TileConfig
import com.yagay.ListCleaner.domain.DefaultOpenConfig
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class RuleRepository(context: Context) {
    private val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val mutableRules = MutableStateFlow(
        prefs.getStringSet(KEY_RULES, emptySet()).orEmpty().mapNotNull(ComponentRule::fromId).toSet()
    )
    private val mutableMode = MutableStateFlow(DisplayMode.fromStored(prefs.getString(KEY_DISPLAY_MODE, null), prefs.getBoolean(KEY_BLACKLIST, true)))
    private val mutablePriorities = MutableStateFlow(runCatching {
        json.decodeFromString(PriorityConfig.serializer(), prefs.getString(KEY_PRIORITIES, null) ?: "{}").validated()
    }.getOrDefault(PriorityConfig()))
    private val mutableDefaultOpen = MutableStateFlow(runCatching {
        json.decodeFromString(DefaultOpenConfig.serializer(), prefs.getString(KEY_DEFAULT_OPEN, null) ?: "{}").validated()
    }.getOrDefault(DefaultOpenConfig()))
    private val mutableOpenTypes = MutableStateFlow(runCatching {
        json.decodeFromString(OpenTypeConfig.serializer(), prefs.getString(KEY_OPEN_TYPES, null) ?: "{}").validated()
    }.getOrDefault(OpenTypeConfig()))
    val defaultOpen: StateFlow<DefaultOpenConfig> = mutableDefaultOpen.asStateFlow()
    val openTypes: StateFlow<OpenTypeConfig> = mutableOpenTypes.asStateFlow()
    private val mutableDiagnostic = MutableStateFlow(prefs.getBoolean(KEY_DIAGNOSTIC, false))
    private val mutableTiles = MutableStateFlow(runCatching {
        json.decodeFromString(TileConfig.serializer(), prefs.getString(KEY_TILES, null) ?: "{}").validated()
    }.getOrDefault(TileConfig()))
    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())
    val tiles: StateFlow<TileConfig> = mutableTiles.asStateFlow()
    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()

    val rules: StateFlow<Set<ComponentRule>> = mutableRules.asStateFlow()
    val displayMode: StateFlow<DisplayMode> = mutableMode.asStateFlow()
    val priorities: StateFlow<PriorityConfig> = mutablePriorities.asStateFlow()
    val diagnosticMode: StateFlow<Boolean> = mutableDiagnostic.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun hasLocalConfiguration(): Boolean = prefs.contains(KEY_INITIALIZED) || prefs.contains(KEY_RULES) ||
        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) ||
        prefs.contains(KEY_TILES) || prefs.contains(KEY_HIDDEN_FROM_APPS) || prefs.contains(KEY_DEFAULT_OPEN) || prefs.contains(KEY_OPEN_TYPES)

    fun markInitialized() { prefs.edit().putBoolean(KEY_INITIALIZED, true).apply() }

    @Synchronized fun restoreRemote(config: ModuleConfig) {
        config.validated()
        replace(config.rules, config.mode != DisplayMode.SHOW_SELECTED, config.priorities, config.mode, config.tiles, config.defaultOpen, config.openTypes)
        setHiddenFromApps(config.hiddenFromApps)
        setDiagnosticMode(config.diagnostic)
        markInitialized()
    }

    @Synchronized fun remoteSnapshot(): ModuleConfig = ModuleConfig(
        rules = mutableRules.value.toSet(), mode = mutableMode.value, priorities = mutablePriorities.value,
        diagnostic = mutableDiagnostic.value, managerAppId = android.os.Process.myUid() % 100_000,
        tiles = mutableTiles.value, hiddenFromApps = mutableHiddenFromApps.value.toSet(),
        defaultOpen = mutableDefaultOpen.value, openTypes = mutableOpenTypes.value
    )

    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val self = "com.yagay.ListCleaner"
        val valid = packages.asSequence().map(String::trim)
            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }
            .take(2_001).toSet()
        require(valid.size <= 2_000) { "隐藏应用列表数量过多" }
        mutableHiddenFromApps.value = valid
        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()
        mutableRevision.value++
    }

    @Synchronized fun setDefaultOpen(preset: OpenPreset, ruleId: String?) {
        val nextMap = mutableDefaultOpen.value.preferred.toMutableMap()
        if (ruleId == null) nextMap.remove(preset) else {
            val parsed = requireNotNull(ComponentRule.fromId(ruleId)) { "无效的默认打开组件" }
            require(parsed.id == ruleId) { "默认打开组件必须使用规范化类名" }
            if (preset == OpenPreset.BROWSER) require(parsed.kind == IntentKind.BROWSER) else require(parsed.kind == IntentKind.OPEN)
            nextMap[preset] = ruleId
        }
        val next = DefaultOpenConfig(nextMap).validated()
        if (next == mutableDefaultOpen.value) return
        mutableDefaultOpen.value = next
        prefs.edit().putString(KEY_DEFAULT_OPEN, json.encodeToString(DefaultOpenConfig.serializer(), next)).apply()
        mutableRevision.value++
    }

    @Synchronized fun setOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>, selected: Boolean) {
        require(preset != OpenPreset.BROWSER)
        val ids = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }.map { requireNotNull(ComponentRule.fromId(it.id)).id }.toSet()
        if (ids.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply { if (selected) addAll(ids) else removeAll(ids) }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun toggleOpenType(preset: OpenPreset, rule: ComponentRule) {
        require(preset != OpenPreset.BROWSER && rule.kind == IntentKind.OPEN && rule.isValid())
        val current = mutableOpenTypes.value.rules[preset].orEmpty()
        setOpenTypeSelected(preset, listOf(rule), rule.id !in current)
    }

    @Synchronized fun invertOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>) {
        require(preset != OpenPreset.BROWSER)
        val valid = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }.map { requireNotNull(ComponentRule.fromId(it.id)).id }.distinct()
        if (valid.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply { valid.forEach { if (!add(it)) remove(it) } }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun setOpenTypePriority(preset: OpenPreset, packages: List<String>) {
        require(preset != OpenPreset.BROWSER)
        val map = mutableOpenTypes.value.priorities.toMutableMap().apply {
            if (packages.isEmpty()) remove(preset) else put(preset, packages.toList())
        }
        setOpenTypes(mutableOpenTypes.value.copy(priorities = map))
    }

    private fun setOpenTypes(value: OpenTypeConfig) {
        val next = value.validated()
        if (next == mutableOpenTypes.value) return
        mutableOpenTypes.value = next
        prefs.edit().putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), next)).apply()
        mutableRevision.value++
    }

    @Synchronized fun setDiagnosticMode(enabled: Boolean) {
        mutableDiagnostic.value = enabled
        prefs.edit().putBoolean(KEY_DIAGNOSTIC, enabled).apply()
        mutableRevision.value++
    }

    @Synchronized fun setPriority(kind: IntentKind, packages: List<String>) {
        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {
            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())
        }).validated()
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    @Synchronized fun setComponentTitle(ruleId: String, title: String?) {
        val parsed = requireNotNull(ComponentRule.fromId(ruleId)) { "无效的组件标识" }
        require(parsed.id == ruleId) { "组件标识必须使用规范化类名" }
        val trimmed = title?.trim().orEmpty()
        val titles = mutablePriorities.value.titles.toMutableMap().apply { if (trimmed.isEmpty()) remove(ruleId) else put(ruleId, trimmed) }
        val next = mutablePriorities.value.copy(titles = titles).validated()
        if (next == mutablePriorities.value) return
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    fun encodePriorities(value: PriorityConfig = mutablePriorities.value): String = json.encodeToString(PriorityConfig.serializer(), value)

    @Synchronized fun toggle(rule: ComponentRule) {
        require(rule.isValid()) { "无效的组件规则" }
        val canonical = requireNotNull(ComponentRule.fromId(rule.id))
        updateRules(mutableRules.value.toMutableSet().apply { if (!add(canonical)) remove(canonical) }.toSet())
    }

    @Synchronized fun setSelected(rules: Collection<ComponentRule>, selected: Boolean) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }
        val next = mutableRules.value.toMutableSet().apply { if (selected) addAll(valid) else removeAll(valid.toSet()) }.toSet()
        updateRules(next)
    }

    @Synchronized fun invertSelected(rules: Collection<ComponentRule>) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }.distinct()
        if (valid.isEmpty()) return
        val next = mutableRules.value.toMutableSet().apply { valid.forEach { rule -> if (!add(rule)) remove(rule) } }.toSet()
        updateRules(next)
    }

    @Synchronized fun setDisplayMode(value: DisplayMode) {
        mutableMode.value = value
        prefs.edit().putString(KEY_DISPLAY_MODE, value.name).apply()
        mutableRevision.value++
    }

    @Synchronized fun replace(
        rules: Set<ComponentRule>, blacklist: Boolean, priorities: PriorityConfig = PriorityConfig(),
        displayMode: DisplayMode = DisplayMode.fromStored(null, blacklist), tiles: TileConfig = TileConfig(),
        defaultOpen: DefaultOpenConfig = DefaultOpenConfig(), openTypes: OpenTypeConfig = OpenTypeConfig()
    ) {
        require(rules.size <= MAX_RULES) { "备份规则数量过多" }
        require(rules.all(ComponentRule::isValid)) { "备份包含无效组件" }
        priorities.validated(); tiles.validated(); defaultOpen.validated(); openTypes.validated()
        mutableRules.value = rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        mutableMode.value = displayMode
        mutablePriorities.value = priorities
        mutableTiles.value = tiles
        mutableDefaultOpen.value = defaultOpen
        mutableOpenTypes.value = openTypes
        prefs.edit()
            .putStringSet(KEY_RULES, rules.map(ComponentRule::id).toSet())
            .putBoolean(KEY_BLACKLIST, blacklist)
            .putString(KEY_DISPLAY_MODE, displayMode.name)
            .putString(KEY_PRIORITIES, encodePriorities(priorities))
            .putString(KEY_TILES, json.encodeToString(TileConfig.serializer(), tiles))
            .putString(KEY_DEFAULT_OPEN, json.encodeToString(DefaultOpenConfig.serializer(), defaultOpen))
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), openTypes))
            .apply()
        mutableRevision.value++
    }

    @Synchronized fun exportJson(): String = json.encodeToString(
        RuleBackup.serializer(),
        RuleBackup(version = 7, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value,
            priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value,
            hiddenFromApps = mutableHiddenFromApps.value, defaultOpen = mutableDefaultOpen.value, openTypes = mutableOpenTypes.value)
    )

    fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS) { "备份文件过大" }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..7) { "不支持的备份版本：${backup.version}" }
        replace(backup.rules, backup.blacklist,
            if (backup.version == 1) PriorityConfig() else backup.priorities,
            if (backup.version >= 3) requireNotNull(backup.displayMode) { "备份缺少显示模式" } else DisplayMode.fromStored(null, backup.blacklist),
            if (backup.version >= 4) backup.tiles else TileConfig(),
            if (backup.version >= 6) backup.defaultOpen else DefaultOpenConfig(),
            if (backup.version >= 7) backup.openTypes else OpenTypeConfig())
        setHiddenFromApps(if (backup.version >= 5) backup.hiddenFromApps else emptySet())
    }

    private fun updateRules(next: Set<ComponentRule>) {
        require(next.size <= MAX_RULES) { "规则数量过多" }
        mutableRules.value = next
        prefs.edit().putStringSet(KEY_RULES, next.map(ComponentRule::id).toSet()).apply()
        mutableRevision.value++
    }

    companion object {
        const val REMOTE_PREFS = "rules"
        const val KEY_RULES = "components"
        const val KEY_BLACKLIST = "blacklist"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_PRIORITIES = "priority_apps"
        const val KEY_DIAGNOSTIC = "diagnostic_mode"
        const val KEY_CONFIG = "config_v1"
        const val KEY_TILES = "tile_config"
        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"
        const val KEY_DEFAULT_OPEN = "default_open"
        const val KEY_OPEN_TYPES = "open_type_config"
        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC, KEY_HIDDEN_FROM_APPS, KEY_DEFAULT_OPEN, KEY_OPEN_TYPES)
        private const val LOCAL_PREFS = "rules_local"
        private const val KEY_INITIALIZED = "configuration_initialized"
        private const val MAX_RULES = 20_000
        const val MAX_BACKUP_CHARS = 2_000_000
    }
}
