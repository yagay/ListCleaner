package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import android.os.Binder
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
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Two layers: system_server filters every caller; Resolver processes provide a fallback and
 * UI-only ordering. Every failure is fail-open and returns Android's original result.
 */
class ListCleanerModule : XposedModule() {
    private data class RuleSnapshot(
        val configured: Set<String>,
        val displayMode: DisplayMode,
        val priorities: PriorityConfig,
        val diagnostic: Boolean,
        val managerAppId: Int = -1,
        val digest: String = ""
    ) {
        val selectedKinds: Set<IntentKind> = selectedKinds(configured)
        private val selectedPackages: Map<IntentKind, Set<String>> = configured.mapNotNull { id ->
            val parts = id.split('|', limit = 3)
            val kind = parts.getOrNull(0)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            val packageName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            kind to packageName
        }.groupBy({ it.first }, { it.second }).mapValues { (_, packages) -> packages.toSet() }
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
            check(param.oldHookHandles.any { handle ->
                val method = handle.executable as? Method
                (handle.id == "$HOOK_ID-${if (systemServer) "system" else "resolver"}" &&
                    method != null && isQueryIntentActivitiesMethod(method))
            }) { "No compatible query handle; restart required" }
            initializePreferences()
            param.oldHookHandles.forEach { handle ->
                val method = handle.executable as? Method
                method?.declaringClass?.classLoader?.let(loaders::add)
                val layer = when (handle.id) {
                    "$HOOK_ID-system" -> Layer.SYSTEM
                    "$HOOK_ID-resolver" -> Layer.RESOLVER
                    "$HOOK_ID-app" -> Layer.APP
                    else -> null
                }
                when {
                    method != null && layer != null && isQueryIntentActivitiesMethod(method) -> {
                        handle.replaceHook(queryHooker(layer))
                        installedMethods.add("${method.declaringClass.name}#${method.toGenericString()}@$layer")
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
                else -> Layer.APP
            }
            loaders.forEach {
                when (expectedLayer) {
                    Layer.SYSTEM -> installSystemServerQueryHooks(it)
                    Layer.RESOLVER -> installResolverClientHooks(it)
                    Layer.APP -> installApplicationClientHooks(it, baseProcess)
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
        } else {
            // Additional LSPosed scope selected by the user: only intercept the app's own
            // PackageManager candidate query. Do not install Resolver UI ordering hooks here.
            installApplicationClientHooks(param.classLoader, param.packageName)
        }
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
    }

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

    private fun installApplicationClientHooks(classLoader: ClassLoader, packageName: String) {
        val clazz = runCatching {
            Class.forName("android.app.ApplicationPackageManager", false, classLoader)
        }.getOrElse {
            record("APP_QUERY_CLASS_UNAVAILABLE package=$packageName error=${it.javaClass.name}")
            return
        }
        var installed = 0
        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.APP)) installed++
        }
        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")
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
        if (layer == Layer.APP) {
            if (extracted.values.size <= 1) {
                diagnostic("APP_SKIP kind=$kind reason=single_or_empty_candidate count=${extracted.values.size}")
                return original
            }
            if (!snapshot.hasSelection(kind)) {
                diagnostic("APP_SKIP kind=$kind reason=no_rules_for_kind")
                return original
            }
            val hasMatchingRule = extracted.values.any { value ->
                val info = value as? ResolveInfo ?: return@any false
                val activity = info.activityInfo ?: return@any false
                isSelectedCandidate(kind, activity, snapshot, Layer.APP)
            }
            if (!hasMatchingRule) {
                diagnostic("APP_SKIP kind=$kind reason=no_component_or_package_match count=${extracted.values.size}")
                return original
            }
        }
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
        return layer == Layer.APP && current.hasPackageSelection(kind, activity.packageName)
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
                val packageFallback = layer == Layer.APP && !exactSelected && current.hasPackageSelection(kind, activity.packageName)
                val selected = exactSelected || packageFallback
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
                    config.priorities, config.diagnostic, config.managerAppId, digest)
                lastEncodedConfig = encoded
                record("MANAGER_IDENTITY appId=${config.managerAppId} source=remote_config")
                record("RULES_READ reason=$reason count=${snapshot.configured.size} mode=${config.mode} diagnostic=${config.diagnostic} atomic=true priorities=${config.priorities.apps.mapValues { it.value.size }} digest=$digest")
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
        val SYSTEM_QUERY_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService"
        )
    }
}
