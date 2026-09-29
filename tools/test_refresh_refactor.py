from pathlib import Path


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def replace_between(path, start, end, new):
    text = read(path)
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"start marker not found in {path}: {start!r}")
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f"end marker not found in {path}: {end!r}")
    write(path, text[:i] + new + text[j:])


# Browser/App-Link discovery becomes an enrichment source instead of blocking base lists.
path = "app/src/main/java/com/yagay/ListCleaner/data/BrowserLinkDiscovery.kt"
replace_once(
    path,
    "    suspend fun discover(force: Boolean = false): Set<String> = discoverDetailed(force).hosts\n\n",
    "    suspend fun discover(force: Boolean = false): Set<String> = discoverDetailed(force).hosts\n\n"
    "    /** Last successful enrichment snapshot. Reading this never starts root/dumpsys work. */\n"
    "    fun snapshot(): BrowserLinkDiscoveryResult = cached\n\n",
)

# Catalog cache: successful empty scans are valid, and observed/special entries participate in the cache key.
path = "app/src/main/java/com/yagay/ListCleaner/data/IntentCatalog.kt"
replace_once(
    path,
    "    val candidates: StateFlow<List<ComponentCandidate>> = mutableCandidates.asStateFlow()\n    private val specialEntryDiscovery = SpecialEntryDiscovery(context)\n",
    "    val candidates: StateFlow<List<ComponentCandidate>> = mutableCandidates.asStateFlow()\n"
    "    private val mutableRevision = MutableStateFlow(0L)\n"
    "    val revision: StateFlow<Long> = mutableRevision.asStateFlow()\n"
    "    private val specialEntryDiscovery = SpecialEntryDiscovery(context)\n",
)
replace_once(
    path,
    "    fun invalidate(packageName: String? = null) {\n        invalidated = true\n",
    "    fun invalidate(packageName: String? = null) {\n        invalidated = true\n        mutableRevision.value = mutableRevision.value + 1L\n",
)
replace_between(
    path,
    "        val fingerprint = customDefinitions.entries\n",
    "        val previousCacheHits = cacheHitsSinceLastScan\n",
    '''        val specialCandidates = runCatching { specialEntryDiscovery.scan() }.getOrDefault(emptyList())
        val specialFingerprint = specialCandidates.asSequence()
            .map { it.rule.id }
            .sorted()
            .joinToString(",")
        val fingerprint = customDefinitions.entries
            .sortedBy { it.key.ordinal }
            .joinToString("|") { (preset, definition) -> "$preset=$definition" } +
            "|browserHosts=" + normalizedBrowserHosts.joinToString(",") +
            "|declaredHandlers=" + discoveryFingerprint +
            "|declaredPackages=" + packageFingerprint +
            "|specialEntries=" + specialFingerprint
        val cached = mutableCandidates.value
        if (!force && !invalidated && fingerprint == cachedDefinitionFingerprint) {
            cacheHitsSinceLastScan++
            return@withContext cached
        }

''',
)
replace_once(path, "        val specialCandidates = specialEntryDiscovery.scan()\n", "")

