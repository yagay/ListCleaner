package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.os.Process
import com.yagay.ListCleaner.domain.AndroidUid
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.ENTRY_RUNTIME_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryRuntimePath
import com.yagay.ListCleaner.domain.IntentKind
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Hooks Settings-owned final candidate lists that do not have a system_server manager API.
 * The unmodified Settings result is persisted first, then List Cleaner applies its package rules.
 * Dream ordering is a separate best-effort presentation hook: it changes returned candidates
 * only, not the selected dream or enabled state. OEM Settings may ignore this list.
 */
class SettingsEntryAuthorityModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = SETTINGS_KINDS,
            includePriorities = true,
            record = ::record,
        )
    }
    private val authorityWriter by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AuthoritySnapshotWriter(preferences, ::record)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SETTINGS_PACKAGE) return
        var installed = 0
        installed += installVpnHooks(param.classLoader)
        installed += installLegacyAutofillHooks(param.classLoader)
        installed += installDreamOrderingHooks(param.classLoader)
        if (installed > 0) {
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else {
            record("AUTHORITY_CLASSES_UNAVAILABLE package=${param.packageName}")
        }
    }

    /**
     * AOSP Settings DreamBackend returns DreamInfo rows already alphabetized.
     * Reorder only the returned presentation list, never enabled/default dream state.
     * Missing or changed OEM implementations are ignored without side effects.
     */
    private fun installDreamOrderingHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching {
            Class.forName("com.android.settingslib.dream.DreamBackend", false, classLoader)
        }.getOrNull() ?: runCatching {
            Class.forName("com.android.settings.dream.DreamBackend", false, classLoader)
        }.getOrNull() ?: return 0
        var installed = 0
        methods(clazz).filter { method ->
            method.name == "getDreamInfos" && method.parameterCount == 0 &&
                List::class.java.isAssignableFrom(method.returnType)
        }.forEach { method ->
            if (install(method, DREAM_HOOK_ID, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val values = original as? List<*> ?: return@Hooker original
                if (values.size < 2) return@Hooker original
                val userId = AndroidUid.userId(Process.myUid())
                val ranking = policyProvider.snapshot(userId).priorities(IntentKind.DREAM)
                if (ranking.isEmpty()) return@Hooker original
                val ordered = com.yagay.ListCleaner.domain.prioritizeApps(
                    values, ranking,
                    { item ->
                        val component = ReflectionAccess.readField(item, "componentName", "mComponentName")
                            as? ComponentName
                        component?.packageName.orEmpty()
                    },
                    { userId },
                )
                if (ordered == values) original else ArrayList(ordered)
            })) installed++
        }
        return installed
    }

    private fun installVpnHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching { Class.forName(VPN_SETTINGS_CLASS, false, classLoader) }.getOrNull()
            ?: return 0
        val terminal = methods(clazz)
            .filter { method ->
                method.name == "getVpnApps" && Modifier.isStatic(method.modifiers) &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
            .maxByOrNull(Method::getParameterCount)
            ?: return 0
        return if (install(terminal, VPN_HOOK_ID, vpnHooker())) 1 else 0
    }

    private fun installLegacyAutofillHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching { Class.forName(DEFAULT_AUTOFILL_PICKER_CLASS, false, classLoader) }.getOrNull()
            ?: return 0
        var installed = 0
        methods(clazz)
            .filter { method ->
                method.name == "getCandidates" && method.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
            .forEach { method ->
                if (install(method, AUTOFILL_HOOK_ID, legacyAutofillHooker())) installed++
            }
        return installed
    }

    private fun vpnHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        values.groupBy(::vpnUserId).forEach { (userId, entries) ->
            if (userId != null) authorityWriter.replacePackages(
                IntentKind.VPN, entries.mapNotNull(::vpnPackageName), userId = userId,
            )
        }
        val filtered = values.filter { value ->
            val userId = vpnUserId(value) ?: return@filter true
            val packageName = vpnPackageName(value) ?: return@filter true
            val policy = policyProvider.snapshot(userId)
            val selected = policy.selectedPackages(IntentKind.VPN)
            selected.isEmpty() || policy.displayMode.includes(packageName in selected, true)
        }.toMutableList()
        filtered.indices.groupBy { vpnUserId(filtered[it]) }.forEach { (userId, positions) ->
            if (userId == null) return@forEach
            val policy = policyProvider.snapshot(userId)
            val ordered = com.yagay.ListCleaner.domain.prioritizeApps(
                positions.map { filtered[it] }, policy.priorities(IntentKind.VPN),
                { vpnPackageName(it).orEmpty() }, { userId },
            )
            positions.forEachIndexed { index, position -> filtered[position] = ordered[index] }
        }
        if (filtered == values) original else ArrayList(filtered)
    }

    private fun vpnUserId(value: Any?): Int? =
        ((ReflectionAccess.readField(value, "userId", "mUserId") as? Number)?.toInt()
            ?: (ReflectionAccess.invokeNoArg(value, "getUserId") as? Number)?.toInt())
            ?.takeIf { it in 0..21474 }

    /** Compatibility path for builds that still expose the legacy component picker. */
    private fun legacyAutofillHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        val packages = values.mapNotNull(::autofillPackageName)
        val userId = (ReflectionAccess.readField(chain.thisObject, "mUserId") as? Number)
            ?.toInt()?.takeIf { it in 0..21474 } ?: return@Hooker original
        authorityWriter.replacePackages(IntentKind.AUTOFILL, packages, userId = userId)

        val policy = policyProvider.snapshot(userId)
        val selectedPackages = policy.selectedPackages(IntentKind.AUTOFILL)
        record(
            "AUTOFILL_LEGACY_QUERY before=${values.size} selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )


        val filtered = values.filter { value ->
            val packageName = autofillPackageName(value) ?: return@filter true
            policy.displayMode.includes(packageName in selectedPackages, selectedPackages.isNotEmpty())
        }
        val ordered = com.yagay.ListCleaner.domain.prioritizeApps(
            filtered, policy.priorities(IntentKind.AUTOFILL),
            { autofillPackageName(it).orEmpty() }, { userId },
        )
        if (ordered == values) return@Hooker original
        record(
            "AUTOFILL_LEGACY_FILTER before=${values.size} after=${filtered.size} " +
                "selectedPackages=${selectedPackages.size}"
        )
        ArrayList(ordered)
    }

    private fun vpnPackageName(value: Any?): String? =
        ReflectionAccess.readString(value, "getPackageName")
            ?: ReflectionAccess.readField(value, "packageName", "mPackageName") as? String

    private fun autofillPackageName(value: Any?): String? {
        val direct = ReflectionAccess.invokeNoArg(value, "getComponentName") as? ComponentName
            ?: ReflectionAccess.readField(value, "mComponentName", "componentName") as? ComponentName
        if (direct != null) return direct.packageName

        val key = ReflectionAccess.readString(value, "getKey")
            ?: ReflectionAccess.readField(value, "mKey", "key") as? String
            ?: return null
        return ComponentName.unflattenFromString(key)?.packageName
            ?: key.substringBefore('/').takeIf { it.contains('.') }
    }

    private fun methods(clazz: Class<*>): Sequence<Method> =
        ReflectionAccess.hierarchyMethods(clazz).distinctBy(Method::toGenericString)

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker): Boolean {
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return false
        return runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
            true
        }.onFailure {
            installedMethods.remove(key)
            record("HOOK_FAILED id=$id method=${method.toGenericString()} error=${it.javaClass.name}")
        }.getOrDefault(false)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.SettingsAuthority"
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val VPN_SETTINGS_CLASS = "com.android.settings.vpn2.VpnSettings"
        const val DEFAULT_AUTOFILL_PICKER_CLASS =
            "com.android.settings.applications.defaultapps.DefaultAutofillPicker"
        const val DREAM_HOOK_ID = "lc-settings-dream-order"
        const val VPN_HOOK_ID = "lc-settings-vpn-authority"
        const val AUTOFILL_HOOK_ID = "lc-settings-autofill-picker"

        val SETTINGS_PATHS = setOf(EntryRuntimePath.SETTINGS_VPN, EntryRuntimePath.SETTINGS_AUTOFILL_PICKER)
        val SETTINGS_KINDS = ENTRY_RUNTIME_DEFINITIONS.values.asSequence()
            .filter { definition -> definition.expectedPaths.any { it in SETTINGS_PATHS } }
            .mapTo(linkedSetOf()) { it.kind }.apply { add(IntentKind.DREAM) }
    }
}

