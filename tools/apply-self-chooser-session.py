from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace(
'''    private val packageAccessorCache = ConcurrentHashMap<Class<*>, PackageNameAccessor>()
    private val recentAppQueryKinds = ConcurrentHashMap<String, IntentKind>()
''',
'''    private val packageAccessorCache = ConcurrentHashMap<Class<*>, PackageNameAccessor>()
    private val recentAppQueryKinds = ConcurrentHashMap<String, IntentKind>()
    private data class SelfChooserSession(val kind: IntentKind, val startedAt: Long, val component: String)
    private val recentSelfChooserSessions = ConcurrentHashMap<String, SelfChooserSession>()
''',
1)

s = s.replace(
'''            pollPreferences()
            val kind = recentAppQueryKinds[packageName]
            if (kind != null) filterAdapterMenuCollections(adapter, packageName, kind)
            val summaries = inspectAdapterCollections(adapter)
            diagnostic("APP_MENU_CONSUMER package=$packageName stage=$stage adapter=${adapter.javaClass.name} kind=${kind ?: "-"} collections=${summaries.size} data=[${summaries.joinToString(" | ")}]")
''',
'''            pollPreferences()
            val now = SystemClock.elapsedRealtime()
            val selfSession = recentSelfChooserSessions[packageName]?.takeIf { now - it.startedAt <= SELF_CHOOSER_SESSION_TTL_MS }
            val kind = selfSession?.kind ?: recentAppQueryKinds[packageName]
            val source = if (selfSession != null) "self_chooser" else if (kind != null) "pm_query" else "none"
            if (kind != null) filterAdapterMenuCollections(adapter, packageName, kind)
            val summaries = inspectAdapterCollections(adapter)
            diagnostic("APP_MENU_CONSUMER package=$packageName stage=$stage adapter=${adapter.javaClass.name} kind=${kind ?: "-"} source=$source component=${selfSession?.component ?: "-"} collections=${summaries.size} data=[${summaries.joinToString(" | ")}]")
''',
1)

old = '''                val nested = runCatching {
                    intent.extras?.keySet().orEmpty().mapNotNull { key ->
                        @Suppress("DEPRECATION")
                        val value = intent.extras?.get(key)
                        (value as? Intent)?.let { child ->
                            "$key:${child.action ?: "-"}:${child.component?.flattenToShortString() ?: "-"}:${child.type ?: "-"}:${child.data?.scheme ?: "-"}"
                        }
                    }.joinToString(";")
                }.getOrNull().orEmpty()
'''
new = '''                val nestedIntents = runCatching {
                    intent.extras?.keySet().orEmpty().mapNotNull { key ->
                        @Suppress("DEPRECATION")
                        (intent.extras?.get(key) as? Intent)?.let { key to it }
                    }
                }.getOrDefault(emptyList())
                val nested = nestedIntents.joinToString(";") { (key, child) ->
                    "$key:${child.action ?: "-"}:${child.component?.flattenToShortString() ?: "-"}:${child.type ?: "-"}:${child.data?.scheme ?: "-"}"
                }
                val ownComponent = intent.component?.takeIf { it.packageName == packageName }
                if (ownComponent != null) {
                    val child = nestedIntents.asSequence().map { it.second }.firstOrNull { it.intentKind(null) != null }
                    val childKind = child?.intentKind(null)
                    if (childKind != null) {
                        recentSelfChooserSessions[packageName] = SelfChooserSession(
                            childKind,
                            SystemClock.elapsedRealtime(),
                            ownComponent.flattenToShortString(),
                        )
                        diagnostic(
                            "APP_SELF_CHOOSER_SESSION package=$packageName stage=$stage component=${ownComponent.flattenToShortString()} " +
                                "kind=$childKind action=${child.action ?: "-"} type=${child.type ?: "-"} scheme=${child.data?.scheme ?: "-"}"
                        )
                    }
                }
'''
if old not in s:
    raise SystemExit('app start nested block not found')
s = s.replace(old, new, 1)

s = s.replace(
'''        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"
        const val CHOOSER_DISCOVERY_WINDOW_MS = 3_000L
''',
'''        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"
        const val SELF_CHOOSER_SESSION_TTL_MS = 8_000L
        const val CHOOSER_DISCOVERY_WINDOW_MS = 3_000L
''',
1)

for required in ['APP_SELF_CHOOSER_SESSION', 'source=$source', 'SELF_CHOOSER_SESSION_TTL_MS', 'recentSelfChooserSessions']:
    if required not in s:
        raise SystemExit('missing ' + required)

p.write_text(s)
print('patched self chooser session correlation')
