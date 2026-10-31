package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.DirectShareType
import com.yagay.ListCleaner.domain.DirectShareTypeRules
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.ENTRY_RUNTIME_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryRuntimePath
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.ObservedEntryProtocol
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.directShareFilteredIndices
import com.yagay.ListCleaner.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Observes launcher ShortcutInfo/Direct Share in system_server and filters only launcher-facing queries. */
class ShortcutSurfaceModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)
    private val observationWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LC-DirectShareObservation").apply { isDaemon = true }
    }
    private val observationWriter by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val runtimeCapture by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RuntimeInterfaceCaptureWriter(preferences, ::record)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = SHORTCUT_KINDS,
            includePriorities = true,
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        policyProvider.start()
        DirectShareActiveScanBridge.initialize(param.classLoader, preferences, ::record)
        installShortcutHooks(param.classLoader)
        installShareTargetHooks(param.classLoader)
        installObservedDiscoveryHooks(param.classLoader)
    }

    private fun installShortcutHooks(classLoader: ClassLoader) {
        var discovered = 0
        SHORTCUT_SERVICE_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            val methods = ReflectionAccess.hierarchyMethods(clazz)
                .filter { it.name == "getShortcuts" && List::class.java.isAssignableFrom(it.returnType) }
                .distinctBy(Method::toGenericString)
                .toList()
            discovered += methods.size
            methods.forEach { method -> install(method, SHORTCUT_HOOK_ID, shortcutHooker(method)) }
        }
        record("SHORTCUT_HOOK_SCAN candidates=$discovered installed=${installedMethods.count { it.startsWith("$SHORTCUT_HOOK_ID#") }}")
    }

    private fun shortcutHooker(method: Method) = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        if (values.isEmpty()) return@Hooker original

        val call = HookCallIdentity.shortcutCall(
            parameterTypeNames = method.parameterTypes.map { it.name },
            args = chain.args,
            binderUid = Binder.getCallingUid(),
        )
        if (!FilterPolicy.ordinaryAppCaller(call.callerUid) || call.callingPackage == null) {
            return@Hooker original
        }
        if (call.queryFlags != null && call.queryFlags and SHORTCUT_MATCH_MASK == 0) {
            record("SHORTCUT_QUERY_SKIP reason=flags caller=${call.callingPackage} flags=${call.queryFlags}")
            return@Hooker original
        }

        values.filterIsInstance<ShortcutInfo>().forEach(RuntimeObservedEntryStore::observeShortcut)

        val userId = HookCallIdentity.shortcutTargetUserId(method.parameterTypes.map { it.name }, chain.args)
            ?: return@Hooker original
        val policy = policyProvider.snapshot(userId)
        if (ManagerIdentity.matches(call.callerUid, policy.managerAppId)) return@Hooker original
        val selected = policy.selected(IntentKind.SHORTCUT_ITEM)
        val priorities = policy.priorities(IntentKind.SHORTCUT_ITEM)
        if (selected.isEmpty() && priorities.isEmpty()) {
            record("SHORTCUT_QUERY caller=${call.callingPackage} user=$userId candidates=${values.size} selected=0 mode=${policy.displayMode}")
            return@Hooker original
        }

        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) values else values.filter { value ->
            val shortcut = value as? ShortcutInfo ?: return@filter true
            val rule = shortcutRule(shortcut, shortcut.activity) ?: return@filter true
            policy.displayMode.includes(rule.id in selected, selected.isNotEmpty())
        }
        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered, priorities,
            { (it as? ShortcutInfo)?.`package` ?: "" },
            { ReflectionAccess.userId((it as? ShortcutInfo)?.userHandle) ?: userId },
        )
        record("SHORTCUT_POLICY caller=${call.callingPackage} user=$userId mode=${policy.displayMode} " +
            "candidates=${values.size} parsed=${values.count { it is ShortcutInfo }} " +
            "selected=${selected.size} matched=${values.count { item -> (item as? ShortcutInfo)?.let { shortcutRule(it, it.activity)?.id in selected } == true }} " +
            "removed=${values.size - filtered.size} ordered=${ordered != values}")
        if (ordered == values) {
            original
        } else {
            record(
                "SHORTCUT_FILTER callerUid=${call.callerUid} callerPackage=${call.callingPackage} " +
                    "before=${values.size} after=${ordered.size} removed=${values.size - filtered.size} " +
                    "selected=${selected.size} priorities=${priorities.size} emptyAllowed=${ordered.isEmpty()}"
            )
            ordered
        }
    }

    private fun installShareTargetHooks(classLoader: ClassLoader) {
        SHORTCUT_SERVICE_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            ReflectionAccess.hierarchyMethods(clazz)
                .filter { method ->
                    method.name == "getShareTargets" &&
                        (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name.endsWith("ParceledListSlice"))
                }
                .distinctBy(Method::toGenericString)
                .forEach { method -> install(method, DIRECT_HOOK_ID, directShareHooker()) }
        }
    }

    /** OEM resolvers can render Direct Share straight from ShortcutService. */
    private fun directShareHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        val values = result.values

        val callingPackage = chain.args.firstOrNull { it is String } as? String
        if (!isDirectShareResolverCaller(callingPackage)) return@Hooker original
        val callerUid = Binder.getCallingUid()
        val userId = (chain.args.lastOrNull() as? Int)?.takeIf { it in 0..21474 }
            ?: return@Hooker original
        val policy = policyProvider.snapshot(userId)
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker original

        val parsed = values.map(::shareShortcut)
        parsed.filterNotNull().forEach { (shortcut, target) ->
            RuntimeObservedEntryStore.observeDirectShare(shortcut, target)
        }
        // The ShortcutService shares matching *only* for a concrete IntentFilter.
        // Store actual MIME-family matches; never infer a Direct Share target
        // from a normal launcher shortcut's categories or ID alone.
        val filter = chain.args.firstOrNull { it is IntentFilter } as? IntentFilter
        val requestedFamilies = filter?.let { mime ->
            (0 until mime.countDataTypes()).mapNotNull {
                DirectShareType.fromMime(mime.getDataType(it))
            }.distinct()
        }.orEmpty()
        val matchedFamily = requestedFamilies.singleOrNull()
        if (matchedFamily != null) {
            val observedAt = System.currentTimeMillis()
            val captured = parsed.mapNotNull { item ->
                val (shortcut, target) = item ?: return@mapNotNull null
                val rule = directShareRule(shortcut, target) ?: return@mapNotNull null
                ObservedEntryRecord(
                    kind = IntentKind.DIRECT_SHARE.name, packageName = rule.packageName,
                    syntheticClass = rule.className,
                    label = shortcut.shortLabel?.toString().orEmpty(),
                    activityClass = target?.className ?: shortcut.activity?.className,
                    observedAt = observedAt, userId = userId,
                    directShareFamilies = setOf(matchedFamily.name),
                ).validatedOrNull()
            }
            if (captured.isNotEmpty()) observationWorker.execute {
                observationWriter.merge(captured)
            }
        }
        val selected = policy.selected(IntentKind.DIRECT_SHARE)
        val priorities = policy.priorities(IntentKind.DIRECT_SHARE)
        val targetPackages = parsed.map { pair ->
            pair?.second?.packageName ?: pair?.first?.`package`
        }
        val ruleIds = parsed.map { pair ->
            pair?.let { (shortcut, target) -> directShareRule(shortcut, target)?.id }
        }
        val effectiveSelection = DirectShareTypeRules.selectedFor(selected, ruleIds, matchedFamily)
        val indices = if ((policy.displayMode == DisplayMode.SHOW_ALL || effectiveSelection.isEmpty()) &&
            priorities.isEmpty()) values.indices.toList() else directShareFilteredIndices(
            targetPackages = targetPackages,
            ruleIds = ruleIds,
            visibleSharePackages = targetPackages.filterNotNull().toSet(),
            selectedRuleIds = effectiveSelection,
            displayMode = policy.displayMode,
            priorities = priorities,
        )
        // Store only target package names, not shortcut IDs, contact names or labels.
        // This is the actual ShortcutService method return, not final Sharesheet pixels.
        runtimeCapture.publish(
            kind = IntentKind.DIRECT_SHARE,
            source = "DIRECT_SHARE_SERVICE_RETURN",
            userId = userId,
            callerPackage = callingPackage.orEmpty(),
            packages = indices.mapNotNull { targetPackages[it] },
        )
        if (indices.size == values.size && indices.indices.all { indices[it] == it }) {
            original
        } else {
            val replacement = indices.map { values[it] }
            record(
                "DIRECT_FILTER callerUid=$callerUid callerPackage=$callingPackage before=${values.size} " +
                    "after=${replacement.size} parsed=${ruleIds.count { it != null }} " +
                    "selected=${selected.size} priorities=${priorities.size}"
            )
            result.rebuild(replacement)
        }
    }

    private fun shortcutRule(shortcut: ShortcutInfo, component: ComponentName?): ComponentRule? {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return null
        val shortcutId = shortcut.id?.takeIf { it.isNotBlank() } ?: return null
        val synthetic = SyntheticEntryKeys.shortcutItemClass(component?.className, shortcutId)
        return ComponentRule(IntentKind.SHORTCUT_ITEM, packageName, synthetic).takeIf(ComponentRule::isValid)
    }

    private fun directShareRule(shortcut: ShortcutInfo, target: ComponentName?): ComponentRule? {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return null
        val shortcutId = shortcut.id?.takeIf { it.isNotBlank() } ?: return null
        val synthetic = SyntheticEntryKeys.directShareClass(
            target?.className ?: shortcut.activity?.className,
            shortcutId,
        )
        return ComponentRule(IntentKind.DIRECT_SHARE, packageName, synthetic).takeIf(ComponentRule::isValid)
    }

    private fun shareShortcut(value: Any?): Pair<ShortcutInfo, ComponentName?>? {
        value ?: return null
        val shortcut = ReflectionAccess.invokeNoArg(value, "getShortcutInfo") as? ShortcutInfo ?: return null
        val target = ReflectionAccess.invokeNoArg(value, "getTargetComponent") as? ComponentName
        return shortcut to target
    }

    private fun isDirectShareResolverCaller(packageName: String?): Boolean =
        packageName == INTENT_RESOLVER_PACKAGE ||
            packageName == FRAMEWORK_PACKAGE ||
            packageName == OPLUS_RESOLVER_PACKAGE ||
            packageName == COLOROS_RESOLVER_PACKAGE

    private fun installObservedDiscoveryHooks(classLoader: ClassLoader) {
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            ReflectionAccess.hierarchyMethods(clazz)
                .filter { method ->
                    method.name in setOf("queryIntentActivities", "queryIntentActivitiesAsUser", "queryIntentActivitiesInternal") &&
                        method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
                        (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name.endsWith("ParceledListSlice"))
                }
                .distinctBy(Method::toGenericString)
                .forEach { method -> install(method, DISCOVERY_HOOK_ID, observedDiscoveryHooker(method)) }
        }
    }

    private fun observedDiscoveryHooker(method: Method) = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent ?: return@Hooker chain.proceed()
        val effective = intent.selector ?: intent
        if (effective.action != ObservedEntryProtocol.ACTION || effective.`package` != ObservedEntryProtocol.PACKAGE) {
            return@Hooker chain.proceed()
        }
        val callerUid = HookCallIdentity.packageManagerCallerUid(
            methodName = method.name,
            parameterTypeNames = method.parameterTypes.map { it.name },
            args = chain.args,
            binderUid = Binder.getCallingUid(),
        )
        val policy = policyProvider.snapshot(com.yagay.ListCleaner.domain.AndroidUid.userId(callerUid))
        if (!ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        val synthetic = RuntimeObservedEntryStore.snapshot(com.yagay.ListCleaner.domain.AndroidUid.userId(callerUid)).map { entry ->
            ResolveInfo().apply {
                nonLocalizedLabel = entry.label.ifBlank { entry.kind.name }
                activityInfo = ActivityInfo().apply {
                    packageName = entry.packageName
                    name = entry.syntheticClass
                    applicationInfo = ApplicationInfo().apply { packageName = entry.packageName }
                    metaData = Bundle().apply {
                        putString(ObservedEntryProtocol.META_KIND, entry.kind.name)
                        putInt(ObservedEntryProtocol.META_USER, entry.userId)
                        putLong(ObservedEntryProtocol.META_TIME, entry.observedAt)
                        entry.activityClass?.let { putString(ObservedEntryProtocol.META_ACTIVITY, it) }
                    }
                }
            }
        }
        record("DISCOVERY_RESULT count=${synthetic.size}")
        result.rebuild(synthetic)
    }

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker) {
        if (Modifier.isAbstract(method.modifiers) || method.declaringClass.isInterface) {
            record("HOOK_SKIPPED id=$id reason=abstract_or_interface method=${method.toGenericString()}")
            return
        }
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return
        runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
        }.onFailure {
            installedMethods.remove(key)
            record(
                "HOOK_FAILED id=$id method=${method.toGenericString()} error=${it.javaClass.name} " +
                    "message=${it.message?.take(160) ?: "none"}"
            )
        }
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.ShortcutSurface"
        const val SHORTCUT_HOOK_ID = "lc-shortcut-items"
        const val DIRECT_HOOK_ID = "lc-direct-observer"
        const val DISCOVERY_HOOK_ID = "lc-observed-discovery"
        const val SHORTCUT_LOCAL_SERVICE = "com.android.server.pm.ShortcutService\$LocalService"
        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
        const val FRAMEWORK_PACKAGE = "android"
        const val OPLUS_RESOLVER_PACKAGE = "com.oplus.resolver"
        const val COLOROS_RESOLVER_PACKAGE = "com.coloros.resolver"
        val SHORTCUT_KINDS = ENTRY_RUNTIME_DEFINITIONS.values.asSequence()
            .filter { EntryRuntimePath.SHORTCUT_SERVICE in it.expectedPaths }
            .mapTo(linkedSetOf()) { it.kind }
        val SHORTCUT_MATCH_MASK = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        val SHORTCUT_SERVICE_CLASSES = listOf(
            "com.android.server.pm.ShortcutService\$LocalService",
            "com.android.server.pm.ShortcutService",
        )
        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked",
        )
    }
}

