package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
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
 */
class SettingsEntryAuthorityModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private var policyStarted = false

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val persistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.filter { it.kind in SETTINGS_KINDS }.mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { emptyMap() },
            record = ::record,
        )
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
        if (installed > 0) {
            startPolicyOnce()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else {
            record("AUTHORITY_CLASSES_UNAVAILABLE package=${param.packageName}")
        }
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
        // AOSP exposes a convenience overload that delegates to the longer implementation. Hooking
        // both would observe the inner unfiltered list and then overwrite it with the outer filtered
        // result. Install only the terminal overload so the authority snapshot is always pristine.
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
        persistVpnSnapshot(values)

        val policy = effectivePolicy()
        val selectedPackages = selectedPackages(policy, IntentKind.VPN)
        record(
            "VPN_QUERY before=${values.size} selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )
        if (policy.displayMode == DisplayMode.SHOW_ALL || selectedPackages.isEmpty()) return@Hooker original

        val filtered = values.filter { value ->
            val packageName = vpnPackageName(value) ?: return@filter true
            policy.displayMode.includes(packageName in selectedPackages, selectedPackages.isNotEmpty())
        }
        if (filtered.size == values.size) return@Hooker original
        record("VPN_FILTER before=${values.size} after=${filtered.size} selectedPackages=${selectedPackages.size}")
        ArrayList(filtered)
    }

    /**
     * Android still exposes the legacy component picker on some builds. The main AUTOFILL catalog
     * stays package-scoped for the modern combined-provider page, while this compatibility path
     * applies the same package decision to every component row belonging to that package.
     */
    private fun legacyAutofillHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        val policy = effectivePolicy()
        val selectedPackages = selectedPackages(policy, IntentKind.AUTOFILL)
        record(
            "AUTOFILL_LEGACY_QUERY before=${values.size} selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )
        if (policy.displayMode == DisplayMode.SHOW_ALL || selectedPackages.isEmpty()) return@Hooker original

        val filtered = values.filter { value ->
            val packageName = autofillPackageName(value) ?: return@filter true
            policy.displayMode.includes(packageName in selectedPackages, selectedPackages.isNotEmpty())
        }
        if (filtered.size == values.size) return@Hooker original
        record(
            "AUTOFILL_LEGACY_FILTER before=${values.size} after=${filtered.size} " +
                "selectedPackages=${selectedPackages.size}"
        )
        ArrayList(filtered)
    }

    private fun persistVpnSnapshot(values: List<*>) {
        val now = System.currentTimeMillis()
        val records = values.asSequence()
            .mapNotNull(::vpnPackageName)
            .distinct()
            .mapNotNull { packageName ->
                val rule = runCatching { SyntheticEntryKeys.packageScopedRule(IntentKind.VPN, packageName) }.getOrNull()
                    ?: return@mapNotNull null
                if (!rule.isValid()) return@mapNotNull null
                ObservedEntryRecord(
                    kind = IntentKind.VPN.name,
                    packageName = packageName,
                    syntheticClass = rule.className,
                    observedAt = now,
                )
            }
            .toList()
        if (persistence.replaceKind(IntentKind.VPN, records)) {
            record("AUTHORITY_SNAPSHOT kind=${IntentKind.VPN} count=${records.size}")
        }
    }

    private fun selectedPackages(policy: RuntimeComponentPolicySnapshot, kind: IntentKind): Set<String> =
        policy.selected(kind).mapNotNull(ComponentRule::fromId).mapTo(linkedSetOf()) { it.packageName }

    private fun vpnPackageName(value: Any?): String? =
        reflectedString(value, "getPackageName")
            ?: reflectedField(value, "packageName") as? String
            ?: reflectedField(value, "mPackageName") as? String

    private fun autofillPackageName(value: Any?): String? {
        val direct = invokeNoArg(value, "getComponentName") as? ComponentName
            ?: reflectedField(value, "mComponentName") as? ComponentName
            ?: reflectedField(value, "componentName") as? ComponentName
        if (direct != null) return direct.packageName

        val key = reflectedString(value, "getKey")
            ?: reflectedField(value, "mKey") as? String
            ?: reflectedField(value, "key") as? String
            ?: return null
        return ComponentName.unflattenFromString(key)?.packageName
            ?: key.substringBefore('/').takeIf { it.contains('.') }
    }

    private fun reflectedString(value: Any?, methodName: String): String? =
        invokeNoArg(value, methodName) as? String

    private fun invokeNoArg(value: Any?, methodName: String): Any? = runCatching {
        val clazz = value?.javaClass ?: return@runCatching null
        val method = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?: return@runCatching null
        method.isAccessible = true
        method.invoke(value)
    }.getOrNull()

    private fun reflectedField(value: Any?, fieldName: String): Any? = runCatching {
        val clazz = value?.javaClass ?: return@runCatching null
        val field = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name == fieldName }
            ?: return@runCatching null
        field.isAccessible = true
        field.get(value)
    }.getOrNull()

    private fun startPolicyOnce() {
        if (policyStarted) return
        synchronized(this) {
            if (policyStarted) return
            fallback.start()
            policyStarted = true
        }
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        if (runtime.authoritative) return runtime
        val local = fallback.snapshot()
        return fallbackRuntimePolicy(
            managerAppId = local.managerAppId,
            displayMode = local.displayMode,
            entryRules = local.rules,
            entryPriorities = emptyMap(),
        )
    }

    private fun methods(clazz: Class<*>): Sequence<Method> =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .distinctBy(Method::toGenericString)

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
        const val VPN_HOOK_ID = "lc-settings-vpn-authority"
        const val AUTOFILL_HOOK_ID = "lc-settings-autofill-picker"
        val SETTINGS_KINDS = setOf(IntentKind.VPN, IntentKind.AUTOFILL)
    }
}
