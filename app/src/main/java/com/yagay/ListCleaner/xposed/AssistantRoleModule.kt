package com.yagay.ListCleaner.xposed

import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Filters Android's Assistant role picker at the final qualifying-package layer.
 *
 * ACTION_ASSIST queries alone are not enough: PermissionController builds the default-assistant
 * menu from Role.getQualifyingPackagesAsUser(), combining activity and VoiceInteractionService
 * qualification. Filtering here keeps Assistant rules effective without altering package state.
 */
class AssistantRoleModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private var policyStarted = false

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.filter { it.kind == IntentKind.ASSISTANT }
                    .mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { emptyMap() },
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        var installed = 0
        ROLE_MODEL_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, param.classLoader) }.getOrNull()
                ?: return@forEach
            clazz.declaredMethods.asSequence()
                .filter(::isQualifyingPackagesMethod)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(qualifyingPackagesHooker())
                        installed++
                        record("HOOK_INSTALLED package=${param.packageName} method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record(
                            "HOOK_FAILED package=${param.packageName} method=$key " +
                                "error=${it.javaClass.name} message=${it.message?.take(160) ?: "none"}"
                        )
                    }
                }
        }
        if (installed > 0) {
            startPolicyOnce()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        }
    }

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

    private fun isQualifyingPackagesMethod(method: Method): Boolean =
        method.name == "getQualifyingPackagesAsUser" &&
            List::class.java.isAssignableFrom(method.returnType)

    private fun qualifyingPackagesHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        val role = chain.thisObject ?: return@Hooker original
        if (roleName(role) != ROLE_ASSISTANT) return@Hooker original

        startPolicyOnce()
        val policy = effectivePolicy()
        val selectedPackages = policy.selected(IntentKind.ASSISTANT)
            .mapNotNull(ComponentRule::fromId)
            .mapTo(linkedSetOf()) { it.packageName }
        if (selectedPackages.isEmpty()) return@Hooker original

        val hasSelection = selectedPackages.isNotEmpty()
        val filtered = values.filter { value ->
            value !is String || policy.displayMode.includes(value in selectedPackages, hasSelection)
        }
        if (filtered.size == values.size) {
            record(
                "HIT role=$ROLE_ASSISTANT count=${values.size} selectedPackages=${selectedPackages.size} " +
                    "mode=${policy.displayMode}"
            )
            return@Hooker original
        }

        record(
            "FILTER role=$ROLE_ASSISTANT before=${values.size} after=${filtered.size} " +
                "selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )
        ArrayList(filtered)
    }

    private fun roleName(role: Any): String? {
        val getter = generateSequence(role.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.name == "getName" && method.parameterCount == 0 && method.returnType == String::class.java
            }
        runCatching {
            getter?.isAccessible = true
            getter?.invoke(role) as? String
        }.getOrNull()?.let { return it }

        val field = generateSequence(role.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { candidate ->
                candidate.type == String::class.java && candidate.name in setOf("mName", "name")
            }
        return runCatching {
            field?.isAccessible = true
            field?.get(role) as? String
        }.getOrNull()
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.AssistantRole"
        const val HOOK_ID = "lc-assistant-role"
        const val ROLE_ASSISTANT = "android.app.role.ASSISTANT"

        val ROLE_MODEL_CLASSES = listOf(
            "com.android.role.controller.model.Role",
            "com.android.permissioncontroller.role.model.Role",
        )
    }
}
