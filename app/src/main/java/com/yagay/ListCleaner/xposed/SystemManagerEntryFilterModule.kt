package com.yagay.ListCleaner.xposed

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Process
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Filters final system-service lists that are cached above PackageManager.
 *
 * PackageManager discovery remains useful for candidate enumeration, but Settings reads these
 * surfaces from AccessibilityManagerService, InputMethodManagerService and PrintManagerService.
 * Internal system_server consumers are deliberately bypassed; ordinary callers and external
 * configuration UIs receive the non-destructive filtered view.
 */
class SystemManagerEntryFilterModule : XposedModule() {
    private data class CallerContext(val uid: Int, val pid: Int, val process: String?)

    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)
    private val imeCallerContext = ThreadLocal<CallerContext?>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.filter { it.kind in MANAGER_KINDS }.mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { config -> config.priorities.apps.filterKeys { it in MANAGER_KINDS } },
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        fallback.start()
        installAccessibilityHooks(param.classLoader)
        installInputMethodHooks(param.classLoader)
        installPrintHooks(param.classLoader)
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        if (runtime.authoritative) return runtime
        val local = fallback.snapshot()
        return fallbackRuntimePolicy(
            managerAppId = local.managerAppId,
            displayMode = local.displayMode,
            entryRules = local.rules,
            entryPriorities = local.priorities,
        )
    }

    private fun installAccessibilityHooks(classLoader: ClassLoader) {
        ACCESSIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            methods(clazz)
                .filter { it.name == "getInstalledAccessibilityServiceList" }
                .filter(::supportedListResult)
                .forEach { method ->
                    install(method, ACCESSIBILITY_HOOK_ID, managerListHooker(
                        kind = IntentKind.ACCESSIBILITY,
                        componentOf = ::accessibilityComponent,
                    ))
                }
        }
    }

    private fun installPrintHooks(classLoader: ClassLoader) {
        PRINT_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            methods(clazz)
                .filter { it.name == "getPrintServices" }
                .filter(::supportedListResult)
                .forEach { method ->
                    install(method, PRINT_HOOK_ID, managerListHooker(
                        kind = IntentKind.PRINT,
                        componentOf = ::printComponent,
                    ))
                }
        }
    }

    /**
     * Android 15+ may return InputMethodInfoSafeList from the Binder method. The public method is
     * hooked only to capture the original caller while its internal List-producing method runs.
     * Older List-returning Binder methods are filtered directly as well.
     */
    private fun installInputMethodHooks(classLoader: ClassLoader) {
        IME_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            methods(clazz).forEach { method ->
                when {
                    method.name in IME_OUTER_METHODS &&
                        (supportedListResult(method) || method.returnType.name.endsWith("InputMethodInfoSafeList")) ->
                        install(method, IME_OUTER_HOOK_ID, imeOuterHooker())

                    method.name == "getInputMethodListInternal" && List::class.java.isAssignableFrom(method.returnType) ->
                        install(method, IME_INTERNAL_HOOK_ID, imeInternalHooker())
                }
            }
        }
    }

    private fun managerListHooker(
        kind: IntentKind,
        componentOf: (Any?) -> ServiceInfo?,
    ) = XposedInterface.Hooker { chain ->
        val caller = currentCaller()
        val policy = effectivePolicy()
        if (!shouldFilter(caller, policy)) return@Hooker chain.proceed()

        val original = chain.proceed()
        filterResult(original, kind, caller.uid, policy, componentOf)
    }

    private fun imeOuterHooker() = XposedInterface.Hooker { chain ->
        val caller = currentCaller()
        val policy = effectivePolicy()
        if (!shouldFilter(caller, policy)) return@Hooker chain.proceed()
        if (policy.selected(IntentKind.INPUT_METHOD).isEmpty() &&
            policy.priorities(IntentKind.INPUT_METHOD).isEmpty()
        ) return@Hooker chain.proceed()

        val previous = imeCallerContext.get()
        imeCallerContext.set(caller)
        try {
            val original = chain.proceed()
            if (original is List<*>) {
                filterResult(original, IntentKind.INPUT_METHOD, caller.uid, policy, ::inputMethodComponent)
            } else {
                record(
                    "IME_BRIDGE outerResult=${original?.javaClass?.name ?: "null"} callerUid=${caller.uid} " +
                        "callerProcess=${caller.process ?: "unknown"}"
                )
                original
            }
        } finally {
            if (previous == null) imeCallerContext.remove() else imeCallerContext.set(previous)
        }
    }

    private fun imeInternalHooker() = XposedInterface.Hooker { chain ->
        val caller = imeCallerContext.get() ?: return@Hooker chain.proceed()
        val policy = effectivePolicy()
        val original = chain.proceed()
        filterResult(original, IntentKind.INPUT_METHOD, caller.uid, policy, ::inputMethodComponent)
    }

    private fun filterResult(
        original: Any?,
        kind: IntentKind,
        callerUid: Int,
        policy: RuntimeComponentPolicySnapshot,
        componentOf: (Any?) -> ServiceInfo?,
    ): Any? {
        val selected = policy.selected(kind)
        val priorities = policy.priorities(kind)
        if ((policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) && priorities.isEmpty()) {
            return original
        }
        val result = listResults.extract(original) ?: return original
        if (result.values.isEmpty()) return original

        var removed = 0
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            result.values
        } else {
            result.values.filter { value ->
                val service = componentOf(value) ?: return@filter true
                val rule = ComponentRule(kind, service.packageName, service.name)
                if (!rule.isValid()) return@filter true
                val include = policy.displayMode.includes(rule.id in selected, selected.isNotEmpty())
                if (!include) removed++
                include
            }
        }
        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> componentOf(value)?.packageName ?: "" },
            { value -> (componentOf(value)?.applicationInfo?.uid ?: 0) / PER_USER_RANGE },
        )
        if (removed == 0 && ordered == result.values) {
            record("MANAGER_HIT kind=$kind callerUid=$callerUid count=${result.values.size}")
            return original
        }
        record(
            "MANAGER_FILTER kind=$kind callerUid=$callerUid before=${result.values.size} " +
                "after=${ordered.size} removed=$removed priorities=${priorities.size}"
        )
        return result.rebuild(ordered)
    }

    private fun accessibilityComponent(value: Any?): ServiceInfo? = when (value) {
        is AccessibilityServiceInfo -> value.resolveInfo?.serviceInfo
        else -> reflectedResolveInfo(value)?.serviceInfo
    }

    /** PrintServiceInfo is hidden from the public SDK on some platform revisions; stay reflective. */
    private fun printComponent(value: Any?): ServiceInfo? =
        reflectedResolveInfo(value)?.serviceInfo

    private fun inputMethodComponent(value: Any?): ServiceInfo? = when (value) {
        is InputMethodInfo -> value.serviceInfo
        else -> runCatching {
            val method = value?.javaClass?.methods?.firstOrNull {
                it.name == "getServiceInfo" && it.parameterCount == 0
            } ?: return@runCatching null
            method.invoke(value) as? ServiceInfo
        }.getOrNull()
    }

    private fun reflectedResolveInfo(value: Any?): android.content.pm.ResolveInfo? = runCatching {
        val method = value?.javaClass?.methods?.firstOrNull {
            it.name == "getResolveInfo" && it.parameterCount == 0
        } ?: return@runCatching null
        method.invoke(value) as? android.content.pm.ResolveInfo
    }.getOrNull()

    private fun shouldFilter(caller: CallerContext, policy: RuntimeComponentPolicySnapshot): Boolean {
        if (ManagerIdentity.matches(caller.uid, policy.managerAppId)) return false
        if (FilterPolicy.ordinaryAppCaller(caller.uid)) return true
        if (caller.pid <= 0 || caller.pid == Process.myPid()) return false
        val value = caller.process?.lowercase() ?: return false
        return "settings" in value || "permissioncontroller" in value ||
            "rolecontroller" in value || "role.controller" in value
    }

    private fun currentCaller(): CallerContext {
        val uid = Binder.getCallingUid()
        val pid = Binder.getCallingPid()
        return CallerContext(uid, pid, callerProcessName(pid))
    }

    private fun callerProcessName(pid: Int): String? {
        if (pid <= 0) return null
        return runCatching {
            File("/proc/$pid/cmdline").inputStream().use { input ->
                val buffer = ByteArray(256)
                val count = input.read(buffer)
                if (count <= 0) null else String(buffer, 0, count)
                    .substringBefore('\u0000').trim().ifBlank { null }
            }
        }.getOrNull()
    }

    private fun methods(clazz: Class<*>): Sequence<Method> =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filterNot { Modifier.isAbstract(it.modifiers) || it.declaringClass.isInterface }
            .distinctBy(Method::toGenericString)

    private fun supportedListResult(method: Method): Boolean =
        List::class.java.isAssignableFrom(method.returnType) ||
            method.returnType.name.endsWith("ParceledListSlice")

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker) {
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return
        runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
        }.onFailure {
            installedMethods.remove(key)
            record(
                "HOOK_FAILED id=$id method=${method.toGenericString()} " +
                    "error=${it.javaClass.name} message=${it.message?.take(160) ?: "none"}"
            )
        }
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.SystemManagers"
        const val ACCESSIBILITY_HOOK_ID = "lc-accessibility-manager-entries"
        const val IME_OUTER_HOOK_ID = "lc-ime-manager-outer"
        const val IME_INTERNAL_HOOK_ID = "lc-ime-manager-internal"
        const val PRINT_HOOK_ID = "lc-print-manager-entries"
        const val PER_USER_RANGE = 100_000

        val MANAGER_KINDS = setOf(IntentKind.ACCESSIBILITY, IntentKind.INPUT_METHOD, IntentKind.PRINT)
        val ACCESSIBILITY_CLASSES = listOf(
            "com.android.server.accessibility.AccessibilityManagerService",
        )
        val IME_CLASSES = listOf(
            "com.android.server.inputmethod.InputMethodManagerService",
            "com.android.server.inputmethod.ZeroJankProxy",
        )
        val IME_OUTER_METHODS = setOf("getInputMethodList", "getInputMethodListLegacy")
        val PRINT_CLASSES = listOf(
            "com.android.server.print.PrintManagerService",
        )
    }
}
