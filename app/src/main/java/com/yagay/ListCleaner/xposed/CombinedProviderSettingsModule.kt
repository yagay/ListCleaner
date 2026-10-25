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
        val method = chain.executable as? java.lang.reflect.Method ?: return@Hooker chain.proceed()
        // AOSP buildMergedList(List<AutofillServiceInfo>, List<CredentialProviderInfo>, String).
        if (method.parameterTypes.map { it.name } != listOf("java.util.List", "java.util.List", "java.lang.String")) {
            return@Hooker chain.proceed()
        }
        val args = chain.args.toTypedArray()
        listOf(IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER).forEachIndexed { index, kind ->
            val values = args[index] as? List<*> ?: return@forEachIndexed
            val grouped = values.groupBy { providerUser(it) }
            grouped.forEach { (user, entries) ->
                if (user != null) authorityWriter.replacePackages(
                    kind, entries.mapNotNull { providerService(it)?.packageName }, userId = user,
                )
            }
            args[index] = ArrayList(values.filter { value ->
                val service = providerService(value) ?: return@filter true
                val user = providerUser(value) ?: return@filter true
                val policy = policyProvider.snapshot(user)
                val selected = policy.selectedPackages(kind)
                selected.isEmpty() || policy.displayMode.includes(service.packageName in selected, true)
            })
        }
        val original = chain.proceed(args)
        val values = original as? List<*> ?: return@Hooker original
        val ordered = values.toMutableList()
        values.indices.groupBy { mergedUser(values[it]) }.forEach { (user, positions) ->
            if (user == null) return@forEach
            val policy = policyProvider.snapshot(user)
            val ranks = (policy.priorities(IntentKind.AUTOFILL) +
                policy.priorities(IntentKind.CREDENTIAL_PROVIDER)).distinct().withIndex().associate { it.value to it.index }
            val promoted = positions.map { values[it] }.sortedBy { ranks[packageName(it)] ?: Int.MAX_VALUE }
            positions.forEachIndexed { index, position -> ordered[position] = promoted[index] }
        }
        if (ordered == values) original else ArrayList(ordered)
    }

    private fun providerService(value: Any?): android.content.pm.ServiceInfo? =
        ReflectionAccess.invokeNoArg(value, "getServiceInfo") as? android.content.pm.ServiceInfo

    private fun providerUser(value: Any?): Int? =
        providerService(value)?.applicationInfo?.uid?.takeIf { it >= 0 }?.div(100000)

    private fun mergedUser(value: Any?): Int? =
        (ReflectionAccess.invokeNoArg(value, "getApplicationInfo") as? android.content.pm.ApplicationInfo)
            ?.uid?.takeIf { it >= 0 }?.div(100000)

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

