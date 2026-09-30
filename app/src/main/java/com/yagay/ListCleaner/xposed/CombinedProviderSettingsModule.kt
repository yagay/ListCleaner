package com.yagay.ListCleaner.xposed

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

/** Android 16 Settings groups Autofill and Credential providers into one package-level row. */
class CombinedProviderSettingsModule : XposedModule() {
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
                config.rules.filter { it.kind in PROVIDER_KINDS }.mapTo(linkedSetOf()) { it.id }
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
        COMBINED_INFO_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, param.classLoader) }.getOrNull()
                ?: return@forEach
            clazz.declaredMethods.asSequence()
                .filter { method ->
                    method.name == "buildMergedList" && Modifier.isStatic(method.modifiers) &&
                        List::class.java.isAssignableFrom(method.returnType)
                }
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(mergedListHooker())
                        startPolicyOnce()
                        record("HOOK_INSTALLED class=$className method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED class=$className method=$key error=${it.javaClass.name}")
                    }
                }
        }
    }

    private fun mergedListHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original

        persistObservations(values)
        if (values.isEmpty()) return@Hooker original

        val policy = effectivePolicy()
        val selectedAutofill = selectedPackages(policy, IntentKind.AUTOFILL)
        val selectedCredential = selectedPackages(policy, IntentKind.CREDENTIAL_PROVIDER)
        val hasSelection = selectedAutofill.isNotEmpty() || selectedCredential.isNotEmpty()
        if (policy.displayMode == DisplayMode.SHOW_ALL || !hasSelection) return@Hooker original

        val filtered = values.filter { value ->
            val packageName = packageName(value) ?: return@filter true
            val selected = (hasAutofill(value) && packageName in selectedAutofill) ||
                (hasCredential(value) && packageName in selectedCredential)
            policy.displayMode.includes(selected, hasSelection)
        }
        if (filtered.size == values.size) return@Hooker original
        record(
            "FILTER before=${values.size} after=${filtered.size} autofill=${selectedAutofill.size} " +
                "credential=${selectedCredential.size} mode=${policy.displayMode}"
        )
        ArrayList(filtered)
    }

    private fun persistObservations(values: List<*>) {
        val now = System.currentTimeMillis()
        val autofillRecords = mutableListOf<ObservedEntryRecord>()
        val credentialRecords = mutableListOf<ObservedEntryRecord>()
        values.forEach { value ->
            val pkg = packageName(value) ?: return@forEach
            if (hasAutofill(value)) {
                val rule = SyntheticEntryKeys.packageScopedRule(IntentKind.AUTOFILL, pkg)
                autofillRecords += ObservedEntryRecord(
                    IntentKind.AUTOFILL.name,
                    pkg,
                    rule.className,
                    observedAt = now,
                )
            }
            if (hasCredential(value)) {
                val rule = SyntheticEntryKeys.packageScopedRule(IntentKind.CREDENTIAL_PROVIDER, pkg)
                credentialRecords += ObservedEntryRecord(
                    IntentKind.CREDENTIAL_PROVIDER.name,
                    pkg,
                    rule.className,
                    observedAt = now,
                )
            }
        }
        val autofillSaved = persistence.replaceKind(IntentKind.AUTOFILL, autofillRecords.distinctBy { it.key })
        val credentialSaved = persistence.replaceKind(
            IntentKind.CREDENTIAL_PROVIDER,
            credentialRecords.distinctBy { it.key },
        )
        if (autofillSaved) record("AUTHORITY_SNAPSHOT kind=${IntentKind.AUTOFILL} count=${autofillRecords.distinctBy { it.key }.size}")
        if (credentialSaved) record("AUTHORITY_SNAPSHOT kind=${IntentKind.CREDENTIAL_PROVIDER} count=${credentialRecords.distinctBy { it.key }.size}")
    }

    private fun selectedPackages(policy: RuntimeComponentPolicySnapshot, kind: IntentKind): Set<String> =
        policy.selected(kind).mapNotNull(ComponentRule::fromId).mapTo(linkedSetOf()) { it.packageName }

    private fun packageName(value: Any?): String? = invokeNoArg(value, "getPackageName") as? String

    private fun hasAutofill(value: Any?): Boolean = invokeNoArg(value, "getAutofillServiceInfo") != null

    private fun hasCredential(value: Any?): Boolean =
        (invokeNoArg(value, "getCredentialProviderInfos") as? Collection<*>)?.isNotEmpty() == true

    private fun invokeNoArg(value: Any?, name: String): Any? = runCatching {
        val clazz = value?.javaClass ?: return@runCatching null
        val method = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return@runCatching null
        method.isAccessible = true
        method.invoke(value)
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

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.CombinedProviders"
        const val HOOK_ID = "lc-combined-provider-settings"
        const val SETTINGS_PACKAGE = "com.android.settings"
        val PROVIDER_KINDS = setOf(IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER)
        val COMBINED_INFO_CLASSES = listOf(
            "com.android.settings.applications.credentials.CombinedProviderInfo",
        )
    }
}
