package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.Context
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import android.os.Binder
import android.app.Activity
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.prioritizeApps
import com.yagay.ListCleaner.domain.selectedKinds
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.intentKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import kotlinx.serialization.json.Json
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Level 1 filters PackageManager resolver results. Level 2 runs only in system_server and adds
 * caller-aware Android package-visibility filtering for apps selected in the hidden-app list.
 * Third-party apps do not need LSPosed scope. Real package/component state is never changed.
 */
class ListCleanerModule : XposedModule() {
    private data class RuleSnapshot(
        val configured: Set<String>,
        val displayMode: DisplayMode,
        val priorities: PriorityConfig,
        val diagnostic: Boolean,
        val managerAppId: Int = -1,
        val digest: String = "",
        val hiddenFromApps: Set<String> = emptySet(),
        val visibilityHiddenTargets: Set<String> = emptySet()
    ) {
        val selectedKinds: Set<IntentKind> = selectedKinds(configured)
        private val selectedPackages: Map<IntentKind, Set<String>> = configured.mapNotNull { id ->
            val parts = id.split('|', limit = 3)
            val kind = parts.getOrNull(0)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            val packageName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            kind to packageName
        }.groupBy({ it.first }, { it.second }).mapValues { (_, packages) -> packages.toSet() }
        val allSelectedPackages: Set<String> = selectedPackages.values.flatten().toSet()
        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
        fun hasPackageSelection(kind: IntentKind, packageName: String): Boolean = packageName in selectedPackages[kind].orEmpty()
    }

    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)

    @Volatile
    private var snapshot = RuleSnapshot(emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false)
    private var lastEncodedConfig: String? = null
    @Volatile
    private var listenerRegistered = false
    private var processName = ""
    private var systemServer = false
    private var diagnosticWindow = 0L
    private var diagnosticCount = 0
    private var suppressedCount = 0L
    private var detailCount = 0
    private var suppressedDetails = 0L
    @Volatile private var nextPreferencePoll = 0L
    private val queryInProgress = ThreadLocal<Boolean>()
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val tracedActions = ConcurrentHashMap.newKeySet<String>()
    private data class CallerCacheEntry(val packages: Set<String>, val expiresAt: Long)
    private val callerPackageCache = ConcurrentHashMap<Int, CallerCacheEntry>()
    private val packageAccessorCache = ConcurrentHashMap<Class<*>, PackageNameAccessor>()
    private val recentAppQueryKinds = ConcurrentHashMap<String, IntentKind>()
    @Volatile private var visibilityFailureCount = 0
    @Volatile private var visibilityFailOpen = false
    @Volatile private var appGlobalsPackageManager: Any? = null
    @Volatile private var appGlobalsGetPackagesForUid: Method? = null

    private data class RecentChooserLaunch(
        val uid: Int,
        val callerPackage: String,
        val component: String,
        val startedAt: Long,
        val hasPayloadHint: Boolean,
    )
    private val recentChooserLaunches = ConcurrentHashMap<Int, RecentChooserLaunch>()
    private val learnedChooserHits = ConcurrentHashMap<String, Int>()
    private data class LearnedChooserTemplate(val kind: IntentKind, val action: String?, val mime: String?, val flags: Int)
    private val learnedChooserTemplates = ConcurrentHashMap<String, LearnedChooserTemplate>()
    private val chooserRequestSchemasLogged = ConcurrentHashMap.newKeySet<Class<*>>()
    private val chooserRequestAccessorCache = ConcurrentHashMap<Class<*>, ActivityStartAccessor>()

    private data class ActivityStartAccessor(
        val intentField: Field,
        val uidField: Field?,
        val packageField: Field?,
        val source: String,
    )

    private data class ActivityStartView(
        val intent: Intent,
        val uid: Int,
        val callerPackage: String,
        val source: String,
    )
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG ||
            (key in RuleRepository.SYNCED_KEYS && !preferences.contains(RuleRepository.KEY_CONFIG))) {
            refreshRulesSafely("preference changed")
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        systemServer = param.isSystemServer
        record("MODULE_LOADED version=${BuildConfig.VERSION_CODE} systemServer=$systemServer frameworkLog=direct-api")
    }

    @Synchronized override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (listenerRegistered) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            listenerRegistered = false
        }
        record("HOT_RELOAD_RETIRING version=${BuildConfig.VERSION_CODE}")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        processName = param.processName
        systemServer = param.isSystemServer
        val loaders = linkedSetOf<ClassLoader>()
        try {
            val reloadBaseProcess = processName.substringBefore(':')
            val expectedQueryHookId = when {
                systemServer -> "$HOOK_ID-system"
                reloadBaseProcess == FRAMEWORK_PACKAGE || reloadBaseProcess == INTENT_RESOLVER_PACKAGE -> "$HOOK_ID-resolver"
                reloadBaseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
            }
            if (expectedQueryHookId != null) {
                check(param.oldHookHandles.any { handle ->
                    val method = handle.executable as? Method
                    handle.id == expectedQueryHookId && method != null && isQueryIntentActivitiesMethod(method)
                }) { "No compatible query handle; restart required" }
            }
            initializePreferences()
            param.oldHookHandles.forEach { handle ->
                val method = handle.executable as? Method
                method?.declaringClass?.classLoader?.let(loaders::add)
                val layer = when (handle.id) {
                    "$HOOK_ID-system" -> Layer.SYSTEM
                    "$HOOK_ID-resolver" -> Layer.RESOLVER
                    else -> null
                }
                when {
                    method != null && layer != null && isQueryIntentActivitiesMethod(method) -> {
                        handle.replaceHook(queryHooker(layer))
                        installedMethods.add("${method.declaringClass.name}#${method.toGenericString()}@$layer")
                    }
                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {
                        val adapter = VisibilityMethodAdapter.create(method)
                        if (adapter != null) {
                            handle.replaceHook(systemVisibilityHooker(adapter))
                            installedMethods.add("VISIBILITY#${method.toGenericString()}")
                        } else handle.unhook()
                    }
                    method != null && handle.id == ARCHIVED_VISIBILITY_HOOK_ID && method.name == "getArchivedPackageInternal" -> {
                        handle.replaceHook(archivedPackageHooker())
                        installedMethods.add("ARCHIVED_VISIBILITY#${method.toGenericString()}")
                    }
                    method != null && handle.id == "ic-final-order" -> {
                        handle.replaceHook(orderHooker())
                        installedMethods.add("ORDER#${method.toGenericString()}")
                    }
                    method != null && handle.id == "ic-alpha-order" -> {
                        handle.replaceHook(alphabeticalOrderHooker())
                        installedMethods.add("ALPHA#${method.toGenericString()}")
                    }
                    else -> handle.unhook()
                }
            }
            if (!systemServer) runCatching {
                val application = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null) as? Context
                application?.classLoader?.let(loaders::add)
            }.onFailure { record("ORDER_LOADER_UNAVAILABLE error=${it.javaClass.name}") }
            val baseProcess = processName.substringBefore(':')
            val expectedLayer = when {
                systemServer -> Layer.SYSTEM
                baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE -> Layer.RESOLVER
                baseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
            }
            loaders.forEach {
                when (expectedLayer) {
                    Layer.SYSTEM -> installSystemServerQueryHooks(it)
                    Layer.RESOLVER -> installResolverClientHooks(it)
                    Layer.APP -> Unit
                    null -> record("HOT_RELOAD_SKIP package=$baseProcess reason=system_scope_pseudo_process")
                }
            }
            if (expectedLayer != null) {
                check(installedMethods.any { it.endsWith("@$expectedLayer") }) {
                    "No query hooks after reload; restart required"
                }
            }
            record("HOT_RELOAD_READY version=${BuildConfig.VERSION_CODE} hooks=${installedMethods.size}")
            if (!systemServer && !processName.startsWith(SYSTEM_UI_PACKAGE)) recordOrderingCapability()
        } catch (failure: Throwable) {
            record("HOT_RELOAD_FAILED version=${BuildConfig.VERSION_CODE} error=${failure.javaClass.name}")
            throw failure
        }
    }

    @Synchronized private fun pollPreferences() {
        val now = SystemClock.elapsedRealtime()
        if (now < nextPreferencePoll) return
        nextPreferencePoll = now + 2_000
        refreshRulesSafely("query poll")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        systemServer = true
        record("SYSTEM_SERVER_STARTING loader=${param.classLoader.javaClass.name}")
        initializePreferences()
        installSystemServerQueryHooks(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        record("PACKAGE_READY package=${param.packageName} loader=${param.classLoader.javaClass.name}")
        if (systemServer) {
            if (!listenerRegistered) initializePreferences()
            return
        }
        initializePreferences()
        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {
            installResolverClientHooks(param.classLoader)
        } else if (param.packageName == SYSTEM_SCOPE_PACKAGE) {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=system_scope_pseudo_process")
        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
            installAppPmQueryProbe(param.classLoader, param.packageName)
            installAppMenuConsumerProbe(param.classLoader, param.packageName)
        } else {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=third_party_not_source")
        }
    }

    private fun installAppPmQueryProbe(classLoader: ClassLoader, packageName: String) {
        val className = "android.content.pm.IPackageManager\$Stub\$Proxy"
        val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
            record("APP_PM_QUERY_PROBE_CLASS_UNAVAILABLE package=$packageName class=$className error=${it.javaClass.name}")
            return
        }
        var installed = 0
        val methods = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter(::isQueryIntentActivitiesMethod)
            .distinctBy(Method::toGenericString)
            .toList()
        methods.forEach { method ->
            val key = "APP_PM_QUERY_PROBE#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId(APP_PM_QUERY_FILTER_HOOK_ID).intercept(appPmQueryFilterHooker(packageName))
                installed++
                record("APP_PM_QUERY_FILTER_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
            }.onFailure {
                installedMethods.remove(key)
                record("APP_PM_QUERY_PROBE_HOOK_FAILED package=$packageName method=${method.toGenericString()} error=${it.javaClass.name}")
            }
        }
        record("APP_PM_QUERY_FILTER_READY package=$packageName hooks=$installed")
    }

    private fun appPmQueryFilterHooker(packageName: String) = XposedInterface.Hooker { chain ->
        val outerIntent = chain.args.firstOrNull { it is Intent } as? Intent
        val intent = outerIntent?.selector ?: outerIntent
        val original = chain.proceed()
        if (intent == null) return@Hooker original
        runCatching {
            pollPreferences()
            val explicit = intent.component != null || intent.`package` != null ||
                outerIntent?.component != null || outerIntent?.`package` != null
            val kind = intent.intentKind(null)
            if (!explicit && kind != null) recentAppQueryKinds[packageName] = kind
            if (explicit || kind == null) {
                diagnostic("APP_PM_QUERY_FILTER_SKIP package=$packageName action=${intent.action ?: "-"} explicit=$explicit kind=${kind ?: "-"}")
                return@runCatching original
            }
            val extracted = extractListResult(original) ?: run {
                diagnostic("APP_PM_QUERY_FILTER_SKIP package=$packageName action=${intent.action ?: "-"} reason=unsupported_result result=${original?.javaClass?.name ?: "null"}")
                return@runCatching original
            }
            val callerUid = Process.myUid()
            val replacement = transform(kind, extracted.values, Layer.APP, callerUid)
            if (replacement == null) {
                diagnostic("APP_PM_QUERY_FILTER_NO_CHANGE package=$packageName kind=$kind size=${extracted.values.size}")
                return@runCatching original
            }
            val rebuilt = extracted.rebuild(replacement)
            diagnostic("APP_PM_QUERY_FILTER_APPLIED package=$packageName kind=$kind before=${extracted.values.size} after=${replacement.size} callerUid=$callerUid")
            rebuilt
        }.getOrElse {
            diagnostic("APP_PM_QUERY_FILTER_FAILED package=$packageName error=${it.javaClass.name}; keeping original")
            original
        }
    }

    private fun installAppMenuConsumerProbe(classLoader: ClassLoader, packageName: String) {
        var installed = 0
        val targets = listOf(
            "android.widget.ListView" to "setAdapter",
            "android.widget.GridView" to "setAdapter",
            "androidx.recyclerview.widget.RecyclerView" to "setAdapter",
        )
        targets.forEach { (className, methodName) ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("APP_MENU_CONSUMER_CLASS_UNAVAILABLE package=$packageName class=$className")
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { method -> method.name == methodName && method.parameterTypes.size == 1 }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "APP_MENU_CONSUMER#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(APP_MENU_CONSUMER_HOOK_ID)
                            .intercept(appMenuConsumerProbeHooker(packageName, className))
                        installed++
                        record("APP_MENU_CONSUMER_HOOK_INSTALLED package=$packageName class=$className method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("APP_MENU_CONSUMER_HOOK_FAILED package=$packageName class=$className error=${it.javaClass.name}")
                    }
                }
        }
        record("APP_MENU_CONSUMER_READY package=$packageName hooks=$installed")
    }

    private fun appMenuConsumerProbeHooker(packageName: String, stage: String) = XposedInterface.Hooker { chain ->
        val adapter = chain.args.firstOrNull()
        if (adapter != null) runCatching {
            pollPreferences()
            val kind = recentAppQueryKinds[packageName]
            if (kind != null) filterAdapterMenuCollections(adapter, packageName, kind)
            val summaries = inspectAdapterCollections(adapter)
            diagnostic("APP_MENU_CONSUMER package=$packageName stage=$stage adapter=${adapter.javaClass.name} kind=${kind ?: "-"} collections=${summaries.size} data=[${summaries.joinToString(" | ")}]")
        }.onFailure { diagnostic("APP_MENU_CONSUMER_FAILED package=$packageName stage=$stage adapter=${adapter.javaClass.name} error=${it.javaClass.name}") }
        chain.proceed()
    }

    private data class MenuModelComponent(val packageName: String, val className: String)

    private fun filterAdapterMenuCollections(adapter: Any, sourcePackage: String, kind: IntentKind) {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        var touched = 0
        var removed = 0
        allInstanceFields(adapter.javaClass).asSequence().filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.take(APP_MENU_MAX_FIELDS).forEach { field ->
            val value = runCatching { field.isAccessible = true; field.get(adapter) }.getOrNull() ?: return@forEach
            @Suppress("UNCHECKED_CAST") val items = value as? MutableCollection<Any?> ?: return@forEach
            if (items.isEmpty() || !seen.add(items)) return@forEach
            val recognized = items.mapNotNull(::extractMenuModelComponent)
            if (recognized.isEmpty()) return@forEach
            touched++
            val before = items.size
            val remove = items.filter { item ->
                val c = extractMenuModelComponent(item) ?: return@filter false
                val cls = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(c.packageName, c.className, null)
                val selected = "${kind.name}|${c.packageName}|$cls" in snapshot.configured
                !snapshot.displayMode.includes(selected, snapshot.hasSelection(kind))
            }
            if (remove.isEmpty()) return@forEach
            if (FilterPolicy.restoreEmpty(kind.name, before, before-remove.size)) return@forEach
            if (runCatching { items.removeAll(remove.toSet()) }.getOrDefault(false)) {
                removed += before-items.size
                diagnostic("APP_MENU_CONSUMER_FILTER_APPLIED package=$sourcePackage kind=$kind adapter=${adapter.javaClass.name} field=${field.name} before=$before after=${items.size} recognized=${recognized.size}")
            }
        }
        if (touched>0 && removed==0) diagnostic("APP_MENU_CONSUMER_FILTER_NO_CHANGE package=$sourcePackage kind=$kind adapter=${adapter.javaClass.name} collections=$touched")
    }

    private fun extractMenuModelComponent(value: Any?): MenuModelComponent? {
        value ?: return null
        when (value) {
            is ResolveInfo -> return value.activityInfo?.let { MenuModelComponent(it.packageName,it.name) }
            is ActivityInfo -> return MenuModelComponent(value.packageName,value.name)
            is ComponentName -> return MenuModelComponent(value.packageName,value.className)
        }
        val strings=mutableListOf<String>()
        allInstanceFields(value.javaClass).asSequence().filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.take(APP_MENU_MAX_MODEL_FIELDS).forEach { f ->
            runCatching { f.isAccessible=true; f.get(value) as? String }.getOrNull()?.let(strings::add)
        }
        if (strings.none { it.startsWith("android.intent.action.") }) return null
        val vals=strings.filterNot { it.startsWith("android.intent.action.") }
        val cls=vals.firstOrNull { it.startsWith(".") || it.substringAfterLast('.').any(Char::isUpperCase) } ?: return null
        val pkg=vals.firstOrNull { it!=cls && looksLikePackageName(it) && it.substringAfterLast('.').all { ch -> ch.isLowerCase() || ch.isDigit() || ch=='_' } }
            ?: vals.firstOrNull { it!=cls && looksLikePackageName(it) }
        return pkg?.let { MenuModelComponent(it,cls) }
    }

    private fun inspectAdapterCollections(adapter: Any): List<String> {
        val summaries = mutableListOf<String>()
        allInstanceFields(adapter.javaClass).asSequence()
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .take(APP_MENU_MAX_FIELDS)
            .forEach { field ->
                if (summaries.size >= APP_MENU_MAX_COLLECTIONS) return@forEach
                val value = runCatching {
                    field.isAccessible = true
                    field.get(adapter)
                }.getOrNull() ?: return@forEach
                when (value) {
                    is Collection<*> -> summarizeMenuCollection(field.name, value)?.let(summaries::add)
                    is Map<*, *> -> summarizeMenuCollection(field.name + ".values", value.values)?.let(summaries::add)
                    is Array<*> -> summarizeMenuCollection(field.name, value.asList())?.let(summaries::add)
                }
            }
        return summaries
    }

    private fun summarizeMenuCollection(fieldName: String, values: Collection<*>): String? {
        if (values.isEmpty()) return null
        val samples = values.asSequence().take(APP_MENU_MAX_ITEMS).mapNotNull(::describeMenuValue).toList()
        if (samples.isEmpty()) return null
        val itemTypes = values.asSequence().take(APP_MENU_MAX_ITEMS).mapNotNull { it?.javaClass?.name }.distinct().take(4).joinToString(",")
        return "$fieldName(size=${values.size},types=[$itemTypes],samples=[${samples.joinToString(",")}])"
    }

    private fun describeMenuValue(value: Any?): String? {
        value ?: return null
        return when (value) {
            is ResolveInfo -> value.activityInfo?.let { "ResolveInfo:${it.packageName}/${it.name}" }
            is ActivityInfo -> "ActivityInfo:${value.packageName}/${value.name}"
            is ComponentName -> "Component:${value.flattenToShortString()}"
            is Intent -> "Intent:${value.action ?: "-"}:${value.component?.flattenToShortString() ?: "-"}:${value.`package` ?: "-"}:${value.type ?: "-"}:${value.data?.scheme ?: "-"}"
            is String -> value.takeIf(::looksLikePackageName)?.let { "String:$it" }
            else -> describeMenuModel(value)
        }
    }

    private fun describeMenuModel(value: Any): String? {
        val hits = mutableListOf<String>()
        allInstanceFields(value.javaClass).asSequence()
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .take(APP_MENU_MAX_MODEL_FIELDS)
            .forEach { field ->
                if (hits.size >= APP_MENU_MAX_MODEL_HITS) return@forEach
                val nested = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull() ?: return@forEach
                val hit = when (nested) {
                    is ResolveInfo -> nested.activityInfo?.let { "${field.name}=RI:${it.packageName}/${it.name}" }
                    is ActivityInfo -> "${field.name}=AI:${nested.packageName}/${nested.name}"
                    is ComponentName -> "${field.name}=CN:${nested.flattenToShortString()}"
                    is Intent -> "${field.name}=I:${nested.action ?: "-"}:${nested.component?.flattenToShortString() ?: "-"}:${nested.`package` ?: "-"}"
                    is String -> nested.takeIf(::looksLikePackageName)?.let { "${field.name}=S:$it" }
                    else -> null
                }
                if (hit != null) hits += hit
            }
        return hits.takeIf { it.isNotEmpty() }?.let { "${value.javaClass.name}{${it.joinToString(";")}}" }
    }

    private fun installAppStartProbe(classLoader: ClassLoader, packageName: String) {
        var installed = 0
        val targets = listOf(
            "android.app.Instrumentation" to setOf("execStartActivity"),
            "android.app.Activity" to setOf("startActivityForResult"),
            "android.app.ContextImpl" to setOf("startActivity"),
        )
        targets.forEach { (className, names) ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("APP_START_PROBE_CLASS_UNAVAILABLE package=$packageName class=$className error=${it.javaClass.name}")
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { it.name in names && it.parameterTypes.any(Intent::class.java::isAssignableFrom) }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "APP_START_PROBE#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(APP_START_PROBE_HOOK_ID).intercept(appStartProbeHooker(packageName, className))
                        installed++
                        record("APP_START_PROBE_HOOK_INSTALLED package=$packageName class=$className method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("APP_START_PROBE_HOOK_FAILED package=$packageName class=$className method=${method.toGenericString()} error=${it.javaClass.name}")
                    }
                }
        }
        record("APP_START_PROBE_READY package=$packageName hooks=$installed")
    }

    private fun appStartProbeHooker(packageName: String, stage: String) = XposedInterface.Hooker { chain ->
        runCatching {
            val intent = chain.args.firstOrNull { it is Intent } as? Intent
            if (intent != null) {
                val data = intent.data
                val extras = runCatching { intent.extras?.keySet()?.sorted()?.joinToString(",") }.getOrNull().orEmpty()
                val nested = runCatching {
                    intent.extras?.keySet().orEmpty().mapNotNull { key ->
                        @Suppress("DEPRECATION")
                        val value = intent.extras?.get(key)
                        (value as? Intent)?.let { child ->
                            "$key:${child.action ?: "-"}:${child.component?.flattenToShortString() ?: "-"}:${child.type ?: "-"}:${child.data?.scheme ?: "-"}"
                        }
                    }.joinToString(";")
                }.getOrNull().orEmpty()
                diagnostic(
                    "APP_START_PROBE package=$packageName stage=$stage action=${intent.action ?: "-"} " +
                        "component=${intent.component?.flattenToShortString() ?: "-"} pkg=${intent.`package` ?: "-"} " +
                        "type=${intent.type ?: "-"} dataScheme=${data?.scheme ?: "-"} dataAuthority=${data?.authority ?: "-"} " +
                        "selectorAction=${intent.selector?.action ?: "-"} selectorComponent=${intent.selector?.component?.flattenToShortString() ?: "-"} " +
                        "clip=${intent.clipData?.itemCount ?: 0} flags=0x${intent.flags.toString(16)} extras=[$extras] nested=[$nested]"
                )
            }
        }.onFailure { diagnostic("APP_START_PROBE_FAILED package=$packageName stage=$stage error=${it.javaClass.name}") }
        chain.proceed()
    }

    private fun installSystemServerQueryHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_QUERY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            runCatching { clazz.methods.filter(::isQueryIntentActivitiesMethod) }.getOrElse {
                record("METHOD_DISCOVERY_FAILED class=$className error=${it.javaClass.name}")
                emptyList()
            }.forEach { method ->
                if (installHook(method, Layer.SYSTEM)) installed++
            }
        }
        record("SYSTEM_HOOKS new=$installed total=${installedMethods.size}")
        installSystemVisibilityHooks(classLoader)
        installAdaptiveChooserDiscoveryHooks(classLoader)
    }

    private fun installAdaptiveChooserDiscoveryHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName("com.android.server.wm.ActivityStarter", false, classLoader)
        }.getOrElse {
            record("CHOOSER_DISCOVERY_CLASS_UNAVAILABLE class=com.android.server.wm.ActivityStarter error=${it.javaClass.name}")
            return
        }
        var installed = 0
        val methods = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { method -> method.name == "executeRequest" && method.parameterTypes.isNotEmpty() }
            .distinctBy(Method::toGenericString)
            .toList()
        methods.forEach { method ->
            val key = "CHOOSER_DISCOVERY#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId(CHOOSER_DISCOVERY_HOOK_ID).intercept(activityStartObserverHooker())
                installed++
                record("CHOOSER_DISCOVERY_HOOK_INSTALLED method=${method.toGenericString()}")
            }.onFailure {
                installedMethods.remove(key)
                record("CHOOSER_DISCOVERY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
            }
        }
        record("CHOOSER_DISCOVERY_HOOKS new=$installed sourceApps=${snapshot.hiddenFromApps.size}")
    }

    private fun activityStartObserverHooker() = XposedInterface.Hooker { chain ->
        runCatching { observeActivityStart(chain) }
            .onFailure { diagnostic("CHOOSER_ACTIVITY_OBSERVER_FAILED error=${it.javaClass.name}") }
        chain.proceed()
    }

    private fun observeActivityStart(chain: XposedInterface.Chain) {
        val current = snapshot
        if (current.hiddenFromApps.isEmpty()) return
        val request = findActivityStartRequest(chain) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_REQUEST_NOT_FOUND args=${chain.args.joinToString { it?.javaClass?.name ?: "null" }}")
            return
        }
        if (current.diagnostic) logChooserRequestSchema(request)
        val view = extractActivityStartView(request, current) ?: return
        val intent = view.intent
        val callerPackage = view.callerPackage
        val uid = view.uid

        if (intent.action == Intent.ACTION_CHOOSER) {
            injectSystemChooserExclusions(intent, callerPackage, uid, current)
        }

        val component = intent.component
        if (component != null && component.packageName == callerPackage) {
            val routeKey = "$callerPackage|${component.flattenToShortString()}"
            val template = learnedChooserTemplates[routeKey]
            if (template != null && current.diagnostic) {
                diagnostic("CHOOSER_SYSTEM_REDIRECT_DISABLED caller=$callerPackage component=${component.flattenToShortString()} reason=app_start_probe")
            }
        }

        if (!current.diagnostic) return
        if (component == null || component.packageName != callerPackage) return
        val hasPayloadHint = intent.data != null || intent.type != null || intent.clipData != null ||
            intent.selector != null || runCatching { intent.extras?.keySet()?.isNotEmpty() == true }.getOrDefault(false)
        val entry = RecentChooserLaunch(uid, callerPackage, component.flattenToShortString(), SystemClock.elapsedRealtime(), hasPayloadHint)
        recentChooserLaunches[uid] = entry
        diagnostic("CHOOSER_ACTIVITY_CANDIDATE uid=$uid caller=$callerPackage component=${entry.component} payloadHint=$hasPayloadHint source=${view.source}")
    }

    private fun findActivityStartRequest(chain: XposedInterface.Chain): Any? {
        chain.args.firstOrNull { value -> value != null && value.javaClass.name.contains("ActivityStarter\$Request") }?.let { return it }
        chain.args.firstOrNull { value -> value != null && value.javaClass.simpleName == "Request" }?.let { return it }
        return chain.args.firstOrNull { value ->
            value != null && allInstanceFields(value.javaClass).any { field -> Intent::class.java.isAssignableFrom(field.type) }
        }
    }

    private fun extractActivityStartView(request: Any, current: RuleSnapshot): ActivityStartView? {
        chooserRequestAccessorCache[request.javaClass]?.let { cached ->
            readActivityStartWithAccessor(request, cached, current)?.let { return it }
            chooserRequestAccessorCache.remove(request.javaClass, cached)
        }

        val fields = allInstanceFields(request.javaClass)
        val values = fields.mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field to field.get(request)
            }.getOrNull()
        }

        val intentCandidates = values.filter { (_, value) -> value is Intent }
        val intentPair = intentCandidates.firstOrNull { (_, value) -> (value as Intent).action == Intent.ACTION_CHOOSER }
            ?: intentCandidates.firstOrNull { (_, value) -> (value as Intent).component != null }
            ?: intentCandidates.firstOrNull()
            ?: run {
                if (current.diagnostic) diagnostic("CHOOSER_REQUEST_PROBE class=${request.javaClass.name} result=no_intent")
                return null
            }
        val intent = intentPair.second as Intent

        val preferredUidNames = setOf("callingUid", "mCallingUid", "realCallingUid", "mRealCallingUid", "originatingUid")
        val intCandidates = values.mapNotNull { (field, value) ->
            val uid = value as? Int ?: return@mapNotNull null
            if (uid < Process.FIRST_APPLICATION_UID) return@mapNotNull null
            val packages = runCatching { callerPackages(uid, null) }.getOrDefault(emptySet())
            if (packages.none(current.hiddenFromApps::contains)) return@mapNotNull null
            Triple(field, uid, packages)
        }
        val uidTriple = intCandidates.firstOrNull { it.first.name in preferredUidNames } ?: intCandidates.firstOrNull()
            ?: run {
                if (current.diagnostic) diagnostic("CHOOSER_REQUEST_PROBE class=${request.javaClass.name} intentField=${intentPair.first.name} result=no_source_uid intFields=${values.filter { it.second is Int }.joinToString { it.first.name }}")
                return null
            }

        val preferredPackageNames = setOf("callingPackage", "mCallingPackage", "realCallingPackage", "mRealCallingPackage")
        val packagePair = values.firstOrNull { (field, value) -> field.name in preferredPackageNames && value is String && value in current.hiddenFromApps }
            ?: values.firstOrNull { (_, value) -> value is String && value in current.hiddenFromApps }
        val callerPackage = (packagePair?.second as? String) ?: uidTriple.third.firstOrNull(current.hiddenFromApps::contains) ?: return null

        val accessor = ActivityStartAccessor(
            intentField = intentPair.first.apply { isAccessible = true },
            uidField = uidTriple.first.apply { isAccessible = true },
            packageField = packagePair?.first?.apply { isAccessible = true },
            source = "dynamic_probe",
        )
        chooserRequestAccessorCache[request.javaClass] = accessor
        if (current.diagnostic) diagnostic(
            "CHOOSER_REQUEST_PROBE class=${request.javaClass.name} result=resolved intentField=${intentPair.first.name} uidField=${uidTriple.first.name} packageField=${packagePair?.first?.name ?: "uid_lookup"} caller=$callerPackage uid=${uidTriple.second} intentAction=${intent.action} component=${intent.component?.flattenToShortString()}"
        )
        return ActivityStartView(intent, uidTriple.second, callerPackage, accessor.source)
    }

    private fun readActivityStartWithAccessor(request: Any, accessor: ActivityStartAccessor, current: RuleSnapshot): ActivityStartView? {
        val intent = runCatching { accessor.intentField.get(request) as? Intent }.getOrNull() ?: return null
        val uid = runCatching { accessor.uidField?.get(request) as? Int }.getOrNull()?.takeIf { it >= Process.FIRST_APPLICATION_UID } ?: return null
        val packages = runCatching { callerPackages(uid, null) }.getOrDefault(emptySet())
        if (packages.none(current.hiddenFromApps::contains)) return null
        val directPackage = runCatching { accessor.packageField?.get(request) as? String }.getOrNull()
        val callerPackage = directPackage?.takeIf(current.hiddenFromApps::contains)
            ?: packages.firstOrNull(current.hiddenFromApps::contains) ?: return null
        return ActivityStartView(intent, uid, callerPackage, "cached_${accessor.source}")
    }

    private fun logChooserRequestSchema(request: Any) {
        if (!chooserRequestSchemasLogged.add(request.javaClass)) return
        val fields = allInstanceFields(request.javaClass)
        val schema = fields.take(96).joinToString(",") { "${it.name}:${it.type.name}" }
        diagnostic("CHOOSER_REQUEST_SCHEMA class=${request.javaClass.name} fields=$schema")
    }

    private fun allInstanceFields(clazz: Class<*>): List<Field> =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .distinctBy { "${it.declaringClass.name}#${it.name}" }
            .toList()

    @Suppress("DEPRECATION")
    private fun injectSystemChooserExclusions(chooser: Intent, callerPackage: String, callerUid: Int, current: RuleSnapshot) {
        val target = runCatching { chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) }.getOrNull()
        if (target == null) {
            diagnostic("SYSTEM_CHOOSER_OBSERVED uid=$callerUid caller=$callerPackage target=missing action=${chooser.action}")
            return
        }
        val effective = target.selector ?: target
        val kind = effective.intentKind(effective.type)
        diagnostic("SYSTEM_CHOOSER_OBSERVED uid=$callerUid caller=$callerPackage targetAction=${effective.action} mime=${effective.type} kind=${kind?.name ?: "UNKNOWN"}")
        if (current.displayMode != DisplayMode.HIDE_SELECTED || kind == null || !current.hasSelection(kind)) return

        val configured = current.configured.asSequence().mapNotNull { id ->
            val parts = id.split('|', limit = 3)
            if (parts.size != 3 || parts[0] != kind.name) return@mapNotNull null
            val pkg = parts[1]
            if (pkg.isBlank() || pkg == callerPackage || pkg == MANAGER_PACKAGE) return@mapNotNull null
            val cls = if (parts[2].startsWith('.')) pkg + parts[2] else parts[2]
            runCatching { ComponentName(pkg, cls) }.getOrNull()
        }.toList()
        if (configured.isEmpty()) return

        val existing = runCatching {
            chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)
                ?.filterIsInstance<ComponentName>().orEmpty()
        }.getOrDefault(emptyList())
        val merged = LinkedHashMap<String, ComponentName>()
        (existing + configured).forEach { component -> merged[component.flattenToString()] = component }
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, merged.values.toTypedArray())
        diagnostic("CHOOSER_EXCLUDE_INJECTED uid=$callerUid caller=$callerPackage kind=$kind added=${configured.size} existing=${existing.size} total=${merged.size}")
    }

    private fun readNamedField(value: Any, names: List<String>): Any? {
        for (clazz in generateSequence(value.javaClass as Class<*>?) { it.superclass }) {
            for (name in names) {
                val field = runCatching { clazz.getDeclaredField(name) }.getOrNull() ?: continue
                runCatching {
                    field.isAccessible = true
                    return field.get(value)
                }
            }
        }
        return null
    }

    private fun observeChooserQuery(callerUid: Int, intent: Intent, kind: IntentKind, candidateCount: Int) {
        val current = snapshot
        if (!current.diagnostic || candidateCount < 2) return
        val launch = recentChooserLaunches[callerUid] ?: return
        val age = SystemClock.elapsedRealtime() - launch.startedAt
        if (age !in 0..CHOOSER_DISCOVERY_WINDOW_MS) {
            recentChooserLaunches.remove(callerUid, launch)
            return
        }
        if (launch.callerPackage !in current.hiddenFromApps) return
        var score = 3 // Multiple resolver candidates immediately after an app-owned Activity launch.
        if (launch.hasPayloadHint) score += 1
        if (intent.data != null || intent.type != null || intent.clipData != null) score += 2
        if (kind in setOf(IntentKind.OPEN, IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.BROWSER)) score += 1
        val key = "${launch.callerPackage}|${launch.component}|${kind.name}"
        val hits = learnedChooserHits.merge(key, 1, Int::plus) ?: 1
        val confidence = when {
            score >= 7 && hits >= 1 -> "HIGH"
            score >= 6 && hits >= 2 -> "HIGH"
            score >= 6 -> "MEDIUM"
            else -> "LOW"
        }
        if (confidence == "HIGH") {
            val routeKey = "${launch.callerPackage}|${launch.component}"
            learnedChooserTemplates[routeKey] = LearnedChooserTemplate(kind, intent.action, intent.type, intent.flags)
            diagnostic("CHOOSER_REDIRECT_ARMED caller=${launch.callerPackage} component=${launch.component} kind=$kind mime=${intent.type}")
        }
        diagnostic("CHOOSER_LEARNED uid=$callerUid caller=${launch.callerPackage} component=${launch.component} kind=$kind candidates=$candidateCount score=$score hits=$hits confidence=$confidence ageMs=$age")
    }

    private fun tryRedirectLearnedChooser(
        request: Any,
        source: Intent,
        view: ActivityStartView,
        component: ComponentName,
        template: LearnedChooserTemplate,
        current: RuleSnapshot,
    ): Boolean {
        // Learned custom chooser entry points are converted back into Android's own chooser.
        // Keeping the launch inside the original ActivityStarter request preserves the source UID
        // so Resolver/ChooserActivity can perform URI grant handling as the real caller.
        if (view.callerPackage == MANAGER_PACKAGE || source.action == Intent.ACTION_CHOOSER) return false
        val payload = buildAdaptiveChooserPayload(source, template) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_SYSTEM_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=no_current_payload kind=${template.kind}")
            return false
        }
        if (payloadHasUri(payload) && payload.flags and URI_GRANT_FLAGS == 0) {
            payload.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(
            payload,
            if (template.kind in setOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE)) "分享到" else "打开方式",
        ).apply {
            // Preserve task semantics only. URI grant flags belong to the nested target Intent.
            addFlags(source.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        // Reuse the component-precise rules for standard ChooserActivity as an extra safety layer.
        injectSystemChooserExclusions(chooser, view.callerPackage, view.uid, current)

        val userId = (readNamedField(request, listOf("userId")) as? Int) ?: 0
        val resolved = resolveRedirectActivity(chooser, userId) ?: run {
            if (current.diagnostic) diagnostic("CHOOSER_SYSTEM_REDIRECT_SKIP caller=${view.callerPackage} component=${component.flattenToShortString()} reason=system_chooser_unresolved")
            return false
        }
        val intentField = allInstanceFields(request.javaClass).firstOrNull { it.name == "intent" && Intent::class.java.isAssignableFrom(it.type) } ?: return false
        val resolveField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolveInfo" && ResolveInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val activityField = allInstanceFields(request.javaClass).firstOrNull { it.name == "activityInfo" && ActivityInfo::class.java.isAssignableFrom(it.type) } ?: return false
        val resolvedTypeField = allInstanceFields(request.javaClass).firstOrNull { it.name == "resolvedType" }
        val componentSpecifiedField = allInstanceFields(request.javaClass).firstOrNull { it.name == "componentSpecified" }

        return runCatching {
            val resolverInfo = requireNotNull(resolved.activityInfo)
            val resolverComponent = ComponentName(resolverInfo.packageName, resolverInfo.name)
            // OxygenOS resolves ACTION_CHOOSER to a launcher/trampoline, but executeRequest may
            // re-resolve an implicit chooser later and return START_INTENT_NOT_RESOLVED (-91).
            // Pin the already-resolved platform component and keep both request Intent slots in sync.
            chooser.component = resolverComponent
            intentField.isAccessible = true
            resolveField.isAccessible = true
            activityField.isAccessible = true
            intentField.set(request, chooser)
            allInstanceFields(request.javaClass)
                .firstOrNull { it.name == "ephemeralIntent" && Intent::class.java.isAssignableFrom(it.type) }
                ?.let { field -> field.isAccessible = true; field.set(request, Intent(chooser)) }
            resolveField.set(request, resolved)
            activityField.set(request, resolverInfo)
            resolvedTypeField?.let { it.isAccessible = true; it.set(request, null) }
            componentSpecifiedField?.let { it.isAccessible = true; it.setBoolean(request, true) }
            diagnostic(
                "CHOOSER_SYSTEM_REDIRECT_APPLIED uid=${view.uid} caller=${view.callerPackage} " +
                    "from=${component.flattenToShortString()} kind=${template.kind} targetAction=${payload.action} " +
                    "mime=${payload.type} uri=${payloadHasUri(payload)} resolver=${resolverComponent.flattenToShortString()} explicit=true"
            )
            true
        }.getOrElse {
            diagnostic("CHOOSER_SYSTEM_REDIRECT_FAILED caller=${view.callerPackage} component=${component.flattenToShortString()} error=${it.javaClass.name}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun buildAdaptiveChooserPayload(source: Intent, template: LearnedChooserTemplate): Intent? {
        fun nested(intent: Intent, depth: Int): Intent? {
            if (depth > 3) return null
            val selector = intent.selector
            if (selector != null) {
                val kind = selector.intentKind(selector.type)
                if (kind == template.kind && payloadHasUri(selector)) return Intent(selector).apply { setComponent(null); setPackage(null) }
                nested(selector, depth + 1)?.let { return it }
            }
            val extraIntent = runCatching { intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) }.getOrNull()
            if (extraIntent != null) {
                val kind = extraIntent.intentKind(extraIntent.type)
                if (kind == template.kind && payloadHasUri(extraIntent)) return Intent(extraIntent).apply { setComponent(null); setPackage(null) }
                nested(extraIntent, depth + 1)?.let { return it }
            }
            val bundle = runCatching { intent.extras }.getOrNull()
            bundle?.keySet()?.forEach { key ->
                val value = runCatching { bundle.get(key) }.getOrNull()
                if (value is Intent) {
                    val kind = value.intentKind(value.type)
                    if (kind == template.kind && payloadHasUri(value)) return Intent(value).apply { setComponent(null); setPackage(null) }
                    nested(value, depth + 1)?.let { return it }
                }
            }
            return null
        }
        nested(source, 0)?.let { target ->
            if (target.action == null) target.setAction(template.action ?: if (template.kind == IntentKind.OPEN || template.kind == IntentKind.BROWSER) Intent.ACTION_VIEW else null)
            if (target.type == null && template.mime != null) target.setType(template.mime)
            target.addFlags(source.flags and URI_GRANT_FLAGS)
            return target
        }
        if (template.kind !in setOf(IntentKind.OPEN, IntentKind.BROWSER)) return null
        val uri = firstUri(source) ?: return null
        return Intent(template.action ?: Intent.ACTION_VIEW).apply {
            if (template.mime != null) setDataAndType(uri, template.mime) else setData(uri)
            addFlags((source.flags or template.flags) and URI_GRANT_FLAGS)
        }
    }

    @Suppress("DEPRECATION")
    private fun firstUri(intent: Intent, depth: Int = 0): android.net.Uri? {
        if (depth > 3) return null
        intent.data?.let { return it }
        intent.clipData?.let { clip -> if (clip.itemCount > 0) clip.getItemAt(0).uri?.let { return it } }
        val bundle = runCatching { intent.extras }.getOrNull() ?: return null
        bundle.keySet().forEach { key ->
            when (val value = runCatching { bundle.get(key) }.getOrNull()) {
                is android.net.Uri -> return value
                is Intent -> firstUri(value, depth + 1)?.let { return it }
                is Array<*> -> value.filterIsInstance<android.net.Uri>().firstOrNull()?.let { return it }
                is Collection<*> -> value.filterIsInstance<android.net.Uri>().firstOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun payloadHasUri(intent: Intent): Boolean = intent.data != null || intent.clipData != null || firstUri(intent) != null

    private fun resolveRedirectActivity(intent: Intent, userId: Int): ResolveInfo? {
        val identity = Binder.clearCallingIdentity()
        return try {
            val pms = Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null) ?: return null
            val methods = (pms.javaClass.methods.asSequence() + pms.javaClass.interfaces.asSequence().flatMap { it.methods.asSequence() })
                .filter { it.name == "resolveIntent" && it.parameterTypes.firstOrNull() == Intent::class.java }
                .distinctBy(Method::toGenericString)
                .toList()
            methods.firstNotNullOfOrNull { method ->
                runCatching {
                    method.isAccessible = true
                    val args = method.parameterTypes.mapIndexed { index, type ->
                        when {
                            index == 0 -> intent
                            type == String::class.java -> null
                            type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
                            type == Int::class.javaPrimitiveType || type == Int::class.java -> if (index == method.parameterTypes.lastIndex) userId else 0
                            type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
                            else -> null
                        }
                    }.toTypedArray()
                    method.invoke(pms, *args) as? ResolveInfo
                }.getOrNull()
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun retargetNeededUriGrants(grants: Any, targetPackage: String, targetUid: Int?): Boolean {
        if (targetUid == null) return false
        var packageChanged = false
        var uidChanged = false
        allInstanceFields(grants.javaClass).forEach { field ->
            runCatching {
                field.isAccessible = true
                when {
                    field.type == String::class.java && field.name.lowercase().contains("target") && (field.name.lowercase().contains("pkg") || field.name.lowercase().contains("package")) -> {
                        field.set(grants, targetPackage); packageChanged = true
                    }
                    (field.type == Int::class.javaPrimitiveType || field.type == Int::class.java) && field.name.lowercase().contains("target") && field.name.lowercase().contains("uid") -> {
                        field.setInt(grants, targetUid); uidChanged = true
                    }
                }
            }
        }
        return packageChanged || uidChanged
    }

    private sealed interface PackageNameAccessor {
        fun read(value: Any): String?

        data class Getter(private val method: Method) : PackageNameAccessor {
            override fun read(value: Any): String? = runCatching { method.invoke(value) as? String }.getOrNull()
        }

        data class FieldValue(private val field: Field) : PackageNameAccessor {
            override fun read(value: Any): String? = runCatching { field.get(value) as? String }.getOrNull()
        }

        data object None : PackageNameAccessor {
            override fun read(value: Any): String? = null
        }
    }

    private data class VisibilityMethodAdapter(
        val uidIndex: Int,
        val targetIndex: Int,
        val computerIndex: Int?,
        val signature: String,
    ) {
        companion object {
            fun create(method: Method): VisibilityMethodAdapter? {
                val types = method.parameterTypes
                if (method.name != "shouldFilterApplication" || method.returnType != Boolean::class.javaPrimitiveType) return null

                // Android 13+ AOSP: (Computer snapshot, int callingUid, ..., PackageStateInternal target, ...)
                if (types.size >= 4 && types[1] == Int::class.javaPrimitiveType &&
                    types[0].name.contains("Computer")) {
                    return VisibilityMethodAdapter(1, 3, 0, "T_PLUS")
                }
                // Android 11/12 AOSP AppsFilter: callingUid is first, target package state is usually third.
                if (types.size >= 3 && types[0] == Int::class.javaPrimitiveType) {
                    return VisibilityMethodAdapter(0, 2, null, "R_S")
                }
                // OEM/newer fallback: resolve indexes once at installation, never scan args on the hot path.
                val uid = types.indexOfFirst { it == Int::class.javaPrimitiveType }
                val target = types.indices.lastOrNull { i ->
                    i != uid && !types[i].isPrimitive && types[i] != String::class.java &&
                        (types[i].name.contains("PackageState") || types[i].name.contains("PackageSetting"))
                } ?: types.indices.lastOrNull { i -> i != uid && !types[i].isPrimitive }
                val computer = types.indices.firstOrNull { types[it].name.contains("Computer") }
                return if (uid >= 0 && target != null) VisibilityMethodAdapter(uid, target, computer, "OEM_FALLBACK") else null
            }
        }
    }

    private fun installSystemVisibilityHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_VISIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("VISIBILITY_CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            val methods = generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { it.name == "shouldFilterApplication" && it.returnType == Boolean::class.javaPrimitiveType }
                .distinctBy(Method::toGenericString)
                .toList()
            methods.forEach { method ->
                val adapter = VisibilityMethodAdapter.create(method)
                if (adapter == null) {
                    record("VISIBILITY_SIGNATURE_UNSUPPORTED method=${method.toGenericString()}")
                    return@forEach
                }
                val key = "VISIBILITY#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker(adapter))
                    installed++
                    record("VISIBILITY_ADAPTER signature=${adapter.signature} uidIndex=${adapter.uidIndex} targetIndex=${adapter.targetIndex} computerIndex=${adapter.computerIndex} method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        }
        installArchivedPackageHook(classLoader)
        record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} targets=${snapshot.visibilityHiddenTargets.size} hotPath=memory_snapshot")
    }

    private fun installArchivedPackageHook(classLoader: ClassLoader) {
        val clazz = runCatching { Class.forName("com.android.server.pm.PackageManagerService", false, classLoader) }.getOrNull() ?: return
        val methods = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == "getArchivedPackageInternal" && it.parameterTypes.isNotEmpty() }
            .toList()
        methods.forEach { method ->
            val key = "ARCHIVED_VISIBILITY#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId(ARCHIVED_VISIBILITY_HOOK_ID).intercept(archivedPackageHooker())
                record("ARCHIVED_VISIBILITY_HOOK_INSTALLED method=${method.toGenericString()}")
            }.onFailure {
                installedMethods.remove(key)
                record("ARCHIVED_VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
            }
        }
    }

    private fun systemVisibilityHooker(adapter: VisibilityMethodAdapter) = XposedInterface.Hooker { chain ->
        if (visibilityFailOpen) return@Hooker chain.proceed()
        val current = snapshot // immutable/volatile snapshot: no preference I/O on this hot path.
        if (!visibilityEnabled(current)) return@Hooker chain.proceed()
        try {
            val args = chain.args
            val callingUid = args.getOrNull(adapter.uidIndex) as? Int ?: return@Hooker chain.proceed()
            if (callingUid < Process.FIRST_APPLICATION_UID) return@Hooker chain.proceed()

            val computer = adapter.computerIndex?.let { args.getOrNull(it) }
            val callers = callerPackages(callingUid, computer)
            if (callers.isEmpty() || callers.none(current.hiddenFromApps::contains)) return@Hooker chain.proceed()

            val target = packageNameFromState(args.getOrNull(adapter.targetIndex)) ?: return@Hooker chain.proceed()
            if (!shouldHidePackage(current, callers, target)) return@Hooker chain.proceed()

            visibilityFailureCount = 0
            diagnostic("SYSTEM_VISIBILITY_FILTER uid=$callingUid caller=${callers.sorted()} target=$target adapter=${adapter.signature}")
            true
        } catch (failure: Throwable) {
            onVisibilityFailure("shouldFilterApplication", failure)
            chain.proceed()
        }
    }

    private fun archivedPackageHooker() = XposedInterface.Hooker { chain ->
        if (visibilityFailOpen) return@Hooker chain.proceed()
        val current = snapshot
        if (!visibilityEnabled(current)) return@Hooker chain.proceed()
        try {
            val callingUid = Binder.getCallingUid()
            if (callingUid < Process.FIRST_APPLICATION_UID) return@Hooker chain.proceed()
            val callers = callerPackages(callingUid, null)
            if (callers.none(current.hiddenFromApps::contains)) return@Hooker chain.proceed()
            val target = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
            if (!shouldHidePackage(current, callers, target)) return@Hooker chain.proceed()
            diagnostic("SYSTEM_VISIBILITY_ARCHIVED_FILTER uid=$callingUid caller=${callers.sorted()} target=$target")
            null
        } catch (failure: Throwable) {
            onVisibilityFailure("getArchivedPackageInternal", failure)
            chain.proceed()
        }
    }

    private fun visibilityEnabled(current: RuleSnapshot): Boolean =
        current.displayMode == DisplayMode.HIDE_SELECTED &&
            current.hiddenFromApps.isNotEmpty() && current.visibilityHiddenTargets.isNotEmpty()

    private fun shouldHidePackage(current: RuleSnapshot, callers: Set<String>, target: String): Boolean {
        if (target !in current.visibilityHiddenTargets) return false
        if (target in PROTECTED_VISIBILITY_PACKAGES || target in callers) return false
        return callers.any(current.hiddenFromApps::contains)
    }

    private fun callerPackages(uid: Int, computer: Any?): Set<String> {
        val now = SystemClock.elapsedRealtime()
        callerPackageCache[uid]?.takeIf { it.expiresAt > now }?.let { return it.packages }
        val packages = if (computer != null) packagesForUidFromComputer(computer, uid) else packagesForUidFromSystem(uid)
        if (packages.isNotEmpty()) callerPackageCache[uid] = CallerCacheEntry(packages, now + CALLER_CACHE_TTL_MS)
        return packages
    }

    private fun packagesForUidFromComputer(computer: Any, uid: Int): Set<String> {
        val method = findGetPackagesForUid(computer.javaClass) ?: return packagesForUidFromSystem(uid)
        val identity = Binder.clearCallingIdentity()
        return try {
            method.isAccessible = true
            stringSet(method.invoke(computer, uid))
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun packagesForUidFromSystem(uid: Int): Set<String> {
        val identity = Binder.clearCallingIdentity()
        return try {
            var pms = appGlobalsPackageManager
            var method = appGlobalsGetPackagesForUid
            if (pms == null || method == null) {
                pms = Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null) ?: return emptySet()
                method = pms.javaClass.methods.firstOrNull { candidate ->
                    candidate.name == "getPackagesForUid" && candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                } ?: pms.javaClass.interfaces.asSequence().flatMap { it.methods.asSequence() }.firstOrNull { candidate ->
                    candidate.name == "getPackagesForUid" && candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                }
                if (method == null) return emptySet()
                method.isAccessible = true
                appGlobalsPackageManager = pms
                appGlobalsGetPackagesForUid = method
            }
            stringSet(method.invoke(pms, uid))
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun stringSet(value: Any?): Set<String> = when (value) {
        is Array<*> -> value.filterIsInstance<String>().toSet()
        is Collection<*> -> value.filterIsInstance<String>().toSet()
        else -> emptySet()
    }

    private fun findGetPackagesForUid(clazz: Class<*>): Method? =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == "getPackagesForUid" && candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType
            }

    private fun packageNameFromState(value: Any?): String? {
        if (value == null) return null
        if (value is String) return value.takeIf(::looksLikePackageName)
        if (value is Number || value is Boolean || value is ClassLoader) return null
        val accessor = packageAccessorCache.computeIfAbsent(value.javaClass, ::buildPackageNameAccessor)
        return accessor.read(value)?.takeIf(::looksLikePackageName)
    }

    private fun buildPackageNameAccessor(clazz: Class<*>): PackageNameAccessor {
        val classes = generateSequence(clazz as Class<*>?) { it.superclass }.toList()
        classes.asSequence().flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == String::class.java &&
                    method.name in setOf("getPackageName", "getName")
            }?.let { method ->
                runCatching { method.isAccessible = true }
                return PackageNameAccessor.Getter(method)
            }
        classes.asSequence().flatMap { it.declaredFields.asSequence() }
            .firstOrNull { field ->
                field.type == String::class.java && field.name in setOf("mName", "name", "packageName", "mPackageName")
            }?.let { field ->
                runCatching { field.isAccessible = true }
                return PackageNameAccessor.FieldValue(field)
            }
        return PackageNameAccessor.None
    }

    private fun onVisibilityFailure(stage: String, failure: Throwable) {
        val failures = ++visibilityFailureCount
        record("VISIBILITY_FAILURE stage=$stage count=$failures error=${failure.javaClass.name}")
        if (failures >= VISIBILITY_FAILURE_LIMIT) {
            visibilityFailOpen = true
            record("VISIBILITY_FAIL_OPEN enabled=true reason=repeated_failures stage=$stage")
        }
    }

    private fun looksLikePackageName(value: String): Boolean =
        value.length in 3..255 && '.' in value && value.none { it.isWhitespace() || it.isISOControl() }

    private fun installResolverClientHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName("android.app.ApplicationPackageManager", false, classLoader)
        }.getOrElse {
            record("RESOLVER_CLASS_UNAVAILABLE error=${it.javaClass.name}")
            return
        }
        var installed = 0
        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.RESOLVER)) installed++
        }
        record("RESOLVER_HOOKS new=$installed total=${installedMethods.size}")
        installFinalOrderingHooks(classLoader)
    }

    private fun installFinalOrderingHooks(loader: ClassLoader) {
        listOf("com.android.internal.app.ResolverListAdapter", "com.android.internal.app.ChooserListAdapter",
            "com.android.intentresolver.ResolverListAdapter", "com.android.intentresolver.ChooserListAdapter").forEach { name ->
            val clazz = runCatching { Class.forName(name, false, loader) }.getOrElse {
                record("ORDER_CLASS_UNAVAILABLE class=$name")
                return@forEach
            }
            runCatching {
                val methods = clazz.declaredMethods.filter { method ->
                    method.name == "processSortedList" && method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == List::class.java &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                if (methods.isEmpty()) record("ORDER_METHOD_UNAVAILABLE class=$name signature=processSortedList_List_boolean")
                methods.forEach { method ->
                    val key = "ORDER#${method.toGenericString()}"
                    if (installedMethods.add(key)) {
                        try {
                            hook(method).setId("ic-final-order").intercept(orderHooker())
                            record("ORDER_HOOK_INSTALLED method=${method.toGenericString()}")
                        } catch (failure: Throwable) {
                            installedMethods.remove(key)
                            record("ORDER_HOOK_FAILED error=${failure.javaClass.name}")
                        }
                    }
                }
            }.onFailure { record("ORDER_DISCOVERY_FAILED class=$name error=${it.javaClass.name}") }
        }
        runCatching {
            val method = Class.forName("android.widget.BaseAdapter", false, loader)
                .getDeclaredMethod("notifyDataSetChanged")
            val key = "ALPHA#${method.toGenericString()}"
            if (installedMethods.add(key)) {
                try {
                    hook(method).setId("ic-alpha-order").intercept(alphabeticalOrderHooker())
                    record("ORDER_HOOK_INSTALLED stage=alpha method=${method.toGenericString()}")
                } catch (failure: Throwable) {
                    installedMethods.remove(key)
                    throw failure
                }
            }
        }.onFailure { record("ORDER_HOOK_FAILED stage=alpha error=${it.javaClass.name}") }
        recordOrderingCapability()
    }

    private fun recordOrderingCapability() = record("ORDER_CAPABILITY ranked=${installedMethods.any { it.startsWith("ORDER#") }} alphaBoundary=${installedMethods.any { it.startsWith("ALPHA#") }} execution=unverified")

    private fun adapterKind(receiver: Any): IntentKind? {
        val intent = OrderingAccess.targetIntent(receiver) as? Intent ?: return null
        val effective = intent.selector ?: intent
        val context = runCatching { OrderingAccess.field(receiver, "mContext") as? Context }.getOrNull()
        val mime = effective.type ?: runCatching {
            context?.let { effective.resolveTypeIfNeeded(it.contentResolver) }
        }.getOrNull()
        return intent.intentKind(mime)
    }

    private fun orderItems(items: List<*>, kind: IntentKind, current: RuleSnapshot, stage: String,
        fixedPackages: Set<String> = emptySet()): List<*> {
        val priorities = current.priorities.apps[kind].orEmpty()
        if (priorities.isEmpty() || items.size < 2) return items
        val infos = items.map { item ->
            requireNotNull(item)
            if (item is ResolveInfo) item else if (stage == "alpha")
                OrderingAccess.call(item, "getResolveInfo") as ResolveInfo
            else item.javaClass.getMethod("getResolveInfoAt", Int::class.javaPrimitiveType)
                .invoke(item, 0) as ResolveInfo
        }
        val movable = items.indices.filter { infos[it].activityInfo.packageName !in fixedPackages }
        val sorted = prioritizeApps(movable, priorities,
            { requireNotNull(infos[it].activityInfo).packageName },
            { requireNotNull(infos[it].activityInfo.applicationInfo).uid / PER_USER_RANGE })
        val positions = items.indices.toMutableList()
        movable.forEachIndexed { index, position -> positions[position] = sorted[index] }
        val changed = positions != items.indices.toList()
        diagnostic("ORDER_RESULT stage=$stage kind=$kind count=${items.size} matched=${infos.count { it.activityInfo.packageName in priorities }} changed=$changed digest=${current.digest}")
        return if (changed) positions.map { items[it] } else items
    }

    private fun orderHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val replacement = runCatching {
            val current = snapshot
            if (current.displayMode == DisplayMode.SHOW_ALL) {
                diagnostic("ORDER_SKIP reason=show_all")
                return@runCatching null
            }
            val receiver = chain.thisObject ?: return@runCatching null
            val kind = adapterKind(receiver) ?: run {
                diagnostic("ORDER_SKIP reason=unclassified_intent")
                return@runCatching null
            }
            val priorities = current.priorities.apps[kind].orEmpty()
            if (priorities.isEmpty()) {
                diagnostic("ORDER_SKIP kind=$kind reason=no_priorities")
                return@runCatching null
            }
            val items = chain.args[0] as? List<*> ?: return@runCatching null
            val ordered = orderItems(items, kind, current, "ranked")
            if (ordered === items) null else chain.args.toTypedArray().also { it[0] = ordered }
        }.getOrElse {
            diagnostic("ORDER_FAILED error=${it.javaClass.name}")
            null
        }
        val result = if (replacement == null) chain.proceed() else chain.proceed(replacement)
        if (replacement != null) diagnostic("ORDER_DELIVERED stage=ranked uiVerified=false")
        result
    }

    private fun alphabeticalOrderHooker() = XposedInterface.Hooker { chain ->
        val receiver = chain.thisObject
        if (receiver != null && OrderingAccess.isChooser(receiver)) runCatching {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                diagnostic("ORDER_SKIP stage=alpha reason=not_main_thread")
                return@runCatching
            }
            pollPreferences()
            if (snapshot.displayMode == DisplayMode.SHOW_ALL) {
                diagnostic("ORDER_SKIP stage=alpha reason=show_all")
                return@runCatching
            }
            val kind = adapterKind(receiver) ?: return@runCatching
            val items = OrderingAccess.field(receiver, "mSortedList")
            if (items == null || items.javaClass != java.util.ArrayList::class.java) {
                diagnostic("ORDER_SKIP stage=alpha reason=unsupported_backing_list")
                return@runCatching
            }
            @Suppress("UNCHECKED_CAST")
            val list = items as java.util.ArrayList<Any?>
            val callerTargets = OrderingAccess.field(receiver, "mCallerTargets") as List<*>
            val fixed = callerTargets.map { target ->
                val info = OrderingAccess.call(requireNotNull(target), "getResolveInfo") as ResolveInfo
                info.activityInfo.packageName
            }.toSet()
            val ordered = orderItems(list, kind, snapshot, "alpha", fixed)
            if (ordered !== list) {
                ordered.forEachIndexed { index, item -> list[index] = item }
                diagnostic("ORDER_DELIVERED stage=alpha kind=$kind uiVerified=false")
            }
        }.onFailure { diagnostic("ORDER_FAILED stage=alpha error=${it.javaClass.name}") }
        chain.proceed()
    }

    private fun installHook(method: Method, layer: Layer): Boolean {
        val key = "${method.declaringClass.name}#${method.toGenericString()}@$layer"
        if (!installedMethods.add(key)) return false
        return runCatching {
            hook(method).setId("$HOOK_ID-${layer.name.lowercase()}").intercept(queryHooker(layer))
            record("HOOK_INSTALLED layer=$layer method=${method.toGenericString()}")
            true
        }.getOrElse {
            installedMethods.remove(key)
            record("HOOK_FAILED layer=$layer method=${method.toGenericString()} error=${it.javaClass.name}")
            Log.w(TAG, "Unable to hook ${method.declaringClass.name}.${method.name}", it)
            false
        }
    }

    private fun queryHooker(layer: Layer) = XposedInterface.Hooker { chain ->
        if (queryInProgress.get() == true) return@Hooker chain.proceed()
        val callerUid = if (layer == Layer.SYSTEM) Binder.getCallingUid() else -1
        queryInProgress.set(true)
        try {
            pollPreferences()
            val original = chain.proceed()
            try {
                processQuery(chain, original, layer, callerUid)
            } catch (failure: Throwable) {
                diagnostic("FILTER_FAILED layer=$layer error=${failure.javaClass.name}; keeping original")
                original
            }
        } finally {
            queryInProgress.remove()
        }
    }

    private fun processQuery(chain: XposedInterface.Chain, original: Any?, layer: Layer, callerUid: Int): Any? {
        val outerIntent = chain.args.firstOrNull { it is Intent } as? Intent
        val intent = outerIntent?.selector ?: outerIntent
        if (layer == Layer.SYSTEM && outerIntent?.action == RuntimeProtocol.ACTION &&
            outerIntent.`package` == RuntimeProtocol.PACKAGE && outerIntent.selector == null) {
            refreshRulesSafely("manager probe")
            val applied = snapshot
            if (ManagerIdentity.matches(callerUid, applied.managerAppId) && applied.digest.isNotEmpty()) {
                val result = extractListResult(original) ?: return original
                val ack = ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply {
                        packageName = RuntimeProtocol.PACKAGE
                        name = RuntimeProtocol.COMPONENT
                        applicationInfo = ApplicationInfo().apply {
                            packageName = RuntimeProtocol.PACKAGE
                            uid = callerUid
                        }
                    }
                    nonLocalizedLabel = "${BuildConfig.VERSION_CODE}:${applied.digest}"
                }
                record("CONFIG_ACK version=${BuildConfig.VERSION_CODE} digest=${applied.digest} callerUid=$callerUid")
                return result.rebuild(listOf(ack))
            }
            return original
        }
        val moduleAppId = snapshot.managerAppId
        if (layer == Layer.SYSTEM && !ManagerIdentity.valid(moduleAppId)) {
            diagnostic("FILTER_PAUSED reason=manager_identity_unknown open_module_app_to_sync")
            return original
        }
        if (ManagerIdentity.matches(callerUid, moduleAppId)) {
            if (tracedActions.add("manager_bypass")) record("MANAGER_QUERY_BYPASS uid=$callerUid")
            return original
        }
        val intentIndex = chain.args.indexOfFirst { it is Intent }
        val resolvedType = if (layer == Layer.SYSTEM) chain.args.getOrNull(intentIndex + 1) as? String else null
        val kind = intent?.intentKind(resolvedType)
        val explicit = intent?.component != null || intent?.`package` != null ||
            outerIntent?.component != null || outerIntent?.`package` != null
        if (intent != null && kind != null) {
            val traceKey = "$layer|$kind|$callerUid"
            if (!explicit && (callerUid >= 10_000 || layer == Layer.RESOLVER) && snapshot.diagnostic &&
                tracedActions.size < 128 && tracedActions.add(traceKey)) {
                diagnostic("FIRST_QUERY_STACK layer=$layer kind=$kind callerUid=$callerUid action=${intent.action} " +
                    Throwable().stackTrace.take(14).joinToString(" <- ") { "${it.className}.${it.methodName}" })
            }
            diagnostic("QUERY layer=$layer kind=$kind action=${intent.action} mime=${intent.type ?: resolvedType} " +
                "scheme=${intent.data?.scheme} component=${intent.component?.flattenToShortString()} " +
                "package=${intent.`package`} callerUid=$callerUid " +
                "result=${original?.javaClass?.name} rules=${snapshot.configured.size} mode=${snapshot.displayMode}")
        }
        if (intent == null || explicit) {
            if (kind != null) diagnostic("SKIP explicit_component_or_package")
            return original
        }
        if (kind == null) {
            if (intent.action == Intent.ACTION_VIEW) diagnostic("SKIP_UNCLASSIFIED scheme=${intent.data?.scheme} mime=${intent.type ?: resolvedType}")
            return original
        }
        val extracted = extractListResult(original) ?: run {
            diagnostic("skip $layer ${intent.action}: unsupported result ${original?.javaClass?.name}")
            return original
        }
        if (layer == Layer.SYSTEM) observeChooserQuery(callerUid, intent, kind, extracted.values.size)
        val replacement = transform(kind, extracted.values, layer, callerUid) ?: return original
        return runCatching { extracted.rebuild(replacement) }.getOrElse {
            Log.e(TAG, "Failed to rebuild ${original?.javaClass?.name}; keeping original", it)
            original
        }
    }

    private fun isSelectedCandidate(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot, layer: Layer): Boolean {
        val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
            activity.packageName, activity.name, activity.targetActivity
        )
        if ("${kind.name}|${activity.packageName}|$canonicalClass" in current.configured) return true
        return false
    }

    private fun transform(kind: IntentKind, values: List<*>, layer: Layer, callerUid: Int): List<*>? {
        if (values.isEmpty()) { diagnostic("SKIP $layer $kind empty_input"); return null }
        val current = snapshot
        if (current.displayMode == DisplayMode.SHOW_ALL) {
            diagnostic("NO_CHANGE $layer $kind size=${values.size} reason=show_all")
            return null
        }
        var changed = false
        val filtered = if (!current.hasSelection(kind) && current.displayMode != DisplayMode.SHOW_SELECTED) {
            values
        } else {
            values.filter { value ->
                val info = value as? ResolveInfo ?: return@filter true
                val activity = info.activityInfo ?: return@filter true
                if (FilterPolicy.sameCaller(callerUid, activity.applicationInfo?.uid ?: -1)) {
                    diagnostic("KEEP_SAME_APP $layer $kind ${activity.packageName}/${activity.name} callerUid=$callerUid", detail = true)
                    return@filter true
                }
                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
                    activity.packageName, activity.name, activity.targetActivity
                )
                val exactSelected = "${kind.name}|${activity.packageName}|$canonicalClass" in current.configured
                val packageFallback = false
                val selected = exactSelected
                diagnostic(
                    "CANDIDATE $layer $kind ${activity.packageName}/${activity.name} target=${activity.targetActivity} canonical=$canonicalClass selected=$selected match=${if (exactSelected) "component" else if (packageFallback) "package" else "none"}",
                    detail = true
                )
                current.displayMode.includes(selected, current.hasSelection(kind)).also {
                    if (!it) changed = true
                }
            }
        }
        if (FilterPolicy.restoreEmpty(kind.name, values.size, filtered.size)) {
            diagnostic("RESTORE_ALL $layer $kind before=${values.size} filtered=0")
            Log.w(TAG, "Refusing to empty $kind resolver; keeping Android result")
            return null
        }
        val ordered = if (kind == IntentKind.PROCESS_TEXT) runCatching {
            orderItems(filtered, kind, current, "text_query")
        }.getOrElse {
            diagnostic("ORDER_FAILED stage=text_query error=${it.javaClass.name}")
            filtered
        } else filtered
        if (ordered !== filtered) changed = true
        if (!changed) {
            diagnostic("NO_CHANGE $layer $kind size=${values.size} hasSelection=${current.hasSelection(kind)}")
            return null
        }
        diagnostic("$layer $kind: ${values.size} -> ${ordered.size}")
        return ordered
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> extractParceledListSlice(original)
        else -> null
    }

    private fun extractParceledListSlice(original: Any): ListResult? {
        val values = runCatching {
            original.javaClass.getMethod("getList").invoke(original) as? List<*>
        }.getOrNull() ?: return null
        val constructor: Constructor<*> = runCatching {
            original.javaClass.getDeclaredConstructor(List::class.java).apply { isAccessible = true }
        }.getOrNull() ?: return null
        return ListResult(values) { constructor.newInstance(it) }
    }

    @Synchronized private fun initializePreferences() {
        if (listenerRegistered) return
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            listenerRegistered = true
        }.onFailure { Log.e(TAG, "Preference listener registration failed", it) }
        refreshRulesSafely("init")
    }

    @Synchronized private fun refreshRulesSafely(reason: String) {
        runCatching {
            preferences.getString(RuleRepository.KEY_CONFIG, null)?.let { encoded ->
                if (lastEncodedConfig == encoded) return@runCatching
                require(encoded.length <= RuleRepository.MAX_BACKUP_CHARS) { "Config too large" }
                val digest = RuntimeProtocol.digest(encoded)
                if (snapshot.digest == digest) return@runCatching
                val config = Json { ignoreUnknownKeys = true }.decodeFromString(ModuleConfig.serializer(), encoded).validated()
                snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,
                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps, config.visibilityHiddenTargets)
                callerPackageCache.clear()
                visibilityFailureCount = 0
                visibilityFailOpen = false
                lastEncodedConfig = encoded
                record("MANAGER_IDENTITY appId=${config.managerAppId} source=remote_config")
                record("RULES_READ reason=$reason count=${snapshot.configured.size} mode=${config.mode} diagnostic=${config.diagnostic} atomic=true priorities=${config.priorities.apps.mapValues { it.value.size }} hiddenFromApps=${config.hiddenFromApps.size} visibilityTargets=${config.visibilityHiddenTargets.size} digest=$digest")
                record("LEGACY_TILE_CONFIG ignored=true enabled=${config.tiles.enabled} hidden=${config.tiles.hidden.size}")
                return@runCatching
            }
            val rules = preferences.getStringSet(RuleRepository.KEY_RULES, emptySet()).orEmpty().toSet()
            val mode = DisplayMode.fromStored(
                preferences.getString(RuleRepository.KEY_DISPLAY_MODE, null),
                preferences.getBoolean(RuleRepository.KEY_BLACKLIST, true)
            )
            val priorities = runCatching {
                Json.decodeFromString(
                    PriorityConfig.serializer(),
                    preferences.getString(RuleRepository.KEY_PRIORITIES, null) ?: "{}"
                ).validated()
            }.getOrDefault(PriorityConfig())
            snapshot = RuleSnapshot(
                configured = rules, displayMode = mode, priorities = priorities,
                diagnostic = preferences.getBoolean(RuleRepository.KEY_DIAGNOSTIC, false)
            )
            lastEncodedConfig = null
            record("RULES_READ reason=$reason count=${rules.size} mode=$mode diagnostic=${snapshot.diagnostic}")
        }.onFailure {
            record("RULES_READ_FAILED error=${it.javaClass.name}")
            Log.e(TAG, "Rules refresh failed; keeping previous snapshot", it)
        }
    }

    @Synchronized private fun diagnostic(message: String, detail: Boolean = false) {
        if (!snapshot.diagnostic) return
        val now = SystemClock.elapsedRealtime()
        if (now - diagnosticWindow >= 5_000) {
            if (suppressedCount > 0 || suppressedDetails > 0)
                record("RATE_LIMIT criticalSuppressed=$suppressedCount detailSuppressed=$suppressedDetails")
            diagnosticWindow = now
            diagnosticCount = 0
            suppressedCount = 0
            detailCount = 0
            suppressedDetails = 0
        }
        if (detail) {
            if (detailCount++ >= 40) { suppressedDetails++; return }
        } else if (diagnosticCount++ >= 200) { suppressedCount++; return }
        record(message)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName uptimeMs=${SystemClock.elapsedRealtime()} $message"
        runCatching { Log.i(DIAGNOSTIC_TAG, line) }
        runCatching { log(Log.INFO, DIAGNOSTIC_TAG, line) }
    }

    private fun isQueryIntentActivitiesMethod(method: Method): Boolean =
        method.name in setOf("queryIntentActivities", "queryIntentActivitiesAsUser") &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            (List::class.java.isAssignableFrom(method.returnType) ||
                method.returnType.name == "android.content.pm.ParceledListSlice")

    private enum class Layer { SYSTEM, RESOLVER, APP }

    private companion object {
        const val TAG = "ListCleaner"
        const val DIAGNOSTIC_TAG = "ListCleaner.Diagnostic"
        const val HOOK_ID = "ic-query-filter"
        const val FRAMEWORK_PACKAGE = "android"
        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
        const val SYSTEM_SCOPE_PACKAGE = "system"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PER_USER_RANGE = 100_000
        const val VISIBILITY_HOOK_ID = "ic-system-package-visibility"
        const val ARCHIVED_VISIBILITY_HOOK_ID = "ic-system-archived-package-visibility"
        const val CHOOSER_DISCOVERY_HOOK_ID = "ic-adaptive-chooser-discovery"
        const val APP_PM_QUERY_FILTER_HOOK_ID = "ic-app-pm-query-filter"
        const val APP_MENU_CONSUMER_HOOK_ID = "ic-app-menu-consumer-probe"
        const val APP_MENU_MAX_FIELDS = 48
        const val APP_MENU_MAX_COLLECTIONS = 12
        const val APP_MENU_MAX_ITEMS = 12
        const val APP_MENU_MAX_MODEL_FIELDS = 24
        const val APP_MENU_MAX_MODEL_HITS = 6
        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"
        const val CHOOSER_DISCOVERY_WINDOW_MS = 3_000L
        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"
        const val ADAPTIVE_CHOOSER_ACTIVITY = "com.yagay.ListCleaner.ui.AdaptiveChooserActivity"
        const val ADAPTIVE_TARGET_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_TARGET"
        const val ADAPTIVE_SESSION_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_SESSION"
        const val ADAPTIVE_TARGET_PACKAGE_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_TARGET_PACKAGE"
        const val ADAPTIVE_GRANT_ACTION = "com.yagay.ListCleaner.action.ADAPTIVE_GRANT"
        const val ADAPTIVE_SESSION_TTL_MS = 60_000L
        const val ADAPTIVE_SOURCE_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_SOURCE"
        const val ADAPTIVE_KIND_EXTRA = "com.yagay.ListCleaner.extra.ADAPTIVE_KIND"
        const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        const val CALLER_CACHE_TTL_MS = 60_000L
        const val VISIBILITY_FAILURE_LIMIT = 3
        val PROTECTED_VISIBILITY_PACKAGES = setOf(
            "android", MANAGER_PACKAGE, "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller"
        )
        val SYSTEM_VISIBILITY_CLASSES = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(
            "com.android.server.pm.AppsFilterImpl"
        ) else listOf(
            "com.android.server.pm.AppsFilter"
        )
        val SYSTEM_QUERY_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService"
        )
    }
}
