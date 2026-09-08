package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.RuleBackup
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.VisibilityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class RuleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val mutableRules = MutableStateFlow(
        prefs.getStringSet(KEY_RULES, emptySet()).orEmpty().mapNotNull(ComponentRule::fromId).toSet()
    )
    private val mutableMode = MutableStateFlow(DisplayMode.fromStored(prefs.getString(KEY_DISPLAY_MODE, null), prefs.getBoolean(KEY_BLACKLIST, true)))
    private val mutablePriorities = MutableStateFlow(runCatching {
        json.decodeFromString(PriorityConfig.serializer(), prefs.getString(KEY_PRIORITIES, null) ?: "{}").validated()
    }.getOrDefault(PriorityConfig()))
    private val mutableOpenTypes = MutableStateFlow(runCatching {
        json.decodeFromString(OpenTypeConfig.serializer(), prefs.getString(KEY_OPEN_TYPES, null) ?: "{}").validated()
    }.getOrDefault(OpenTypeConfig()))
    private val mutableDiagnostic = MutableStateFlow(prefs.getBoolean(KEY_DIAGNOSTIC, false))
    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())
    private val mutableVisibilityScopes = MutableStateFlow(
        prefs.getStringSet(KEY_VISIBILITY_SCOPES, emptySet()).orEmpty().mapNotNull { name ->
            runCatching { VisibilityScope.valueOf(name) }.getOrNull()
        }.toSet()
    )
    private val mutableVisibilityFullPackages = MutableStateFlow<Map<VisibilityScope, Set<String>>>(emptyMap())
    val openTypes: StateFlow<OpenTypeConfig> = mutableOpenTypes.asStateFlow()
    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()
    val visibilityScopes: StateFlow<Set<VisibilityScope>> = mutableVisibilityScopes.asStateFlow()
    val visibilityFullPackages: StateFlow<Map<VisibilityScope, Set<String>>> = mutableVisibilityFullPackages.asStateFlow()
    val rules: StateFlow<Set<ComponentRule>> = mutableRules.asStateFlow()
    val displayMode: StateFlow<DisplayMode> = mutableMode.asStateFlow()
    val priorities: StateFlow<PriorityConfig> = mutablePriorities.asStateFlow()
    val diagnosticMode: StateFlow<Boolean> = mutableDiagnostic.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun hasLocalConfiguration(): Boolean = prefs.contains(KEY_INITIALIZED) || prefs.contains(KEY_RULES) ||
        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) ||
        prefs.contains(KEY_HIDDEN_FROM_APPS) || prefs.contains(KEY_OPEN_TYPES) || prefs.contains(KEY_VISIBILITY_SCOPES) ||
        prefs.contains(KEY_TILES) || prefs.contains(KEY_DEFAULT_OPEN)

    fun markInitialized() {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).remove(KEY_TILES).remove(KEY_DEFAULT_OPEN).apply()
    }

    @Synchronized fun restoreRemote(config: ModuleConfig) {
        config.validated()
        replace(config.rules, config.mode != DisplayMode.SHOW_SELECTED, config.priorities, config.mode, config.openTypes)
        setHiddenFromApps(config.hiddenFromApps)
        setVisibilityScopes(config.visibilityCompat.scopes)
        setDiagnosticMode(config.diagnostic)
        markInitialized()
    }

    /** Legacy ModuleConfig fields remain deserializable, but new remote snapshots always use defaults. */
    @Synchronized fun remoteSnapshot(): ModuleConfig = ModuleConfig(
        rules = mutableRules.value.toSet(), mode = mutableMode.value, priorities = mutablePriorities.value,
        diagnostic = mutableDiagnostic.value, managerAppId = android.os.Process.myUid() % 100_000,
        hiddenFromApps = mutableHiddenFromApps.value.toSet(), openTypes = mutableOpenTypes.value,
        visibilityCompat = VisibilityCompatConfig(
            scopes = mutableVisibilityScopes.value.toSet(),
            fullPackages = mutableVisibilityFullPackages.value.mapValues { it.value.toSet() }
        ).validated()
    )

    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val self = "com.yagay.ListCleaner"
        val valid = packages.asSequence().map(String::trim)
            .filter { it.isNotEmpty() && it != "android" && it != self && it.length <= 255 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' } }
            .take(2_001).toSet()
        require(valid.size <= 2_000) { appContext.getString(R.string.repo_hidden_apps_too_many) }
        if (valid == mutableHiddenFromApps.value) return
        mutableHiddenFromApps.value = valid
        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()
        mutableRevision.value++
    }

    @Synchronized fun setVisibilityScopes(scopes: Set<VisibilityScope>) {
        val valid = scopes.toSet()
        if (valid == mutableVisibilityScopes.value) return
        mutableVisibilityScopes.value = valid
        prefs.edit().putStringSet(KEY_VISIBILITY_SCOPES, valid.map { it.name }.toSet()).apply()
        mutableRevision.value++
    }

    /** Derived from the current candidate catalog. Not backed up; rebuilt after scans/rule changes. */
    @Synchronized fun setVisibilityFullPackages(packages: Map<VisibilityScope, Set<String>>) {
        val next = VisibilityCompatConfig(fullPackages = packages).validated().fullPackages
            .mapValues { it.value.toSet() }
        if (next == mutableVisibilityFullPackages.value) return
        mutableVisibilityFullPackages.value = next
        mutableRevision.value++
    }

    @Synchronized fun setCustomOpenDefinition(preset: OpenPreset, definition: CustomOpenDefinition?) {
        require(preset.isCustom) { appContext.getString(R.string.repo_custom_slot_only) }
        val current = mutableOpenTypes.value
        val definitions = current.customDefinitions.toMutableMap()
        val rules = current.rules.toMutableMap()
        val priorities = current.priorities.toMutableMap()
        if (definition == null) {
            definitions.remove(preset)
            rules.remove(preset)
            priorities.remove(preset)
        } else definitions[preset] = definition.validated()
        setOpenTypes(current.copy(rules = rules, priorities = priorities, customDefinitions = definitions))
    }

    @Synchronized fun setOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>, selected: Boolean) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
        val ids = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.toSet()
        if (ids.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply {
            if (selected) addAll(ids) else removeAll(ids)
        }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun toggleOpenType(preset: OpenPreset, rule: ComponentRule) {
        require(preset != OpenPreset.BROWSER && rule.kind == IntentKind.OPEN && rule.isValid())
        setOpenTypeSelected(preset, listOf(rule), rule.id !in mutableOpenTypes.value.rules[preset].orEmpty())
    }

    @Synchronized fun invertOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
        val valid = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.distinct()
        if (valid.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply {
            valid.forEach { if (!add(it)) remove(it) }
        }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun setOpenTypePriority(preset: OpenPreset, packages: List<String>) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
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
        if (mutableDiagnostic.value == enabled) return
        mutableDiagnostic.value = enabled
        prefs.edit().putBoolean(KEY_DIAGNOSTIC, enabled).apply()
        mutableRevision.value++
    }

    @Synchronized fun setPriority(kind: IntentKind, packages: List<String>) {
        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {
            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())
        }).validated()
        if (next == mutablePriorities.value) return
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    @Synchronized fun setComponentTitle(ruleId: String, title: String?) {
        val parsed = requireNotNull(ComponentRule.fromId(ruleId)) {
            appContext.getString(R.string.repo_invalid_component_id)
        }
        require(parsed.id == ruleId) { appContext.getString(R.string.repo_component_id_not_normalized) }
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
        require(rule.isValid()) { appContext.getString(R.string.repo_invalid_component_rule) }
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
        updateRules(mutableRules.value.toMutableSet().apply {
            valid.forEach { rule -> if (!add(rule)) remove(rule) }
        }.toSet())
    }

    @Synchronized fun setDisplayMode(value: DisplayMode) {
        if (mutableMode.value == value) return
        mutableMode.value = value
        prefs.edit().putString(KEY_DISPLAY_MODE, value.name).apply()
        mutableRevision.value++
    }

    @Synchronized fun replace(
        rules: Set<ComponentRule>, blacklist: Boolean, priorities: PriorityConfig = PriorityConfig(),
        displayMode: DisplayMode = DisplayMode.fromStored(null, blacklist), openTypes: OpenTypeConfig = OpenTypeConfig()
    ) {
        require(rules.size <= MAX_RULES) { appContext.getString(R.string.repo_backup_too_many_rules) }
        require(rules.all(ComponentRule::isValid)) { appContext.getString(R.string.repo_backup_invalid_component) }
        priorities.validated()
        openTypes.validated()
        mutableRules.value = rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        mutableMode.value = displayMode
        mutablePriorities.value = priorities
        mutableOpenTypes.value = openTypes.validated()
        mutableVisibilityFullPackages.value = emptyMap()
        prefs.edit()
            .putStringSet(KEY_RULES, mutableRules.value.map(ComponentRule::id).toSet())
            .putBoolean(KEY_BLACKLIST, blacklist)
            .putString(KEY_DISPLAY_MODE, displayMode.name)
            .putString(KEY_PRIORITIES, encodePriorities(priorities))
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), mutableOpenTypes.value))
            .remove(KEY_TILES)
            .remove(KEY_DEFAULT_OPEN)
            .apply()
        mutableRevision.value++
    }

    @Synchronized fun exportJson(): String = json.encodeToString(
        RuleBackup.serializer(),
        RuleBackup(
            version = 9,
            blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED,
            rules = mutableRules.value,
            priorities = mutablePriorities.value,
            displayMode = mutableMode.value,
            hiddenFromApps = mutableHiddenFromApps.value,
            openTypes = mutableOpenTypes.value,
            visibilityScopes = mutableVisibilityScopes.value
        )
    )

    fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..9) {
            appContext.getString(R.string.repo_backup_unsupported_version, backup.version)
        }
        // Legacy tiles/defaultOpen are deliberately ignored: those features no longer have runtime consumers.
        replace(
            backup.rules,
            backup.blacklist,
            if (backup.version == 1) PriorityConfig() else backup.priorities,
            if (backup.version >= 3) requireNotNull(backup.displayMode) {
                appContext.getString(R.string.repo_backup_missing_display_mode)
            } else DisplayMode.fromStored(null, backup.blacklist),
            if (backup.version >= 7) backup.openTypes else OpenTypeConfig()
        )
        setHiddenFromApps(if (backup.version >= 5) backup.hiddenFromApps else emptySet())
        setVisibilityScopes(if (backup.version >= 9) backup.visibilityScopes else emptySet())
        markInitialized()
    }

    private fun updateRules(next: Set<ComponentRule>) {
        require(next.size <= MAX_RULES) { appContext.getString(R.string.repo_too_many_rules) }
        if (next == mutableRules.value) return
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
        /** Legacy keys retained only so upgrades can detect and erase old local state. */
        const val KEY_TILES = "tile_config"
        const val KEY_DEFAULT_OPEN = "default_open"
        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"
        const val KEY_OPEN_TYPES = "open_type_config"
        const val KEY_VISIBILITY_SCOPES = "visibility_scopes"
        val SYNCED_KEYS = setOf(
            KEY_RULES,
            KEY_BLACKLIST,
            KEY_DISPLAY_MODE,
            KEY_PRIORITIES,
            KEY_DIAGNOSTIC,
            KEY_HIDDEN_FROM_APPS,
            KEY_OPEN_TYPES,
            KEY_VISIBILITY_SCOPES
        )
        private const val LOCAL_PREFS = "rules_local"
        private const val KEY_INITIALIZED = "configuration_initialized"
        private const val MAX_RULES = 20_000
        const val MAX_BACKUP_CHARS = 2_000_000
    }
}
