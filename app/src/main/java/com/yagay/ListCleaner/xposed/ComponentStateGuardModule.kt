package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.PersistentComponentState
import com.yagay.ListCleaner.data.RuleRepository
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * system_server guard for Root-managed tile/shortcut/widget components.
 *
 * A number of apps re-enable their own manifest components during startup. Android allows an app
 * to change components that run under its own UID, so a one-shot `pm disable` is not sufficient.
 * This module keeps ListCleaner's persisted disabled policy authoritative by rejecting attempts
 * from ordinary app UIDs to switch protected components back to DEFAULT or ENABLED.
 */
class ComponentStateGuardModule : XposedModule() {
    @Volatile
    private var processName = ""

    @Volatile
    private var protectedComponents: Set<String> = emptySet()

    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == PersistentComponentState.REMOTE_KEY) {
            refreshProtectedComponents("preference changed")
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    /**
     * This entry is intentionally not hot-reloaded. Component-state hooks live in system_server;
     * a framework restart gives the new guard a clean, deterministic install while the existing
     * ListCleaner resolver entry may continue to support hot reload independently.
     */
    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        initializePreferences()
        installHooks(param.classLoader)
    }

    private fun initializePreferences() {
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        }.onFailure {
            Log.w(TAG, "Unable to register persistent component preference listener", it)
        }
        refreshProtectedComponents("init")
    }

    @Synchronized
    private fun refreshProtectedComponents(reason: String) {
        runCatching {
            protectedComponents = PersistentComponentState.sanitize(
                preferences.getStringSet(PersistentComponentState.REMOTE_KEY, emptySet()).orEmpty()
            )
            record("POLICY_READ reason=$reason count=${protectedComponents.size}")
        }.onFailure {
            Log.e(TAG, "Unable to read persistent component policy; keeping previous snapshot", it)
        }
    }

    private fun installHooks(classLoader: ClassLoader) {
        var installed = 0
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { method ->
                    method.name == "setComponentEnabledSetting" ||
                        method.name == "setComponentEnabledSettings"
                }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    val success = runCatching {
                        method.isAccessible = true
                        when (method.name) {
                            "setComponentEnabledSetting" ->
                                hook(method).setId(SINGLE_HOOK_ID).intercept(singleSettingHooker(method))
                            "setComponentEnabledSettings" ->
                                hook(method).setId(BATCH_HOOK_ID).intercept(batchSettingHooker(method))
                        }
                        record("HOOK_INSTALLED method=$key")
                        true
                    }.getOrElse {
                        installedMethods.remove(key)
                        Log.w(TAG, "Unable to hook $key", it)
                        false
                    }
                    if (success) installed++
                }
        }
        record("HOOKS_READY new=$installed total=${installedMethods.size}")
    }

    private fun singleSettingHooker(method: Method) = XposedInterface.Hooker { chain ->
        val callingUid = Binder.getCallingUid()
        if (!shouldGuardCaller(callingUid)) return@Hooker chain.proceed()

        val componentIndex = method.parameterTypes.indexOfFirst {
            ComponentName::class.java.isAssignableFrom(it)
        }
        if (componentIndex < 0) return@Hooker chain.proceed()
        val stateIndex = componentIndex + 1
        if (stateIndex !in method.parameterTypes.indices ||
            method.parameterTypes[stateIndex] != Int::class.javaPrimitiveType) {
            return@Hooker chain.proceed()
        }
        val userIndex = method.parameterTypes.indices.lastOrNull {
            method.parameterTypes[it] == Int::class.javaPrimitiveType
        } ?: return@Hooker chain.proceed()

        val component = chain.args.getOrNull(componentIndex) as? ComponentName
            ?: return@Hooker chain.proceed()
        val newState = chain.args.getOrNull(stateIndex) as? Int
            ?: return@Hooker chain.proceed()
        val userId = chain.args.getOrNull(userIndex) as? Int
            ?: return@Hooker chain.proceed()

        if (isProtectedEnable(userId, component, newState)) {
            record(
                "REENABLE_BLOCKED mode=single uid=$callingUid user=$userId " +
                    "component=${component.flattenToShortString()} state=$newState"
            )
            return@Hooker null
        }
        chain.proceed()
    }

    private fun batchSettingHooker(method: Method) = XposedInterface.Hooker { chain ->
        val callingUid = Binder.getCallingUid()
        if (!shouldGuardCaller(callingUid)) return@Hooker chain.proceed()

        val listIndex = method.parameterTypes.indexOfFirst { List::class.java.isAssignableFrom(it) }
        if (listIndex < 0) return@Hooker chain.proceed()
        val userIndex = method.parameterTypes.indices.lastOrNull {
            method.parameterTypes[it] == Int::class.javaPrimitiveType
        } ?: return@Hooker chain.proceed()
        val userId = chain.args.getOrNull(userIndex) as? Int
            ?: return@Hooker chain.proceed()
        val settings = chain.args.getOrNull(listIndex) as? List<*>
            ?: return@Hooker chain.proceed()

        var blocked = 0
        val allowed = settings.filter { setting ->
            val component = setting?.let(::settingComponentName)
            val state = setting?.let(::settingEnabledState)
            val reject = component != null && state != null && isProtectedEnable(userId, component, state)
            if (reject) {
                blocked++
                record(
                    "REENABLE_BLOCKED mode=batch uid=$callingUid user=$userId " +
                        "component=${component!!.flattenToShortString()} state=$state"
                )
            }
            !reject
        }

        if (blocked == 0) return@Hooker chain.proceed()
        if (allowed.isEmpty()) return@Hooker null

        val replacement = chain.args.toTypedArray()
        replacement[listIndex] = allowed
        chain.proceed(replacement)
    }

    private fun settingComponentName(setting: Any): ComponentName? = runCatching {
        setting.javaClass.getMethod("getComponentName").invoke(setting) as? ComponentName
    }.getOrNull()

    private fun settingEnabledState(setting: Any): Int? = runCatching {
        setting.javaClass.getMethod("getEnabledState").invoke(setting) as? Int
    }.getOrNull()

    private fun shouldGuardCaller(uid: Int): Boolean = uid >= Process.FIRST_APPLICATION_UID

    private fun isProtectedEnable(userId: Int, component: ComponentName, newState: Int): Boolean {
        if (newState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            newState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return false
        val key = PersistentComponentState.key(userId, component)
        val runtime = RuntimeComponentPolicy.snapshot()
        val protected = if (runtime.authoritative) runtime.protectedComponents else protectedComponents
        return key in protected
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(DIAGNOSTIC_TAG, line) }
        runCatching { log(Log.INFO, DIAGNOSTIC_TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.ComponentGuard"
        const val DIAGNOSTIC_TAG = "ListCleaner.ComponentGuard"
        const val SINGLE_HOOK_ID = "lc-component-state-single"
        const val BATCH_HOOK_ID = "lc-component-state-batch"
        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService"
        )
    }
}
