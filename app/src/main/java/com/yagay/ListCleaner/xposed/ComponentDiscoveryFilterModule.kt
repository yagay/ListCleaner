package com.yagay.ListCleaner.xposed

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.PersistentComponentState
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.ModuleConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Secondary discovery filter for Root-managed components.
 *
 * Root disable remains the authoritative state change. This module adds a second barrier in
 * system_server so protected TileService / shortcut / AppWidget provider entries are omitted from
 * discovery results even if Android/OEM startup temporarily restores a component to ENABLED.
 *
 * The manager app is bypassed so ListCleaner can still enumerate disabled components for editing.
 */
class ComponentDiscoveryFilterModule : XposedModule() {
    private data class ListResult(
        val values: List<*>,
        val rebuild: (List<*>) -> Any?
    )

    private data class ParceledListAccessor(
        val getList: Method,
        val constructor: Constructor<*>
    )

    private enum class Surface(val action: String) {
        TILE("android.service.quicksettings.action.QS_TILE"),
        SHORTCUT("android.intent.action.CREATE_SHORTCUT"),
        WIDGET("android.appwidget.action.APPWIDGET_UPDATE")
    }

    @Volatile
    private var processName = ""

    @Volatile
    private var protectedComponents: Set<String> = emptySet()

    @Volatile
    private var managerAppId: Int = -1

    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val parceledListAccessorCache = ConcurrentHashMap<Class<*>, ParceledListAccessor>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null ||
            key == PersistentComponentState.REMOTE_KEY ||
            key == RuleRepository.KEY_CONFIG
        ) {
            refreshPolicy("preference changed")
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        initializePreferences()
        installPackageManagerDiscoveryHooks(param.classLoader)
        installAppWidgetHooks(param.classLoader)
    }

    private fun initializePreferences() {
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        }.onFailure {
            Log.w(TAG, "Unable to register discovery preference listener", it)
        }
        refreshPolicy("init")
    }

    @Synchronized
    private fun refreshPolicy(reason: String) {
        runCatching {
            val config = preferences.getString(RuleRepository.KEY_CONFIG, null)?.let { encoded ->
                if (encoded.length > RuleRepository.MAX_BACKUP_CHARS) return@let null
                Json { ignoreUnknownKeys = true }
                    .decodeFromString(ModuleConfig.serializer(), encoded)
                    .validated()
            }
            protectedComponents = if (config != null) {
                PersistentComponentState.sanitize(config.rootDisabledComponents)
            } else {
                PersistentComponentState.sanitize(
                    preferences.getStringSet(PersistentComponentState.REMOTE_KEY, emptySet()).orEmpty()
                )
            }
            managerAppId = config?.managerAppId ?: -1
            record(
                "POLICY_READ reason=$reason protected=${protectedComponents.size} " +
                    "managerAppId=$managerAppId runtimeAuthoritative=${RuntimeComponentPolicy.snapshot().authoritative}"
            )
        }.onFailure {
            Log.e(TAG, "Unable to refresh discovery policy; keeping previous snapshot", it)
        }
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        return if (runtime.authoritative) runtime else RuntimeComponentPolicySnapshot(
            managerAppId = managerAppId,
            protectedComponents = protectedComponents,
        )
    }

    private fun installPackageManagerDiscoveryHooks(classLoader: ClassLoader) {
        var installed = 0
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("PMS_CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter(::isDiscoveryQueryMethod)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "PMS#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(PMS_HOOK_ID).intercept(discoveryQueryHooker(method))
                        installed++
                        record("PMS_HOOK_INSTALLED method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("PMS_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                    }
                }
        }
        record("PMS_HOOKS_READY new=$installed total=${installedMethods.size}")
    }

    private fun isDiscoveryQueryMethod(method: Method): Boolean {
        if (method.parameterTypes.none { Intent::class.java.isAssignableFrom(it) }) return false
        val supportedResult =
            List::class.java.isAssignableFrom(method.returnType) ||
                method.returnType.name == "android.content.pm.ParceledListSlice"
        if (!supportedResult) return false
        return method.name in setOf(
            "queryIntentActivities",
            "queryIntentActivitiesAsUser",
            "queryIntentServices",
            "queryIntentServicesAsUser",
            "queryIntentReceivers",
            "queryIntentReceiversAsUser",
            "queryBroadcastReceivers",
            "queryBroadcastReceiversAsUser"
        )
    }

    private fun discoveryQueryHooker(method: Method) = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
            ?: return@Hooker chain.proceed()
        val surface = surfaceFor(method.name, intent)
            ?: return@Hooker chain.proceed()

        val callerUid = Binder.getCallingUid()
        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) {
            return@Hooker chain.proceed()
        }

        val original = chain.proceed()
        filterResolveResult(original, surface, queryUserId(chain), policy.protectedComponents)
    }

    private fun surfaceFor(methodName: String, intent: Intent): Surface? {
        val action = (intent.selector ?: intent).action ?: return null
        return when {
            action == Surface.TILE.action && "Service" in methodName ->
                Surface.TILE
            action == Surface.SHORTCUT.action && ("Activit" in methodName || "Activities" in methodName) ->
                Surface.SHORTCUT
            action == Surface.WIDGET.action && ("Receiver" in methodName || "Broadcast" in methodName) ->
                Surface.WIDGET
            else -> null
        }
    }

    private fun queryUserId(chain: XposedInterface.Chain): Int? {
        val indices = chain.args.indices.filter { index ->
            chain.args.getOrNull(index) is Int
        }
        return indices.lastOrNull()?.let { chain.args.getOrNull(it) as? Int }
    }

    private fun filterResolveResult(
        original: Any?,
        surface: Surface,
        fallbackUserId: Int?,
        protectedComponents: Set<String>,
    ): Any? {
        if (protectedComponents.isEmpty()) return original
        val result = extractListResult(original) ?: return original
        var removed = 0
        val filtered = result.values.filter { value ->
            val info = value as? ResolveInfo ?: return@filter true
            val componentInfo = when (surface) {
                Surface.TILE -> info.serviceInfo
                Surface.SHORTCUT, Surface.WIDGET -> info.activityInfo
            } ?: return@filter true
            val userId = componentUserId(componentInfo, fallbackUserId) ?: return@filter true
            val component = ComponentName(componentInfo.packageName, componentInfo.name)
            val protected = PersistentComponentState.key(userId, component) in protectedComponents
            if (protected) {
                removed++
                record(
                    "DISCOVERY_FILTERED surface=$surface user=$userId " +
                        "callerUid=${Binder.getCallingUid()} component=${component.flattenToShortString()}"
                )
            }
            !protected
        }
        if (removed == 0) return original
        return runCatching { result.rebuild(filtered) }.getOrElse {
            record("DISCOVERY_REBUILD_FAILED surface=$surface error=${it.javaClass.name}")
            original
        }
    }

    private fun componentUserId(info: android.content.pm.ComponentInfo, fallback: Int?): Int? {
        val uid = info.applicationInfo?.uid ?: -1
        if (uid >= 0) return uid / PER_USER_RANGE
        return fallback?.takeIf { it >= 0 }
    }

    private fun installAppWidgetHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName(APP_WIDGET_SERVICE, false, classLoader)
        }.getOrElse {
            record("WIDGET_CLASS_UNAVAILABLE class=$APP_WIDGET_SERVICE error=${it.javaClass.name}")
            return
        }

        var installed = 0
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { method ->
                method.name == "getInstalledProvidersForProfile" &&
                    (List::class.java.isAssignableFrom(method.returnType) ||
                        method.returnType.name == "android.content.pm.ParceledListSlice")
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = "WIDGET#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(WIDGET_HOOK_ID).intercept(widgetProviderHooker())
                    installed++
                    record("WIDGET_HOOK_INSTALLED method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("WIDGET_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        record("WIDGET_HOOKS_READY new=$installed")
    }

    private fun widgetProviderHooker() = XposedInterface.Hooker { chain ->
        val callerUid = Binder.getCallingUid()
        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) {
            return@Hooker chain.proceed()
        }

        val original = chain.proceed()
        val protectedComponents = policy.protectedComponents
        if (protectedComponents.isEmpty()) return@Hooker original

        runCatching {
            val result = extractListResult(original) ?: return@runCatching original
            val fallbackUserId = chain.args.filterIsInstance<Int>().getOrNull(1)
                ?: chain.args.filterIsInstance<Int>().lastOrNull()

            var removed = 0
            val filtered = result.values.filter { value ->
                val info = value as? AppWidgetProviderInfo ?: return@filter true
                val provider = info.provider ?: return@filter true
                val userId = fallbackUserId ?: return@filter true
                val protected = PersistentComponentState.key(userId, provider) in protectedComponents
                if (protected) {
                    removed++
                    record(
                        "WIDGET_PROVIDER_FILTERED user=$userId callerUid=$callerUid " +
                            "component=${provider.flattenToShortString()}"
                    )
                }
                !protected
            }

            if (removed == 0) original else result.rebuild(filtered)
        }.onFailure {
            record("WIDGET_FILTER_FAILED callerUid=$callerUid error=${it.javaClass.name}")
        }.getOrElse { original }
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name == "android.content.pm.ParceledListSlice" ||
            original.javaClass.name.endsWith(".ParceledListSlice") ->
            extractParceledListSlice(original)
        else -> null
    }

    private fun extractParceledListSlice(original: Any): ListResult? {
        val accessor = runCatching {
            parceledListAccessorCache.computeIfAbsent(original.javaClass) { clazz ->
                val getList = clazz.methods.firstOrNull {
                    it.name == "getList" && it.parameterCount == 0
                }?.apply { isAccessible = true }
                    ?: throw NoSuchMethodException("${clazz.name}#getList()")
                val constructor = clazz.declaredConstructors.firstOrNull { ctor ->
                    ctor.parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(ctor.parameterTypes[0])
                }?.apply { isAccessible = true }
                    ?: throw NoSuchMethodException("${clazz.name}(List)")
                ParceledListAccessor(getList, constructor)
            }
        }.onFailure {
            record("PARCELED_LIST_ACCESSOR_UNAVAILABLE class=${original.javaClass.name} error=${it.javaClass.name}")
        }.getOrNull() ?: return null

        val values = runCatching {
            @Suppress("UNCHECKED_CAST")
            accessor.getList.invoke(original) as? List<*>
        }.onFailure {
            record("PARCELED_LIST_READ_FAILED class=${original.javaClass.name} error=${it.javaClass.name}")
        }.getOrNull() ?: return null

        return ListResult(values) { filtered ->
            runCatching {
                accessor.constructor.newInstance(filtered)
            }.onFailure {
                record("PARCELED_LIST_REBUILD_FAILED class=${original.javaClass.name} error=${it.javaClass.name}")
            }.getOrElse { original }
        }
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(DIAGNOSTIC_TAG, line) }
        runCatching { log(Log.INFO, DIAGNOSTIC_TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.DiscoveryFilter"
        const val DIAGNOSTIC_TAG = "ListCleaner.DiscoveryFilter"
        const val PMS_HOOK_ID = "lc-component-discovery-pm"
        const val WIDGET_HOOK_ID = "lc-component-discovery-widget"
        const val APP_WIDGET_SERVICE = "com.android.server.appwidget.AppWidgetServiceImpl"
        const val PER_USER_RANGE = 100_000

        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService"
        )
    }
}