# Main ViewModel: shared rule context for priority page, auto-refresh invalidated catalogs,
# fast base scan first, heavy App-Link enrichment in a separate background job.
path = "app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt"
replace_once(
    path,
    "    private val filter = MutableStateFlow<IntentKind?>(null)\n",
    "    private val filter = MutableStateFlow<IntentKind?>(null)\n"
    "    private val ruleOpenPresetFilter = MutableStateFlow<OpenPreset?>(null)\n"
    "    val ruleOpenPreset: StateFlow<OpenPreset?> = ruleOpenPresetFilter\n"
    "    private val ruleBrowserHostFilter = MutableStateFlow<String?>(null)\n"
    "    val ruleBrowserHost: StateFlow<String?> = ruleBrowserHostFilter\n",
)
replace_once(
    path,
    "    private var refreshJob: Job? = null\n    private var refreshGeneration = 0L\n",
    "    private var refreshJob: Job? = null\n    private var browserRefreshJob: Job? = null\n    private var refreshGeneration = 0L\n",
)
replace_once(
    path,
    '''        }
    }

    fun setDiagnosticMode(enabled: Boolean) {''',
    '''        }
        viewModelScope.launch {
            var firstRevision = true
            app.catalog.revision.collectLatest {
                if (firstRevision) {
                    firstRevision = false
                    return@collectLatest
                }
                kotlinx.coroutines.delay(250)
                refresh()
            }
        }
    }

    fun setDiagnosticMode(enabled: Boolean) {''',
)
replace_between(
    path,
    "    fun refresh(forceCatalog: Boolean = false) {\n",
    "    fun refreshModuleStatus() = moduleRuntime.refresh()\n",
    '''    fun refresh(forceCatalog: Boolean = false) {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        browserRefreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val configured = app.rules.rules.value +
                    app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId) +
                    app.rules.browserLinks.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                candidates.value = app.catalog.completeConfigured(candidates.value, configured)

                // Never block the primary rule/priority list on root App-Link discovery. Reuse the
                // last enrichment snapshot for the fast scan, publish it immediately, then refresh
                // domains in a separate job and merge any richer result later.
                val cachedBrowserDiscovery = browserLinkDiscovery.snapshot()
                if (generation == refreshGeneration) {
                    discoveredBrowserHosts.value = cachedBrowserDiscovery.hosts
                }
                val baseResult = app.catalog.scan(
                    app.rules.openTypes.value.customDefinitions,
                    app.rules.browserLinks.value.hosts + cachedBrowserDiscovery.hosts,
                    browserDiscovery = cachedBrowserDiscovery,
                    force = forceCatalog
                )
                if (generation != refreshGeneration) return@launch
                val updatedConfigured = app.rules.rules.value +
                    app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId) +
                    app.rules.browserLinks.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                candidates.value = app.catalog.completeConfigured(baseResult, updatedConfigured)
                error.value = app.catalog.scanWarning
                loading.value = false

                browserRefreshJob = viewModelScope.launch {
                    try {
                        val browserDiscovery = browserLinkDiscovery.discoverDetailed(forceCatalog)
                        if (generation != refreshGeneration) return@launch
                        discoveredBrowserHosts.value = browserDiscovery.hosts
                        if (browserDiscovery != cachedBrowserDiscovery) {
                            val enriched = app.catalog.scan(
                                app.rules.openTypes.value.customDefinitions,
                                app.rules.browserLinks.value.hosts + browserDiscovery.hosts,
                                browserDiscovery = browserDiscovery,
                                force = true
                            )
                            if (generation == refreshGeneration) {
                                val latestConfigured = app.rules.rules.value +
                                    app.rules.openTypes.value.rules.values.flatten().mapNotNull(ComponentRule::fromId) +
                                    app.rules.browserLinks.value.rules.values.flatten().mapNotNull(ComponentRule::fromId)
                                candidates.value = app.catalog.completeConfigured(enriched, latestConfigured)
                                error.value = app.catalog.scanWarning
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Log.w(TAG, "Background App Link enrichment failed", failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Log.e(TAG, "Candidate scan failed", failure)
                if (generation == refreshGeneration) error.value = app.getString(R.string.scan_failed)
            } finally {
                if (generation == refreshGeneration) loading.value = false
            }
        }
        refreshModuleStatus()
    }

''',
)
replace_once(
    path,
    "    fun setFilter(value: IntentKind?) { filter.value = value }\n    fun setQuery(value: String) { query.value = value }\n    fun setUiFilter(value: UiFilter) { uiFilter.value = value }\n    fun setDestination(value: Destination) { destination.value = value }\n",
    '''    fun setFilter(value: IntentKind?) {
        filter.value = value
        if (value != IntentKind.OPEN) ruleOpenPresetFilter.value = null
        if (value != IntentKind.DEEP_LINK) ruleBrowserHostFilter.value = null
    }
    fun setRuleOpenPreset(value: OpenPreset?) { ruleOpenPresetFilter.value = value }
    fun setRuleBrowserHost(value: String?) { ruleBrowserHostFilter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setUiFilter(value: UiFilter) { uiFilter.value = value }
    fun setDestination(value: Destination) {
        if (destination.value != value) {
            query.value = ""
            expandedAppKey.value = null
        }
        destination.value = value
    }
''',
)

