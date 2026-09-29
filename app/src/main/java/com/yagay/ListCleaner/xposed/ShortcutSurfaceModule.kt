package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.ObservedEntryProtocol
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Observes ShortcutInfo/Direct Share in system_server; only app-facing shortcut queries are filtered. */
class ShortcutSurfaceModule : XposedModule() {
    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)
    private data class ParceledListAccessor(val getList: Method, val constructor: Constructor<*>)

    @Volatile private var processName = ""
    @Volatile private var fallbackMode = DisplayMode.HIDE_SELECTED
    @Volatile private var fallbackRules: Set<String> = emptySet()
    @Volatile private var fallbackPriorities: Map<IntentKind, List<String>> = emptyMap()
    @Volatile private var fallbackManagerAppId = -1
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val parceledListAccessorCache = ConcurrentHashMap<Class<*>, ParceledListAccessor>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refreshFallback("preference changed")
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        initializePreferences()
        installShortcutHooks(param.classLoader)
        installShareTargetHooks(param.classLoader)
        installObservedDiscoveryHooks(param.classLoader)
    }

    private fun initializePreferences() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }
        refreshFallback("init")
    }

    @Synchronized
    private fun refreshFallback(reason: String) {
        runCatching {
            val encoded = preferences.getString(RuleRepository.KEY_CONFIG, null) ?: return@runCatching
            if (encoded.length > RuleRepository.MAX_BACKUP_CHARS) return@runCatching
            val config = Json { ignoreUnknownKeys = true }
                .decodeFromString(ModuleConfig.serializer(), encoded).validated()
            fallbackMode = config.mode
            fallbackManagerAppId = config.managerAppId
            fallbackRules = config.rules.filter {
                it.kind == IntentKind.LAUNCHER_SHORTCUT || it.kind == IntentKind.SHORTCUT_ITEM
            }.mapTo(linkedSetOf()) { it.id }
            fallbackPriorities = config.priorities.apps.filterKeys {
                it == IntentKind.LAUNCHER_SHORTCUT || it == IntentKind.SHORTCUT_ITEM
            }
            record("POLICY_READ reason=$reason rules=${fallbackRules.size}")
        }.onFailure { Log.w(TAG, "Unable to refresh shortcut policy", it) }
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        return if (runtime.authoritative) runtime else RuntimeComponentPolicySnapshot(
            managerAppId = fallbackManagerAppId,
            displayMode = fallbackMode,
            entryRules = fallbackRules,
            entryPriorities = fallbackPriorities,
        )
    }

    private fun selectedForKind(kind: IntentKind, policy: RuntimeComponentPolicySnapshot): Set<String> =
        policy.entryRules.asSequence().mapNotNull(ComponentRule::fromId)
            .filter { it.kind == kind }.map { it.id }.toSet()

    private fun installShortcutHooks(classLoader: ClassLoader) {
        val clazz = runCatching { Class.forName(SHORTCUT_LOCAL_SERVICE, false, classLoader) }.getOrNull() ?: return
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == "getShortcuts" && List::class.java.isAssignableFrom(it.returnType) }
            .distinctBy(Method::toGenericString)
            .forEach { method -> install(method, SHORTCUT_HOOK_ID, shortcutHooker()) }
    }

    private fun shortcutHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        values.filterIsInstance<ShortcutInfo>().forEach(RuntimeObservedEntryStore::observeShortcut)
        if (values.isEmpty()) return@Hooker original

        val callerUid = Binder.getCallingUid()
        if (!FilterPolicy.ordinaryAppCaller(callerUid)) return@Hooker original

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker original
        val selectedApps = selectedForKind(IntentKind.LAUNCHER_SHORTCUT, policy)
        val selectedItems = selectedForKind(IntentKind.SHORTCUT_ITEM, policy)
        val itemPriorities = policy.entryPriorities[IntentKind.SHORTCUT_ITEM].orEmpty()
        val appPriorities = policy.entryPriorities[IntentKind.LAUNCHER_SHORTCUT].orEmpty()
        if (selectedApps.isEmpty() && selectedItems.isEmpty() && itemPriorities.isEmpty() && appPriorities.isEmpty()) {
            return@Hooker original
        }

        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || (selectedApps.isEmpty() && selectedItems.isEmpty())) {
            values
        } else values.filter { value ->
            val shortcut = value as? ShortcutInfo ?: return@filter true
            val itemRule = shortcutRule(shortcut, shortcut.activity)
            val appRule = launcherShortcutRule(shortcut)
            val appSelected = appRule?.id in selectedApps
            val itemSelected = itemRule?.id in selectedItems
            when (policy.displayMode) {
                DisplayMode.HIDE_SELECTED -> !(appSelected || itemSelected)
                DisplayMode.SHOW_SELECTED -> appSelected || itemSelected
                DisplayMode.SHOW_ALL -> true
            }
        }
        if (values.isNotEmpty() && filtered.isEmpty()) {
            record("RESTORE_ALL_SHORTCUTS callerUid=$callerUid before=${values.size}")
            return@Hooker original
        }
        val priorities = if (appPriorities.isNotEmpty()) appPriorities else itemPriorities
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
                .forEach { method -> install(method, DIRECT_HOOK_ID, directShareObserver()) }
        }
    }

    /** Direct Share filtering belongs to the Chooser process. system_server only observes targets. */
    private fun directShareObserver() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        result.values.forEach { value ->
            shareShortcut(value)?.let { (shortcut, target) ->
                RuntimeObservedEntryStore.observeDirectShare(shortcut, target)
            }
        }
        original
    }

    private fun shortcutRule(shortcut: ShortcutInfo, component: ComponentName?): ComponentRule? {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return null
        val shortcutId = shortcut.id?.takeIf { it.isNotBlank() } ?: return null
        val synthetic = SyntheticEntryKeys.shortcutItemClass(component?.className, shortcutId)
        return ComponentRule(IntentKind.SHORTCUT_ITEM, packageName, synthetic).takeIf(ComponentRule::isValid)
    }

    private fun launcherShortcutRule(shortcut: ShortcutInfo): ComponentRule? {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return null
        return ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            packageName,
            SyntheticEntryKeys.launcherShortcutAppClass(),
        ).takeIf(ComponentRule::isValid)
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
                .forEach { method -> install(method, DISCOVERY_HOOK_ID, observedDiscoveryHooker()) }
        }
    }

    private fun observedDiscoveryHooker() = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent ?: return@Hooker chain.proceed()
        val effective = intent.selector ?: intent
        if (effective.action != ObservedEntryProtocol.ACTION || effective.`package` != ObservedEntryProtocol.PACKAGE) {
            return@Hooker chain.proceed()
        }
        val policy = effectivePolicy()
        if (!ManagerIdentity.matches(Binder.getCallingUid(), policy.managerAppId)) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val observed = RuntimeObservedEntryStore.snapshot()
        val appLevel = observed.asSequence()
            .filter { it.kind == IntentKind.SHORTCUT_ITEM }
            .distinctBy { it.packageName }
            .map { entry ->
                entry.copy(
                    kind = IntentKind.LAUNCHER_SHORTCUT,
                    syntheticClass = SyntheticEntryKeys.launcherShortcutAppClass(),
                    label = entry.packageName,
                    activityClass = null,
                )
            }.toList()
        val synthetic = (observed + appLevel).map { entry ->
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
        runCatching { result.rebuild(synthetic) }.getOrElse { original }
    }

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker) {
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return
        runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
        }.onFailure {
            installedMethods.remove(key)
            record("HOOK_FAILED id=$id error=${it.javaClass.name}")
        }
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> extractParceledListSlice(original)
        else -> null
    }

    private fun extractParceledListSlice(original: Any): ListResult? {
        val accessor = parceledListAccessorCache.computeIfAbsent(original.javaClass) { clazz ->
            val getList = clazz.methods.firstOrNull { it.name == "getList" && it.parameterCount == 0 }
                ?.apply { isAccessible = true } ?: throw NoSuchMethodException("${clazz.name}#getList")
            val constructor = clazz.declaredConstructors.firstOrNull { ctor ->
                ctor.parameterTypes.size == 1 && List::class.java.isAssignableFrom(ctor.parameterTypes[0])
            }?.apply { isAccessible = true } ?: throw NoSuchMethodException("${clazz.name}(List)")
            ParceledListAccessor(getList, constructor)
        }
        val values = accessor.getList.invoke(original) as? List<*> ?: return null
        return ListResult(values) { accessor.constructor.newInstance(it) }
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
