package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.RuleBackup
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.TileConfig
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

    // Existing installations may have deliberately empty rules; key presence, not count, matters.
    fun hasLocalConfiguration(): Boolean = prefs.contains(KEY_INITIALIZED) || prefs.contains(KEY_RULES) ||
        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) || prefs.contains(KEY_TILES) || prefs.contains(KEY_HIDDEN_FROM_APPS)

    fun markInitialized() { prefs.edit().putBoolean(KEY_INITIALIZED, true).apply() }

    @Synchronized fun restoreRemote(config: ModuleConfig) {
        config.validated()
        replace(config.rules, config.mode != DisplayMode.SHOW_SELECTED, config.priorities, config.mode, config.tiles)
        setHiddenFromApps(config.hiddenFromApps)
        setDiagnosticMode(config.diagnostic)
        markInitialized()
    }

    @Synchronized fun remoteSnapshot(): ModuleConfig = ModuleConfig(
        mutableRules.value.toSet(), mutableMode.value, mutablePriorities.value,
        mutableDiagnostic.value, android.os.Process.myUid() % 100_000, mutableTiles.value, mutableHiddenFromApps.value.toSet()
    )

    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val self = "com.yagay.ListCleaner"
        val valid = packages.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }
            .take(2_001).toSet()
        require(valid.size <= 2_000) { "隐藏应用列表数量过多" }
        mutableHiddenFromApps.value = valid
        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()
        mutableRevision.value++
    }

    @Synchronized
    fun setDiagnosticMode(enabled: Boolean) {
        mutableDiagnostic.value = enabled
        prefs.edit().putBoolean(KEY_DIAGNOSTIC, enabled).apply()
        mutableRevision.value++
    }

    @Synchronized
    fun setPriority(kind: IntentKind, packages: List<String>) {
        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {
            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())
        }).validated()
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    @Synchronized
    fun setComponentTitle(ruleId: String, title: String?) {
        val parsed = requireNotNull(ComponentRule.fromId(ruleId)) { "无效的组件标识" }
        require(parsed.id == ruleId) { "组件标识必须使用规范化类名" }
        val trimmed = title?.trim().orEmpty()
        val titles = mutablePriorities.value.titles.toMutableMap().apply {
            if (trimmed.isEmpty()) remove(ruleId) else put(ruleId, trimmed)
        }
        val next = mutablePriorities.value.copy(titles = titles).validated()
        if (next == mutablePriorities.value) return
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    fun encodePriorities(value: PriorityConfig = mutablePriorities.value): String =
        json.encodeToString(PriorityConfig.serializer(), value)

    @Synchronized fun toggle(rule: ComponentRule) {
        require(rule.isValid()) { "无效的组件规则" }
        val canonical = requireNotNull(ComponentRule.fromId(rule.id))
        updateRules(mutableRules.value.toMutableSet().apply { if (!add(canonical)) remove(canonical) }.toSet())
    }

    @Synchronized fun setSelected(rules: Collection<ComponentRule>, selected: Boolean) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }
        val next = mutableRules.value.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid.toSet())
        }.toSet()
        updateRules(next)
    }

    @Synchronized fun invertSelected(rules: Collection<ComponentRule>) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }.distinct()
        if (valid.isEmpty()) return
        val next = mutableRules.value.toMutableSet().apply {
            valid.forEach { rule -> if (!add(rule)) remove(rule) }
        }.toSet()
        updateRules(next)
    }

    @Synchronized fun setDisplayMode(value: DisplayMode) {
        // An empty whitelist is treated as disabled by the hook, so it can never blank the Resolver.
        mutableMode.value = value
        prefs.edit().putString(KEY_DISPLAY_MODE, value.name).apply()
        mutableRevision.value++
    }

    @Synchronized
    fun replace(rules: Set<ComponentRule>, blacklist: Boolean, priorities: PriorityConfig = PriorityConfig(), displayMode: DisplayMode = DisplayMode.fromStored(null, blacklist), tiles: TileConfig = TileConfig()) {
        require(rules.size <= MAX_RULES) { "备份规则数量过多" }
        require(rules.all(ComponentRule::isValid)) { "备份包含无效组件" }
        priorities.validated()
        tiles.validated()
        mutableRules.value = rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        mutableMode.value = displayMode
        mutablePriorities.value = priorities
        mutableTiles.value = tiles
        prefs.edit()
            .putStringSet(KEY_RULES, rules.map(ComponentRule::id).toSet())
            .putBoolean(KEY_BLACKLIST, blacklist)
            .putString(KEY_DISPLAY_MODE, displayMode.name)
            .putString(KEY_PRIORITIES, encodePriorities(priorities))
            .putString(KEY_TILES, json.encodeToString(TileConfig.serializer(), tiles))
            .apply()
        mutableRevision.value++
    }

    @Synchronized fun exportJson(): String = json.encodeToString(
        RuleBackup.serializer(),
        RuleBackup(version = 5, blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED, rules = mutableRules.value, priorities = mutablePriorities.value, displayMode = mutableMode.value, tiles = mutableTiles.value, hiddenFromApps = mutableHiddenFromApps.value)
    )

    fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS) { "备份文件过大" }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..5) { "不支持的备份版本：${backup.version}" }
        replace(backup.rules, backup.blacklist, if (backup.version == 1) PriorityConfig() else backup.priorities, if (backup.version >= 3) requireNotNull(backup.displayMode) { "备份缺少显示模式" } else DisplayMode.fromStored(null, backup.blacklist), if (backup.version >= 4) backup.tiles else TileConfig())
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
        val SYNCED_KEYS = setOf(KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC, KEY_HIDDEN_FROM_APPS)
        private const val LOCAL_PREFS = "rules_local"
        private const val KEY_INITIALIZED = "configuration_initialized"
        private const val MAX_RULES = 20_000
        const val MAX_BACKUP_CHARS = 2_000_000
    }
}
