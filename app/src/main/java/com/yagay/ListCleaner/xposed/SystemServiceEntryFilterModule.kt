package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.isSystemServiceEntry
import com.yagay.ListCleaner.domain.prioritizeApps
import com.yagay.ListCleaner.domain.systemServiceEntryKind
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Generic non-destructive filter for Android's standard service-selection surfaces. */
class SystemServiceEntryFilterModule : XposedModule() {
    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)
    private data class ParceledListAccessor(val getList: Method, val constructor: Constructor<*>)

    @Volatile private var processName = ""
    @Volatile private var fallbackMode = DisplayMode.HIDE_SELECTED
    @Volatile private var fallbackRules: Set<String> = emptySet()
    @Volatile private var fallbackPriorities: Map<com.yagay.ListCleaner.domain.IntentKind, List<String>> = emptyMap()
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
        runCatching { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }
        refreshFallback("init")
        installServiceHooks(param.classLoader)
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
            fallbackRules = config.rules.filter { it.kind.isSystemServiceEntry() }.mapTo(linkedSetOf()) { it.id }
            fallbackPriorities = config.priorities.apps.filterKeys { it.isSystemServiceEntry() }
            record("POLICY_READ reason=$reason rules=${fallbackRules.size}")
        }.onFailure { Log.w(TAG, "Unable to refresh service-entry policy", it) }
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

    private fun installServiceHooks(classLoader: ClassLoader) {
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: return@forEach
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { method ->
                    method.name in setOf("queryIntentServices", "queryIntentServicesAsUser", "queryIntentServicesInternal") &&
                        method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
                        (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name.endsWith("ParceledListSlice"))
                }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(serviceHooker())
                        record("HOOK_INSTALLED method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED method=$key error=${it.javaClass.name}")
                    }
                }
        }
    }

    private fun serviceHooker() = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent ?: return@Hooker chain.proceed()
        val effective = intent.selector ?: intent
        val kind = systemServiceEntryKind(effective.action) ?: return@Hooker chain.proceed()
        val definition = SYSTEM_SERVICE_ENTRY_DEFINITIONS.firstOrNull { it.action == effective.action }
            ?: return@Hooker chain.proceed()

        val callerUid = Binder.getCallingUid()
        if (!FilterPolicy.ordinaryAppCaller(callerUid)) return@Hooker chain.proceed()

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker chain.proceed()
        val selected = policy.entryRules.asSequence().mapNotNull(ComponentRule::fromId)
            .filter { it.kind == kind }.map { it.id }.toSet()
        val priorities = policy.entryPriorities[kind].orEmpty()
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) result.values else result.values.filter { value ->
            val service = (value as? ResolveInfo)?.serviceInfo ?: return@filter true
            if (definition.requiredPermission != null && service.permission != definition.requiredPermission) return@filter true
            val rule = ComponentRule(kind, service.packageName, service.name)
            if (!rule.isValid()) return@filter true
            policy.displayMode.includes(rule.id in selected, selected.isNotEmpty())
        }

        // Never make an app-facing selector unusable due to an over-broad rule or OEM API drift.
        if (result.values.isNotEmpty() && filtered.isEmpty()) {
            record("RESTORE_ALL kind=$kind callerUid=$callerUid before=${result.values.size}")
            return@Hooker original
        }
        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> (value as? ResolveInfo)?.serviceInfo?.packageName ?: "" },
            { value -> ((value as? ResolveInfo)?.serviceInfo?.applicationInfo?.uid ?: 0) / PER_USER_RANGE },
        )
        if (ordered == result.values) original else runCatching { result.rebuild(ordered) }.getOrElse { original }
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
        const val TAG = "ListCleaner.ServiceEntry"
        const val HOOK_ID = "lc-service-entry"
        const val PER_USER_RANGE = 100_000
        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked",
        )
    }
}