# Rules page and priority page share kind + OPEN preset + Deep Link host context.
path = "app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt"
replace_once(
    path,
    "    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }\n    var browserHost by rememberSaveable { mutableStateOf<String?>(null) }\n",
    "    val openPreset by vm.ruleOpenPreset.collectAsState()\n    val browserHost by vm.ruleBrowserHost.collectAsState()\n",
)
replace_once(path, "        if (state.filter != IntentKind.OPEN) openPreset = null\n", "        if (state.filter != IntentKind.OPEN) vm.setRuleOpenPreset(null)\n")
replace_once(path, "        if (state.filter != IntentKind.DEEP_LINK) browserHost = null\n", "        if (state.filter != IntentKind.DEEP_LINK) vm.setRuleBrowserHost(null)\n")
replace_once(path, "        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) openPreset = null\n", "        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) vm.setRuleOpenPreset(null)\n")
replace_once(path, "        if (browserHost != null && browserHost !in state.browserAvailableHosts) browserHost = null\n", "        if (browserHost != null && browserHost !in state.browserAvailableHosts) vm.setRuleBrowserHost(null)\n")
replace_once(path, "                                    onSelected = { openPreset = it },\n", "                                    onSelected = vm::setRuleOpenPreset,\n")
replace_once(path, "                                    onSelected = { browserHost = it },\n", "                                    onSelected = vm::setRuleBrowserHost,\n")
replace_once(
    path,
    "        Text(\n            stringResource(R.string.rules_page_intro),\n",
    "        Text(\n            stringResource(R.string.rules_scan_summary, state.candidates.count { it.isCatalogCandidate }, groupCount),\n            style = MaterialTheme.typography.labelSmall,\n            color = MaterialTheme.colorScheme.onSurfaceVariant\n        )\n        Text(\n            stringResource(R.string.rules_page_intro),\n",
)
replace_once(
    path,
    "                    Text(stringResource(R.string.no_matching_components))\n",
    '''                    Text(
                        stringResource(
                            if (state.filter == IntentKind.DIRECT_SHARE || state.filter == IntentKind.SHORTCUT_ITEM)
                                R.string.observed_entry_empty_help
                            else R.string.no_matching_components
                        )
                    )
''',
)
replace_once(path, "                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }\n", "")

path = "app/src/main/java/com/yagay/ListCleaner/ui/PriorityDialog.kt"
replace_once(
    path,
    "    var kind by rememberSaveable { mutableStateOf(state.filter ?: IntentKind.SHARE) }\n    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }\n    var browserHost by rememberSaveable { mutableStateOf<String?>(null) }\n",
    "    val kind = state.filter ?: IntentKind.SHARE\n    val openPreset by vm.ruleOpenPreset.collectAsState()\n    val browserHost by vm.ruleBrowserHost.collectAsState()\n",
)
replace_once(path, "        if (kind != IntentKind.OPEN) openPreset = null\n", "        if (kind != IntentKind.OPEN) vm.setRuleOpenPreset(null)\n")
replace_once(path, "        if (kind != IntentKind.DEEP_LINK) browserHost = null\n", "        if (kind != IntentKind.DEEP_LINK) vm.setRuleBrowserHost(null)\n")
replace_once(path, "        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) openPreset = null\n", "        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) vm.setRuleOpenPreset(null)\n")
replace_once(path, "        if (browserHost != null && browserHost !in state.browserAvailableHosts) browserHost = null\n", "        if (browserHost != null && browserHost !in state.browserAvailableHosts) vm.setRuleBrowserHost(null)\n")
replace_once(path, "                            onFilter = { entry -> if (entry != null) { kind = entry; expandedKey = null } },\n", "                            onFilter = { entry -> if (entry != null) { vm.setFilter(entry); expandedKey = null } },\n")
replace_once(path, "                                        onSelected = { openPreset = it },\n", "                                        onSelected = vm::setRuleOpenPreset,\n")
replace_once(path, "                                        onSelected = { browserHost = it },\n", "                                        onSelected = vm::setRuleBrowserHost,\n")
replace_once(
    path,
    "                    Text(\n                        stringResource(R.string.priority_intro),\n",
    "                    Text(\n                        stringResource(R.string.priority_rule_scope_summary, visibleSaved.size, groups.size),\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant\n                    )\n                    Text(\n                        stringResource(R.string.priority_intro),\n",
)

