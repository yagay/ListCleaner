package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.Intent
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

/** Observes launcher ShortcutInfo/Direct Share in system_server and filters only launcher-facing queries. */
class ShortcutSurfaceModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.filter {
                    it.kind == IntentKind.SHORTCUT_ITEM || it.kind == IntentKind.DIRECT_SHARE
                }.mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { config ->
                config.priorities.apps.filterKeys {
                    it == IntentKind.SHORTCUT_ITEM || it == IntentKind.DIRECT_SHARE
                }
            },
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
        fallback.start()
        installShortcutHooks(param.classLoader)
        installShareTargetHooks(param.classLoader)
        installObservedDiscoveryHooks(param.classLoader)
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        if (runtime.authoritative) return runtime
        val local = fallback.snapshot()
        return fallbackRuntimePolicy(
            managerAppId = local.managerAppId,
            displayMode = local.displayMode,
            entryRules = local.rules,
            entryPriorities = local.priorities,
        )
    }

    private fun installShortcutHooks(classLoader: ClassLoader) {
        val clazz = runCatching { Class.forName(SHORTCUT_LOCAL_SERVICE, false, classLoader) }.getOrNull() ?: return
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == "getShortcuts" && List::class.java.isAssignableFrom(it.returnType) }
            .distinctBy(Method::toGenericString)
            .forEach { method -> install(method, SHORTCUT_HOOK_ID, shortcutHooker(method)) }
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
            return@Hooker original
        }

        values.filterIsInstance<ShortcutInfo>().forEach(RuntimeObservedEntryStore::observeShortcut)

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(call.callerUid, policy.managerAppId)) return@Hooker original
        val selected = policy.selected(IntentKind.SHORTCUT_ITEM)
        val priorities = policy.priorities(IntentKind.SHORTCUT_ITEM)
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker original

        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) values else values.filter { value ->
            val shortcut = value as? ShortcutInfo ?: return@filter true
            val rule = shortcutRule(shortcut, shortcut.activity) ?: return@filter true
            policy.displayMode.includes(rule.id in selected, selected.isNotEmpty())
        }
        if (values.isNotEmpty() && filtered.isEmpty()) {
            record(
                "RESTORE_ALL_SHORTCUTS callerUid=${call.callerUid} callerPackage=${call.callingPackage} " +
                    "before=${values.size}"
            )
            return@Hooker original
        }
        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered, priorities,
            { (it as? ShortcutInfo)?.`package` ?: "" },
            { 0 },
        )
        if (ordered == values) original else ordered
    }

    private fun installShareTargetHooks(classLoader: ClassLoader) {
        SHORTCUT_SERVICE_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { method ->
                    method.name == "getShareTargets" &&
                        (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name.endsWith("ParceledListSlice"))
                }
                .distinctBy(Method::toGenericString)
                .forEach { method -> install(method, DIRECT_HOOK_ID, directShareHooker()) }
        }
    }

    /**
     * OxygenOS/other OEM resolvers can render Direct Share straight from ShortcutService and never
     * call ChooserActivity.sendShareShortcutInfoList(). Filter here as a second, lower-level path;
     * the Chooser hook remains in place for AOSP variants where it is actually used.
     */
    private fun directShareHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        val values = result.values
        if (values.isEmpty()) return@Hooker original

        val parsed = values.map(::shareShortcut)
        parsed.filterNotNull().forEach { (shortcut, target) ->
            RuntimeObservedEntryStore.observeDirectShare(shortcut, target)
        }

        val callingPackage = chain.args.firstOrNull { it is String } as? String
        if (!isDirectShareResolverCaller(callingPackage)) return@Hooker original

        val callerUid = Binder.getCallingUid()
        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker original

        val selected = policy.selected(IntentKind.DIRECT_SHARE)
        val priorities = policy.priorities(IntentKind.DIRECT_SHARE)
        if ((policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) && priorities.isEmpty()) {
            return@Hooker original
        }

        val targetPackages = parsed.map { pair ->
            pair?.second?.packageName ?: pair?.first?.`package`
        }
        val ruleIds = parsed.map { pair ->
            pair?.let { (shortcut, target) -> directShareRule(shortcut, target)?.id }
        }

        // At this lower ShortcutService layer the ordinary Share app list is not available. Treat
        // every package returned by this same query as visible, so this path applies only the
        // explicit Direct Share target rules/order. Chooser-level app consistency still runs on
        // ROMs that use sendShareShortcutInfoList(). Unknown targets remain fail-open.
        val queryPackages = targetPackages.filterNotNull().toSet()
        val keptIndices = directShareFilteredIndices(
            targetPackages = targetPackages,
            ruleIds = ruleIds,
            visibleSharePackages = queryPackages,
            selectedRuleIds = selected,
            displayMode = policy.displayMode,
            priorities = priorities,
        )
        if (keptIndices.size == values.size && keptIndices.indices.all { keptIndices[it] == it }) {
            return@Hooker original
        }

        val replacement = keptIndices.map { values[it] }
        record(
            "DIRECT_FILTER callerUid=$callerUid callerPackage=$callingPackage before=${values.size} " +
                "after=${replacement.size} parsed=${ruleIds.count { it != null }} " +
                "selected=${selected.size} priorities=${priorities.size}"
        )
        result.rebuild(replacement)
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
        val shortcut = runCatching {
            findNoArgMethod(value.javaClass, "getShortcutInfo")?.invoke(value) as? ShortcutInfo
        }.getOrNull() ?: return null
        val target = runCatching {
            findNoArgMethod(value.javaClass, "getTargetComponent")?.invoke(value) as? ComponentName
        }.getOrNull()
        return shortcut to target
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.apply { isAccessible = true }

    private fun isDirectShareResolverCaller(packageName: String?): Boolean =
        packageName == INTENT_RESOLVER_PACKAGE ||
            packageName == FRAMEWORK_PACKAGE ||
            packageName == OPLUS_RESOLVER_PACKAGE ||
            packageName == COLOROS_RESOLVER_PACKAGE

    private fun installObservedDiscoveryHooks(classLoader: ClassLoader) {
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
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
        val policy = effectivePolicy()
        if (!ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        val synthetic = RuntimeObservedEntryStore.snapshot().map { entry ->
            ResolveInfo().apply {
                nonLocalizedLabel = entry.label.ifBlank { entry.kind.name }
                activityInfo = ActivityInfo().apply {
                    packageName = entry.packageName
                    name = entry.syntheticClass
                    applicationInfo = ApplicationInfo().apply { packageName = entry.packageName }
                    metaData = Bundle().apply {
                        putString(ObservedEntryProtocol.META_KIND, entry.kind.name)
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
        val SHORTCUT_MATCH_MASK = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
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
