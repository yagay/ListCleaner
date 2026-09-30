package com.yagay.ListCleaner.xposed

import android.os.Process
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
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Android 16 Settings groups Autofill and Credential providers into one package-level row. */
class CombinedProviderSettingsModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = PROVIDER_KINDS,
            includePriorities = false,
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
                        installed++
                        record("HOOK_INSTALLED class=$className method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED class=$className method=$key error=${it.javaClass.name}")
                    }
                }
        }
        if (installed > 0) {
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        }
    }

    private fun mergedListHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original

        persistObservations(values)
        if (values.isEmpty()) return@Hooker original

        val policy = policyProvider.snapshot()
        val selectedAutofill = policy.selectedPackages(IntentKind.AUTOFILL)
        val selectedCredential = policy.selectedPackages(IntentKind.CREDENTIAL_PROVIDER)
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
        val autofill = linkedSetOf<String>()
        val credential = linkedSetOf<String>()
        values.forEach { value ->
            val pkg = packageName(value) ?: return@forEach
            if (hasAutofill(value)) autofill += pkg
            if (hasCredential(value)) credential += pkg
        }
        authorityWriter.replacePackages(IntentKind.AUTOFILL, autofill)
        authorityWriter.replacePackages(IntentKind.CREDENTIAL_PROVIDER, credential)
    }

    private fun packageName(value: Any?): String? =
        ReflectionAccess.invokeNoArg(value, "getPackageName") as? String

    private fun hasAutofill(value: Any?): Boolean =
        ReflectionAccess.invokeNoArg(value, "getAutofillServiceInfo") != null

    private fun hasCredential(value: Any?): Boolean =
        (ReflectionAccess.invokeNoArg(value, "getCredentialProviderInfos") as? Collection<*>)?.isNotEmpty() == true

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.CombinedProviders"
        const val HOOK_ID = "lc-combined-provider-settings"
        const val SETTINGS_PACKAGE = "com.android.settings"
        val PROVIDER_KINDS = ENTRY_RUNTIME_DEFINITIONS.values.asSequence()
            .filter { EntryRuntimePath.COMBINED_PROVIDER_SETTINGS in it.expectedPaths }
            .mapTo(linkedSetOf()) { it.kind }
        val COMBINED_INFO_CLASSES = listOf(
            "com.android.settings.applications.credentials.CombinedProviderInfo",
        )
    }
}