# Root component refresh: show local scan first, do not drop a refresh pressed while busy,
# and only rescan after runtime sync when discovery protocol actually changes.
path = "app/src/main/java/com/yagay/ListCleaner/ui/RootComponentsController.kt"
replace_once(
    path,
    "    private val mutableRootNotice = MutableStateFlow<String?>(null)\n    val rootNotice: StateFlow<String?> = mutableRootNotice\n",
    "    private val mutableRootNotice = MutableStateFlow<String?>(null)\n    val rootNotice: StateFlow<String?> = mutableRootNotice\n    private var refreshPending = false\n",
)
replace_between(
    path,
    "    fun refresh() {\n",
    "    fun change(target: RootComponent, enable: Boolean) = change(listOf(target), enable)\n",
    '''    fun refresh() {
        if (mutableBusy.value) {
            refreshPending = true
            return
        }
        mutableBusy.value = true
        scope.launch {
            try {
                val protocolBefore = app.runtime.value.componentDiscoveryProtocol
                // Component discovery is local PackageManager/LauncherApps work. Publish it first;
                // LSPosed synchronization is enrichment and must not block the visible list.
                mutableScan.value = withContext(Dispatchers.IO) { catalog.scan() }
                try {
                    withContext(Dispatchers.IO) {
                        persistentComponents.syncRemote()
                        app.synchronize()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Root runtime synchronization failed after local scan", failure)
                }
                val protocolAfter = app.runtime.value.componentDiscoveryProtocol
                if (protocolAfter != protocolBefore) {
                    mutableScan.value = withContext(Dispatchers.IO) { catalog.scan() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Root component scan failed", failure)
                mutableMessage.value = app.getString(R.string.root_scan_failed)
            } finally {
                mutableBusy.value = false
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

''',
)

path = "app/src/main/java/com/yagay/ListCleaner/ui/RootComponentsScreen.kt"
replace_once(
    path,
    "                Text(stringResource(R.string.root_summary, groups.size, visible.size), style = MaterialTheme.typography.labelLarge)\n",
    "                Text(stringResource(R.string.root_summary, groups.size, visible.size), style = MaterialTheme.typography.labelLarge)\n"
    "                if (scan.items.size != visible.size) {\n"
    "                    Text(stringResource(R.string.root_scan_total, scan.items.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n"
    "                }\n",
)

# Top refresh now has page-specific meaning.
path = "app/src/main/java/com/yagay/ListCleaner/ui/MainActivity.kt"
replace_once(
    path,
    "                    { if (state.destination == Destination.TILES) vm.refreshComponents() else vm.refresh(forceCatalog = true) },\n",
    '''                    {
                        when (state.destination) {
                            Destination.RULES, Destination.PRIORITY -> vm.refresh(forceCatalog = true)
                            Destination.TILES -> vm.refreshComponents()
                            Destination.DASHBOARD -> vm.refreshModuleStatus()
                        }
                    },
''',
)

# Clearer scan-vs-filter feedback and observed-entry guidance.
additions_by_file = {
    "app/src/main/res/values/strings_ui.xml": '''
    <string name="rules_scan_summary">Scanned %1$d entries · currently showing %2$d apps</string>
    <string name="priority_rule_scope_summary">Rule-visible scope: %1$d apps · currently showing %2$d</string>
    <string name="root_scan_total">Scanned %1$d components before filters</string>
    <string name="observed_entry_empty_help">No observed entries yet. Use the target app\'s share panel or launcher shortcuts first, then refresh.</string>
''',
    "app/src/main/res/values-zh/strings_ui.xml": '''
    <string name="rules_scan_summary">已扫描 %1$d 个入口 · 当前显示 %2$d 个应用</string>
    <string name="priority_rule_scope_summary">按规则可排序 %1$d 个应用 · 当前显示 %2$d 个</string>
    <string name="root_scan_total">筛选前已扫描 %1$d 个组件</string>
    <string name="observed_entry_empty_help">暂未观察到相关入口。请先使用目标应用的分享面板或桌面长按快捷方式，再返回刷新。</string>
''',
}
for path, additions in additions_by_file.items():
    text = read(path)
    if 'name="rules_scan_summary"' not in text:
        text = text.replace("</resources>", additions + "</resources>")
        write(path, text)

print("refactor applied")
