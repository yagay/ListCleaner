package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.isSystemServiceEntry
import com.yagay.ListCleaner.domain.prioritizeApps
import com.yagay.ListCleaner.domain.systemServiceEntryKind
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Shared non-destructive PackageManager entry filter for role/activity/provider/service surfaces. */
class PackageManagerEntryFilterModule : XposedModule() {
    private data class QuerySpec(
        val kind: IntentKind,
        val component: (ResolveInfo) -> ComponentInfo?,
        val requiredPermission: String? = null,
        val matchByPackage: Boolean = false,
    )

    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.asSequence()
                    .filter {
                        it.kind == IntentKind.ASSISTANT ||
                            it.kind == IntentKind.DOCUMENT_PROVIDER ||
                            it.kind.isSystemServiceEntry()
                    }
                    .mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { config ->
                config.priorities.apps.filterKeys { kind ->
                    kind == IntentKind.DOCUMENT_PROVIDER || kind.isSystemServiceEntry()
                }
            },
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
        installHooks(param.classLoader)
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

    private fun installHooks(classLoader: ClassLoader) {
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter(::isSupportedQuery)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(entryHooker(method))
                        record("HOOK_INSTALLED method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED method=$key error=${it.javaClass.name}")
                    }
                }
        }
        record("HOOKS_READY total=${installedMethods.size}")
    }

    private fun isSupportedQuery(method: Method): Boolean {
        if (method.parameterTypes.none { Intent::class.java.isAssignableFrom(it) }) return false
        val supportedResult = List::class.java.isAssignableFrom(method.returnType) ||
            method.returnType.name.endsWith("ParceledListSlice")
        return supportedResult && method.name in QUERY_METHODS
    }

    private fun querySpec(method: Method, intent: Intent): QuerySpec? {
        val action = (intent.selector ?: intent).action ?: return null
        if (action == Intent.ACTION_ASSIST && "Activit" in method.name) {
            return QuerySpec(
                kind = IntentKind.ASSISTANT,
                component = { it.activityInfo },
                matchByPackage = true,
            )
        }
        if (action == DocumentsContract.PROVIDER_INTERFACE && "ContentProvider" in method.name) {
            return QuerySpec(IntentKind.DOCUMENT_PROVIDER, { it.providerInfo })
        }
        if ("Service" !in method.name) return null
        if (action == VOICE_INTERACTION_SERVICE_INTERFACE) {
            return QuerySpec(
                kind = IntentKind.ASSISTANT,
                component = { it.serviceInfo },
                requiredPermission = BIND_VOICE_INTERACTION_PERMISSION,
                matchByPackage = true,
            )
        }
        val kind = systemServiceEntryKind(action) ?: return null
        val definition = SYSTEM_SERVICE_ENTRY_DEFINITIONS.firstOrNull { it.action == action } ?: return null
        return QuerySpec(kind, { it.serviceInfo }, definition.requiredPermission)
    }

    private fun entryHooker(method: Method) = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent ?: return@Hooker chain.proceed()
        val spec = querySpec(method, intent) ?: return@Hooker chain.proceed()
        val binderUid = Binder.getCallingUid()
        val binderPid = Binder.getCallingPid()
        val callerUid = HookCallIdentity.packageManagerCallerUid(
            methodName = method.name,
            parameterTypeNames = method.parameterTypes.map { it.name },
            args = chain.args,
            binderUid = binderUid,
        )

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker chain.proceed()
        val selected = policy.selected(spec.kind)
        val priorities = policy.priorities(spec.kind)
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker chain.proceed()

        val callerProcess = callerProcessName(binderPid)
        val ordinaryCaller = FilterPolicy.ordinaryAppCaller(callerUid)
        val privilegedConfigurationUi = !ordinaryCaller && isExternalConfigurationUiCaller(binderPid, callerProcess)
        if (!ordinaryCaller && !privilegedConfigurationUi) {
            record(
                "SYSTEM_CALLER_BYPASS kind=${spec.kind} action=${(intent.selector ?: intent).action} " +
                    "callerUid=$callerUid binderUid=$binderUid callerPid=$binderPid " +
                    "callerProcess=${callerProcess ?: "unknown"}"
            )
            return@Hooker chain.proceed()
        }

        val selectedPackages = if (spec.matchByPackage) {
            selected.asSequence()
                .mapNotNull(ComponentRule::fromId)
                .mapTo(linkedSetOf()) { it.packageName }
        } else emptySet()
        record(
            "QUERY kind=${spec.kind} action=${(intent.selector ?: intent).action} callerUid=$callerUid " +
                "callerPid=$binderPid callerProcess=${callerProcess ?: "unknown"} " +
                "match=${if (spec.matchByPackage) "package" else "component"} selected=${selected.size}"
        )

        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        var removed = 0
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            result.values
        } else {
            result.values.filter { value ->
                val resolve = value as? ResolveInfo ?: return@filter true
                val component = spec.component(resolve) ?: return@filter true
                if (spec.requiredPermission != null && component is android.content.pm.ServiceInfo &&
                    component.permission != spec.requiredPermission
                ) return@filter true
                val rule = ComponentRule(spec.kind, component.packageName, component.name)
                if (!rule.isValid()) return@filter true
                val selectedMatch = if (spec.matchByPackage) {
                    component.packageName in selectedPackages
                } else {
                    rule.id in selected
                }
                val include = policy.displayMode.includes(selectedMatch, selected.isNotEmpty())
                if (!include) removed++
                include
            }
        }

        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> (value as? ResolveInfo)?.let(spec.component)?.packageName ?: "" },
            { value ->
                val uid = (value as? ResolveInfo)?.let(spec.component)?.applicationInfo?.uid ?: 0
                uid / PER_USER_RANGE
            },
        )
        if (removed == 0 && ordered == result.values) return@Hooker original
        record(
            "RESULT kind=${spec.kind} callerUid=$callerUid before=${result.values.size} " +
                "after=${ordered.size} removed=$removed priorities=${priorities.size} " +
                "emptyAllowed=${ordered.isEmpty()}"
        )
        result.rebuild(ordered)
    }

    private fun isExternalConfigurationUiCaller(pid: Int, process: String?): Boolean {
        if (pid <= 0 || pid == Process.myPid()) return false
        val value = process?.lowercase() ?: return false
        return "settings" in value || "permissioncontroller" in value ||
            "rolecontroller" in value || "role.controller" in value
    }

    private fun callerProcessName(pid: Int): String? {
        if (pid <= 0) return null
        return runCatching {
            File("/proc/$pid/cmdline").inputStream().use { input ->
                val buffer = ByteArray(256)
                val count = input.read(buffer)
                if (count <= 0) null else String(buffer, 0, count).substringBefore('\u0000').trim().ifBlank { null }
            }
        }.getOrNull()
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.PmEntries"
        const val HOOK_ID = "lc-pm-entry-filter"
        const val PER_USER_RANGE = 100_000
        const val VOICE_INTERACTION_SERVICE_INTERFACE = "android.service.voice.VoiceInteractionService"
        const val BIND_VOICE_INTERACTION_PERMISSION = "android.permission.BIND_VOICE_INTERACTION"
        val QUERY_METHODS = setOf(
            "queryIntentActivities", "queryIntentActivitiesAsUser", "queryIntentActivitiesInternal",
            "queryIntentServices", "queryIntentServicesAsUser", "queryIntentServicesInternal",
            "queryIntentContentProviders", "queryIntentContentProvidersAsUser", "queryIntentContentProvidersInternal",
        )
        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked",
        )
    }
}
