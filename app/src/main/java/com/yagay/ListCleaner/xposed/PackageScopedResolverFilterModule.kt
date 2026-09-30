package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.intentKind
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.webTargetKind
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Package-scoped role compatibility layer for Android's resolver client process. */
class PackageScopedResolverFilterModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = RESOLVER_PACKAGE_KINDS,
            includePriorities = false,
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in RESOLVER_PACKAGES) return
        val clazz = runCatching {
            Class.forName(APPLICATION_PM_CLASS, false, param.classLoader)
        }.getOrElse {
            record("CLASS_UNAVAILABLE package=${param.packageName} error=${it.javaClass.name}")
            return
        }
        var installed = 0
        ReflectionAccess.hierarchyMethods(clazz)
            .filter(::isActivityQuery)
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = method.toGenericString()
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(HOOK_ID).intercept(queryHooker())
                    installed++
                    record("HOOK_INSTALLED package=${param.packageName} method=$key")
                }.onFailure {
                    installedMethods.remove(key)
                    record("HOOK_FAILED method=$key error=${it.javaClass.name}")
                }
            }
        if (installed > 0) {
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        }
    }

    private fun isActivityQuery(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        if (method.name !in QUERY_METHODS) return false
        if (method.parameterTypes.none { Intent::class.java.isAssignableFrom(it) }) return false
        return List::class.java.isAssignableFrom(method.returnType) ||
            method.returnType.name.endsWith("ParceledListSlice")
    }

    private fun queryHooker() = XposedInterface.Hooker { chain ->
        val outer = chain.args.firstOrNull { it is Intent } as? Intent ?: return@Hooker chain.proceed()
        val intent = outer.selector ?: outer
        if (intent.component != null || intent.`package` != null ||
            outer.component != null || outer.`package` != null
        ) return@Hooker chain.proceed()

        val kind = intent.intentKind() ?: return@Hooker chain.proceed()
        if (kind !in RESOLVER_PACKAGE_KINDS) return@Hooker chain.proceed()

        val policy = policyProvider.snapshot()
        val selected = policy.selectedPackages(kind)
        if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            return@Hooker chain.proceed()
        }

        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        if (result.values.isEmpty()) return@Hooker original

        val filtered = result.values.filter { value ->
            val resolved = value as? ResolveInfo ?: return@filter true
            val activity = resolved.activityInfo ?: return@filter true
            val effectiveKind = if (kind == IntentKind.BROWSER) resolved.webTargetKind() else kind
            if (effectiveKind != kind) return@filter true
            policy.displayMode.includes(activity.packageName in selected, selected.isNotEmpty())
        }
        if (FilterPolicy.restoreEmpty(kind.name, result.values.size, filtered.size)) {
            record("RESTORE_ORIGINAL kind=$kind before=${result.values.size} callerProcess=$processName")
            return@Hooker original
        }
        if (filtered.size == result.values.size) {
            record("HIT kind=$kind count=${result.values.size} selectedPackages=${selected.size}")
            return@Hooker original
        }
        record(
            "FILTER kind=$kind before=${result.values.size} after=${filtered.size} " +
                "selectedPackages=${selected.size} mode=${policy.displayMode}"
        )
        result.rebuild(filtered)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.PackageResolver"
        const val HOOK_ID = "lc-package-role-resolver"
        const val APPLICATION_PM_CLASS = "android.app.ApplicationPackageManager"
        val RESOLVER_PACKAGES = setOf("android", "com.android.intentresolver")
        val RESOLVER_PACKAGE_KINDS = IntentKind.entries.filterTo(linkedSetOf()) {
            it.isPackageScopedEntry() && it in setOf(IntentKind.ASSISTANT, IntentKind.HOME, IntentKind.BROWSER)
        }
        val QUERY_METHODS = setOf("queryIntentActivities", "queryIntentActivitiesAsUser")
    }
}
