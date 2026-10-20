package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.RuleBackup
import com.yagay.ListCleaner.domain.MultiSpaceBackup
import com.yagay.ListCleaner.domain.SpaceBackupEntry
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.VisibilityScope
import com.yagay.ListCleaner.domain.UserSpaceRules
import com.yagay.ListCleaner.domain.toSpaceRules
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class BackupImportPreview(val total: Int, val added: Int, val removed: Int, val version: Int)

data class SpaceBackupPreview(
    val sourceUserId: Int,
    val sourceName: String,
    val rules: Int,
    val rootDisabledRecords: Int,
)

data class SpaceBackupInspection(
    val version: Int,
    val spaces: List<SpaceBackupPreview>,
)

class RuleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val initialConfig = loadInitialConfig()
    /** Each Android user has independent rules; 0 is always the legacy-compatible base. */
    private val storedSpaces = mutableMapOf<Int, UserSpaceRules>().apply {
        put(0, initialConfig.toSpaceRules())
        putAll(initialConfig.userSpaces)
    }
    private var activeUserId = 0
    private val derivedVisibility = mutableMapOf<Int, Map<VisibilityScope, Set<String>>>()
    val selectedUserId: Int get() = activeUserId

    @Synchronized fun switchSpace(userId: Int) {
        require(userId in 0..99999)
        if (userId == activeUserId) return
        storedSpaces[activeUserId] = localSnapshot().toSpaceRules()
        derivedVisibility[activeUserId] = mutableVisibilityFullPackages.value
        activeUserId = userId
        val next = storedSpaces[userId] ?: UserSpaceRules()
        mutableRules.value = next.rules
        mutableMode.value = next.mode
        mutablePriorities.value = next.priorities
        mutableOpenTypes.value = next.openTypes
        mutableBrowserLinks.value = next.browserLinks
        mutableDiagnostic.value = next.diagnostic
        mutableHiddenFromApps.value = next.hiddenFromApps
        mutableVisibilityScopes.value = next.visibilityCompat.scopes
        mutableVisibilityFullPackages.value = derivedVisibility[userId].orEmpty()
        persistLocalSnapshot()
        mutableRevision.value++
    }

    private val mutableRules = MutableStateFlow(initialConfig.rules)
    private val mutableMode = MutableStateFlow(initialConfig.mode)
    private val mutablePriorities = MutableStateFlow(initialConfig.priorities)
    private val mutableOpenTypes = MutableStateFlow(initialConfig.openTypes)
    private val mutableBrowserLinks = MutableStateFlow(initialConfig.browserLinks)
    private val mutableDiagnostic = MutableStateFlow(initialConfig.diagnostic)
    private val mutableHiddenFromApps = MutableStateFlow(initialConfig.hiddenFromApps)
    private val mutableVisibilityScopes = MutableStateFlow(initialConfig.visibilityCompat.scopes)
    private val mutableVisibilityFullPackages = MutableStateFlow<Map<VisibilityScope, Set<String>>>(emptyMap())

    val openTypes: StateFlow<OpenTypeConfig> = mutableOpenTypes.asStateFlow()
    val browserLinks: StateFlow<BrowserLinkConfig> = mutableBrowserLinks.asStateFlow()
    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()
    val visibilityScopes: StateFlow<Set<VisibilityScope>> = mutableVisibilityScopes.asStateFlow()
    val visibilityFullPackages: StateFlow<Map<VisibilityScope, Set<String>>> = mutableVisibilityFullPackages.asStateFlow()
    val rules: StateFlow<Set<ComponentRule>> = mutableRules.asStateFlow()
    val displayMode: StateFlow<DisplayMode> = mutableMode.asStateFlow()
    val priorities: StateFlow<PriorityConfig> = mutablePriorities.asStateFlow()
    val diagnosticMode: StateFlow<Boolean> = mutableDiagnostic.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    // SharedPreferences returns a stable string until a write. Validate a given snapshot only
    // once so repeated runtime handshakes do not decode megabytes of JSON.
    private var validatedLocalJson: String? = null
    private var validatedLocalResult: Boolean = false

    init {
        if (!prefs.contains(KEY_LOCAL_CONFIG) && hasLegacyLocalConfiguration()) {
            persistLocalSnapshot()
        }
    }

    private fun hasLegacyLocalConfiguration(): Boolean =
        prefs.contains(KEY_INITIALIZED) || prefs.contains(KEY_RULES) || prefs.contains(KEY_DISPLAY_MODE) ||
            prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) ||
            prefs.contains(KEY_HIDDEN_FROM_APPS) || prefs.contains(KEY_OPEN_TYPES) ||
            prefs.contains(KEY_BROWSER_LINKS) || prefs.contains(KEY_VISIBILITY_SCOPES) ||
            prefs.contains(KEY_TILES) || prefs.contains(KEY_DEFAULT_OPEN)

    private fun loadInitialConfig(): ModuleConfig {
        prefs.getString(KEY_LOCAL_CONFIG, null)?.let { encoded ->
            if (encoded.length <= MAX_BACKUP_CHARS) {
                runCatching {
                    json.decodeFromString(ModuleConfig.serializer(), encoded)
                        .copy(managerAppId = -1, rootDisabledComponents = null)
                        .validated()
                }.getOrNull()?.let { return it }
            }
        }

        val legacyRules = prefs.getStringSet(KEY_RULES, emptySet()).orEmpty()
            .mapNotNull(ComponentRule::fromId).toSet()
        val legacyMode = DisplayMode.fromStored(
            prefs.getString(KEY_DISPLAY_MODE, null),
            prefs.getBoolean(KEY_BLACKLIST, true),
        )
        val legacyPriorities = runCatching {
            json.decodeFromString(PriorityConfig.serializer(), prefs.getString(KEY_PRIORITIES, null) ?: "{}")
                .validated()
        }.getOrDefault(PriorityConfig())
        val legacyOpenTypes = runCatching {
            json.decodeFromString(OpenTypeConfig.serializer(), prefs.getString(KEY_OPEN_TYPES, null) ?: "{}")
                .validated()
        }.getOrDefault(OpenTypeConfig())
        val legacyBrowserLinks = runCatching {
            json.decodeFromString(BrowserLinkConfig.serializer(), prefs.getString(KEY_BROWSER_LINKS, null) ?: "{}")
                .validated()
        }.getOrDefault(BrowserLinkConfig())
        val legacyHidden = sanitizeHiddenAppsForLoad(
            prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty()
        )
        val legacyScopes = prefs.getStringSet(KEY_VISIBILITY_SCOPES, emptySet()).orEmpty()
            .mapNotNull { name -> runCatching { VisibilityScope.valueOf(name) }.getOrNull() }
            .toSet()
        return ModuleConfig(
            rules = legacyRules,
            mode = legacyMode,
            priorities = legacyPriorities,
            diagnostic = prefs.getBoolean(KEY_DIAGNOSTIC, false),
            hiddenFromApps = legacyHidden,
            openTypes = legacyOpenTypes,
            browserLinks = legacyBrowserLinks,
            visibilityCompat = VisibilityCompatConfig(scopes = legacyScopes),
        ).validated()
    }

    /**
     * A corrupt local JSON value must not masquerade as a valid configuration: doing so
     * can cause an empty legacy fallback to overwrite recoverable remote rules.
     */
    @Synchronized
    private fun validStoredSnapshot(): Boolean {
        val encoded = prefs.getString(KEY_LOCAL_CONFIG, null) ?: return false
        if (encoded === validatedLocalJson || encoded == validatedLocalJson) {
            return validatedLocalResult
        }
        val valid = encoded.length <= MAX_BACKUP_CHARS && runCatching {
            json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
        }.isSuccess
        validatedLocalJson = encoded
        validatedLocalResult = valid
        return valid
    }

    fun hasCorruptLocalConfiguration(): Boolean =
        prefs.contains(KEY_LOCAL_CONFIG) && !validStoredSnapshot()

    fun hasLocalConfiguration(): Boolean =
        if (prefs.contains(KEY_LOCAL_CONFIG)) validStoredSnapshot()
        else hasLegacyLocalConfiguration()

    @Synchronized fun markInitialized() {
        persistLocalSnapshot()
    }

    @Synchronized fun restoreRemote(config: ModuleConfig) {
        activeUserId = 0
        derivedVisibility.clear()
        storedSpaces.clear()
        storedSpaces[0] = config.toSpaceRules()
        storedSpaces.putAll(config.userSpaces)
        applyConfig(config, config.mode != DisplayMode.SHOW_SELECTED)
    }

    /** Publish every space atomically, never just the one currently displayed in the UI. */
    @Synchronized fun remoteSnapshot(): ModuleConfig {
        val active = localSnapshot().copy(
            visibilityCompat = VisibilityCompatConfig(
                scopes = mutableVisibilityScopes.value.toSet(),
                fullPackages = mutableVisibilityFullPackages.value.mapValues { it.value.toSet() }
            )
        ).toSpaceRules()
        val all = storedSpaces.toMutableMap()
        all[activeUserId] = active
        val scoped = all.mapValues { (userId, space) ->
            val full = if (userId == activeUserId) mutableVisibilityFullPackages.value
                       else derivedVisibility[userId].orEmpty()
            space.copy(visibilityCompat = VisibilityCompatConfig(
                scopes = space.visibilityCompat.scopes,
                fullPackages = full,
            ))
        }
        val primary = (scoped[0] ?: UserSpaceRules()).toModuleConfig()
        return primary.copy(
            managerAppId = AndroidUid.appId(android.os.Process.myUid()),
            userSpaces = scoped.filterKeys { it != 0 },
        ).validated()
    }

    private fun localSnapshot(): ModuleConfig = ModuleConfig(
        rules = mutableRules.value.toSet(),
        mode = mutableMode.value,
        priorities = mutablePriorities.value,
        diagnostic = mutableDiagnostic.value,
        hiddenFromApps = mutableHiddenFromApps.value.toSet(),
        openTypes = mutableOpenTypes.value,
        browserLinks = mutableBrowserLinks.value,
        visibilityCompat = VisibilityCompatConfig(scopes = mutableVisibilityScopes.value.toSet()),
    ).validated()

    private fun persistPrepared(config: ModuleConfig) {
        val local = config.copy(
            managerAppId = -1,
            rootDisabledComponents = null,
            visibilityCompat = VisibilityCompatConfig(scopes = config.visibilityCompat.scopes.toSet()),
            userSpaces = emptyMap(),
        ).validated()
        storedSpaces[activeUserId] = local.toSpaceRules()
        val primary = (storedSpaces[0] ?: UserSpaceRules()).toModuleConfig()
            .copy(userSpaces = storedSpaces.filterKeys { it != 0 }).validated()
        val encoded = json.encodeToString(ModuleConfig.serializer(), primary)
        prefs.edit()
            .putString(KEY_LOCAL_CONFIG, encoded)
            .putBoolean(KEY_INITIALIZED, true)
            .remove(KEY_TILES)
            .remove(KEY_DEFAULT_OPEN)
            .apply()
        validatedLocalJson = encoded
        validatedLocalResult = true
    }

    private fun persistLocalSnapshot() = persistPrepared(localSnapshot())

    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val valid = normalizeHiddenApps(packages)
        if (valid == mutableHiddenFromApps.value) return
        mutableHiddenFromApps.value = valid
        persistLocalSnapshot()
        mutableRevision.value++
    }

    @Synchronized fun setVisibilityScopes(scopes: Set<VisibilityScope>) {
        val valid = scopes.toSet()
        if (valid == mutableVisibilityScopes.value) return
        mutableVisibilityScopes.value = valid
        persistLocalSnapshot()
        mutableRevision.value++
    }

    /** Derived from the current candidate catalog. Not backed up; rebuilt after scans/rule changes. */
    @Synchronized fun setVisibilityFullPackages(packages: Map<VisibilityScope, Set<String>>) {
        val next = VisibilityCompatConfig(fullPackages = packages).validated().fullPackages
            .mapValues { it.value.toSet() }
        if (next == mutableVisibilityFullPackages.value) return
        mutableVisibilityFullPackages.value = next
        derivedVisibility[activeUserId] = next
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
        persistLocalSnapshot()
        mutableRevision.value++
    }

    @Synchronized fun setBrowserHosts(hosts: Set<String>) {
        val normalized = hosts.map { requireNotNull(normalizeBrowserHost(it)) { "invalid_browser_host" } }.toSet()
        require(normalized.size <= BrowserLinkConfig.MAX_HOSTS) { "too_many_browser_hosts" }
        val current = mutableBrowserLinks.value
        setBrowserLinks(
            current.copy(
                hosts = normalized,
                rules = current.rules.filterKeys { it in normalized },
                priorities = current.priorities.filterKeys { it in normalized }
            )
        )
    }

    @Synchronized fun setBrowserHostSelected(host: String, rules: Collection<ComponentRule>, selected: Boolean) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val ids = rules.filter { it.kind == IntentKind.DEEP_LINK && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.toSet()
        if (ids.isEmpty()) return
        val map = mutableBrowserLinks.value.rules.toMutableMap()
        val next = map[normalized].orEmpty().toMutableSet().apply {
            if (selected) addAll(ids) else removeAll(ids)
        }
        if (next.isEmpty()) map.remove(normalized) else map[normalized] = next
        setBrowserLinks(mutableBrowserLinks.value.copy(rules = map))
    }

    @Synchronized fun toggleBrowserHost(host: String, rule: ComponentRule) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(rule.kind == IntentKind.DEEP_LINK && rule.isValid())
        setBrowserHostSelected(normalized, listOf(rule), rule.id !in mutableBrowserLinks.value.rules[normalized].orEmpty())
    }

    @Synchronized fun invertBrowserHostSelected(host: String, rules: Collection<ComponentRule>) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val valid = rules.filter { it.kind == IntentKind.DEEP_LINK && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.distinct()
        if (valid.isEmpty()) return
        val map = mutableBrowserLinks.value.rules.toMutableMap()
        val next = map[normalized].orEmpty().toMutableSet().apply {
            valid.forEach { if (!add(it)) remove(it) }
        }
        if (next.isEmpty()) map.remove(normalized) else map[normalized] = next
        setBrowserLinks(mutableBrowserLinks.value.copy(rules = map))
    }

    @Synchronized fun setBrowserHostPriority(host: String, packages: List<String>) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val map = mutableBrowserLinks.value.priorities.toMutableMap().apply {
            if (packages.isEmpty()) remove(normalized) else put(normalized, packages.toList())
        }
        setBrowserLinks(mutableBrowserLinks.value.copy(priorities = map))
    }

    private fun setBrowserLinks(value: BrowserLinkConfig) {
        val next = value.validated()
        if (next == mutableBrowserLinks.value) return
        mutableBrowserLinks.value = next
        persistLocalSnapshot()
        mutableRevision.value++
    }

    @Synchronized fun setDiagnosticMode(enabled: Boolean) {
        if (mutableDiagnostic.value == enabled) return
        mutableDiagnostic.value = enabled
        persistLocalSnapshot()
        mutableRevision.value++
    }

    @Synchronized fun setPriority(kind: IntentKind, packages: List<String>) {
        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {
            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())
        }).validated()
        if (next == mutablePriorities.value) return
        mutablePriorities.value = next
        persistLocalSnapshot()
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
        mutablePriorities.value = next
        persistLocalSnapshot()
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
        persistLocalSnapshot()
        mutableRevision.value++
    }

    @Synchronized fun replace(
        rules: Set<ComponentRule>, blacklist: Boolean, priorities: PriorityConfig = PriorityConfig(),
        displayMode: DisplayMode = DisplayMode.fromStored(null, blacklist), openTypes: OpenTypeConfig = OpenTypeConfig()
    ) {
        applyConfig(
            ModuleConfig(
                rules = rules,
                mode = displayMode,
                priorities = priorities,
                diagnostic = mutableDiagnostic.value,
                hiddenFromApps = mutableHiddenFromApps.value,
                openTypes = openTypes,
                browserLinks = mutableBrowserLinks.value,
                visibilityCompat = VisibilityCompatConfig(scopes = mutableVisibilityScopes.value)
            ),
            blacklist
        )
    }

    /** Snapshot includes unswitched edits, but never derives settings from another profile. */
    @Synchronized private fun allSpaceSettings(): Map<Int, UserSpaceRules> =
        storedSpaces.toMutableMap().apply { put(activeUserId, localSnapshot().toSpaceRules()) }.toMap()

    /** Select any subset of spaces; names are informational and not trusted during restore. */
    @Synchronized fun exportSpaces(
        userIds: Set<Int>,
        names: Map<Int, String>,
        rootDisabledKeys: Set<String> = emptySet(),
    ): String {
        require(userIds.isNotEmpty() && userIds.size <= 16)
        val available = allSpaceSettings()
        val entries = userIds.sorted().map { userId ->
            require(userId in 0..99999)
            val settings = available[userId] ?: UserSpaceRules()
            SpaceBackupEntry(
                sourceUserId = userId,
                sourceName = names[userId]?.trim()?.take(100)?.takeIf { it.isNotBlank() }
                    ?: "User $userId",
                settings = settings.validated(),
                rootDisabledComponents = rootDisabledKeys.filterTo(linkedSetOf()) {
                    it.startsWith("$userId|")
                },
            )
        }
        val encoded = json.encodeToString(MultiSpaceBackup.serializer(),
            MultiSpaceBackup(spaces = entries).validated())
        require(encoded.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        return encoded
    }

    /**
     * Legacy (v1–v12) archives contain exactly one set of resolver rules. Treat it as one
     * source entry that can be explicitly mapped to *any* target Android user.
     */
    @Synchronized private fun decodePortableBackup(content: String): MultiSpaceBackup {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val root = json.parseToJsonElement(content).jsonObject
        if (root["format"]?.jsonPrimitive?.content == MultiSpaceBackup.FORMAT) {
            return json.decodeFromString(MultiSpaceBackup.serializer(), content).validated()
        }
        val old = json.decodeFromString(RuleBackup.serializer(), content)
        require(old.version in 1..12) {
            appContext.getString(R.string.repo_backup_unsupported_version, old.version)
        }
        if (old.version >= 3) requireNotNull(old.displayMode) {
            appContext.getString(R.string.repo_backup_missing_display_mode)
        }
        require(old.rules.all(ComponentRule::isValid)) {
            appContext.getString(R.string.repo_backup_invalid_component)
        }
        val settings = UserSpaceRules(
            rules = old.rules,
            mode = old.displayMode ?: DisplayMode.fromStored(null, old.blacklist),
            priorities = if (old.version == 1) PriorityConfig() else old.priorities,
            diagnostic = false,
            hiddenFromApps = if (old.version >= 5) old.hiddenFromApps else emptySet(),
            openTypes = if (old.version >= 7) old.openTypes else OpenTypeConfig(),
            browserLinks = if (old.version >= 10) old.browserLinks else BrowserLinkConfig(),
            visibilityCompat = VisibilityCompatConfig(
                scopes = if (old.version >= 9) old.visibilityScopes else emptySet()
            ),
        ).validated()
        return MultiSpaceBackup(spaces = listOf(
            SpaceBackupEntry(sourceUserId = 0, sourceName = appContext.getString(R.string.backup_spaces_legacy_source),
                settings = settings)
        )).validated()
    }

    fun inspectSpaces(content: String): SpaceBackupInspection {
        val version = json.parseToJsonElement(content).jsonObject["version"]
            ?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val parsed = decodePortableBackup(content)
        return SpaceBackupInspection(version, parsed.spaces.map {
            SpaceBackupPreview(it.sourceUserId, it.sourceName, it.settings.rules.size,
                it.rootDisabledComponents.size)
        })
    }

    /** Source IDs are never assumed to be equal to target IDs on another phone. */
    @Synchronized fun importSpaces(content: String, mappings: Map<Int, Int>) {
        val configs = decodePortableBackup(content).mapped(mappings)
        val next = allSpaceSettings().toMutableMap().apply { putAll(configs) }
        replaceSpaceSettings(next, mappings.values.toSet())
    }

    /** Used for one-tap undo: stores every profile, including profiles absent from the UI. */
    @Synchronized fun createSpaceCheckpoint(): String {
        val all = allSpaceSettings()
        val config = (all[0] ?: UserSpaceRules()).toModuleConfig()
            .copy(userSpaces = all.filterKeys { it != 0 }).validated()
        return json.encodeToString(ModuleConfig.serializer(), config)
    }

    @Synchronized fun restoreSpaceCheckpoint(checkpoint: String) {
        require(checkpoint.length <= MAX_BACKUP_CHARS)
        val saved = json.decodeFromString(ModuleConfig.serializer(), checkpoint).validated()
        val all = mutableMapOf(0 to saved.toSpaceRules()).apply { putAll(saved.userSpaces) }
        replaceSpaceSettings(all, all.keys, replaceAll = true)
    }

    private fun replaceSpaceSettings(
        requested: Map<Int, UserSpaceRules>,
        modified: Set<Int>,
        replaceAll: Boolean = false,
    ) {
        require(requested.containsKey(0) && requested.keys.all { it in 0..99999 })
        val clean = requested.mapValues { (_, value) ->
            value.copy(visibilityCompat = VisibilityCompatConfig(
                scopes = value.visibilityCompat.scopes
            )).validated()
        }
        val primary = requireNotNull(clean[0]).toModuleConfig()
            .copy(userSpaces = clean.filterKeys { it != 0 }).validated()
        val encoded = json.encodeToString(ModuleConfig.serializer(), primary)
        require(encoded.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        // Commit once before publishing new state to UI/Hook. If persistence fails, do not mutate
        // any in-memory space or start a partial user-by-user import.
        check(prefs.edit().putString(KEY_LOCAL_CONFIG, encoded)
            .putBoolean(KEY_INITIALIZED, true).commit()) { "Space backup could not be committed" }
        storedSpaces.clear()
        storedSpaces.putAll(clean)
        if (replaceAll) derivedVisibility.clear()
        else modified.forEach(derivedVisibility::remove)
        validatedLocalJson = encoded
        validatedLocalResult = true
        val active = storedSpaces[activeUserId] ?: UserSpaceRules()
        mutableRules.value = active.rules
        mutableMode.value = active.mode
        mutablePriorities.value = active.priorities
        mutableOpenTypes.value = active.openTypes
        mutableBrowserLinks.value = active.browserLinks
        mutableDiagnostic.value = active.diagnostic
        mutableHiddenFromApps.value = active.hiddenFromApps
        mutableVisibilityScopes.value = active.visibilityCompat.scopes
        mutableVisibilityFullPackages.value = emptyMap()
        mutableRevision.value++
    }

    @Synchronized fun exportJson(): String = json.encodeToString(
        RuleBackup.serializer(),
        RuleBackup(
            version = 12,
            blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED,
            rules = mutableRules.value,
            priorities = mutablePriorities.value,
            displayMode = mutableMode.value,
            hiddenFromApps = mutableHiddenFromApps.value,
            openTypes = mutableOpenTypes.value,
            browserLinks = mutableBrowserLinks.value,
            visibilityScopes = mutableVisibilityScopes.value
        )
    )

    @Synchronized fun previewImportJson(content: String): BackupImportPreview {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..12) {
            appContext.getString(R.string.repo_backup_unsupported_version, backup.version)
        }
        if (backup.version >= 3) requireNotNull(backup.displayMode) {
            appContext.getString(R.string.repo_backup_missing_display_mode)
        }
        require(backup.rules.all(ComponentRule::isValid)) {
            appContext.getString(R.string.repo_backup_invalid_component)
        }
        val incoming = backup.rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        val existing = mutableRules.value
        return BackupImportPreview(incoming.size, (incoming - existing).size, (existing - incoming).size, backup.version)
    }

    @Synchronized fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..12) {
            appContext.getString(R.string.repo_backup_unsupported_version, backup.version)
        }
        val displayMode = if (backup.version >= 3) requireNotNull(backup.displayMode) {
            appContext.getString(R.string.repo_backup_missing_display_mode)
        } else DisplayMode.fromStored(null, backup.blacklist)
        applyConfig(
            ModuleConfig(
                rules = backup.rules,
                mode = displayMode,
                priorities = if (backup.version == 1) PriorityConfig() else backup.priorities,
                diagnostic = mutableDiagnostic.value,
                hiddenFromApps = if (backup.version >= 5) backup.hiddenFromApps else emptySet(),
                openTypes = if (backup.version >= 7) backup.openTypes else OpenTypeConfig(),
                browserLinks = if (backup.version >= 10) backup.browserLinks else BrowserLinkConfig(),
                visibilityCompat = VisibilityCompatConfig(
                    scopes = if (backup.version >= 9) backup.visibilityScopes else emptySet()
                )
            ),
            backup.blacklist
        )
    }

    private fun applyConfig(config: ModuleConfig, legacyBlacklist: Boolean) {
        require(config.rules.size <= MAX_RULES) { appContext.getString(R.string.repo_backup_too_many_rules) }
        require(config.rules.all(ComponentRule::isValid)) { appContext.getString(R.string.repo_backup_invalid_component) }

        val canonicalRules = config.rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        val prepared = config.copy(
            rules = canonicalRules,
            priorities = config.priorities.validated(),
            hiddenFromApps = normalizeHiddenApps(config.hiddenFromApps),
            openTypes = config.openTypes.validated(),
            browserLinks = config.browserLinks.validated(),
            visibilityCompat = VisibilityCompatConfig(scopes = config.visibilityCompat.scopes.toSet()),
            managerAppId = -1,
            rootDisabledComponents = null,
        ).validated()

        persistPrepared(prepared)
        prefs.edit().putBoolean(KEY_BLACKLIST, legacyBlacklist).apply()

        mutableRules.value = prepared.rules
        mutableMode.value = prepared.mode
        mutablePriorities.value = prepared.priorities
        mutableOpenTypes.value = prepared.openTypes
        mutableBrowserLinks.value = prepared.browserLinks
        mutableDiagnostic.value = prepared.diagnostic
        mutableHiddenFromApps.value = prepared.hiddenFromApps
        mutableVisibilityScopes.value = prepared.visibilityCompat.scopes
        mutableVisibilityFullPackages.value = emptyMap()
        mutableRevision.value++
    }

    private fun normalizeHiddenApps(packages: Set<String>): Set<String> {
        val self = "com.yagay.ListCleaner"
        val valid = packages.asSequence()
            .map(String::trim)
            .filter { it != "android" && it != self && PackageIdentity.valid(it) }
            .take(MAX_HIDDEN_APPS + 1)
            .toSet()
        require(valid.size <= MAX_HIDDEN_APPS) { appContext.getString(R.string.repo_hidden_apps_too_many) }
        return valid
    }

    private fun sanitizeHiddenAppsForLoad(packages: Iterable<String>): Set<String> {
        val self = "com.yagay.ListCleaner"
        return packages.asSequence()
            .map(String::trim)
            .filter { it != "android" && it != self && PackageIdentity.valid(it) }
            .take(MAX_HIDDEN_APPS)
            .toSet()
    }

    private fun updateRules(next: Set<ComponentRule>) {
        require(next.size <= MAX_RULES) { appContext.getString(R.string.repo_too_many_rules) }
        if (next == mutableRules.value) return
        mutableRules.value = next
        persistLocalSnapshot()
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
        const val KEY_DEFAULT_OPEN = "default_open"
        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"
        const val KEY_OPEN_TYPES = "open_type_config"
        const val KEY_BROWSER_LINKS = "browser_link_config"
        const val KEY_VISIBILITY_SCOPES = "visibility_scopes"
        val SYNCED_KEYS = setOf(
            KEY_RULES, KEY_BLACKLIST, KEY_DISPLAY_MODE, KEY_PRIORITIES, KEY_DIAGNOSTIC,
            KEY_HIDDEN_FROM_APPS, KEY_OPEN_TYPES, KEY_BROWSER_LINKS, KEY_VISIBILITY_SCOPES
        )
        private const val LOCAL_PREFS = "rules_local"
        private const val KEY_LOCAL_CONFIG = "config_v2"
        private const val KEY_INITIALIZED = "configuration_initialized"
        private const val MAX_RULES = 20_000
        private const val MAX_HIDDEN_APPS = 2_000
        const val MAX_BACKUP_CHARS = 2_000_000
    }
}
