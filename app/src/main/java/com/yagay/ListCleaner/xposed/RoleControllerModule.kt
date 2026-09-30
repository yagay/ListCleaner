package com.yagay.ListCleaner.xposed

import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Filters package-level Android RoleController candidate lists for supported List Cleaner kinds. */
class RoleControllerModule : XposedModule() {
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
                config.rules.asSequence()
                    .filter { it.kind in ROLE_KINDS }
                    .mapTo(linkedSetOf()) { it.id }
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
                        record("HOOK_INSTALLED package=${param.packageName} class=$className method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record(
                            "HOOK_FAILED package=${param.packageName} class=$className method=$key " +
                                "error=${it.javaClass.name} message=${it.message?.take(160) ?: "none"}"
                        )
                    }
                }
        }
        if (installed > 0) {
            startPolicyOnce()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else if (looksLikeRoleController(param.packageName)) {
            record("ROLE_MODEL_UNAVAILABLE package=${param.packageName} tried=${ROLE_MODEL_CLASSES.joinToString(",")}")
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
        val roleName = roleName(role) ?: return@Hooker original
        val kind = ROLE_KIND_BY_NAME[roleName] ?: return@Hooker original

        startPolicyOnce()
        persistAuthoritySnapshot(kind, values)

        val policy = effectivePolicy()
        val selectedPackages = policy.selected(kind)
            .mapNotNull(ComponentRule::fromId)
            .mapTo(linkedSetOf()) { it.packageName }
        if (selectedPackages.isEmpty()) {
            record("HIT role=$roleName kind=$kind count=${values.size} selectedPackages=0 mode=${policy.displayMode}")
            return@Hooker original
        }

        record(
            "QUERY role=$roleName kind=$kind before=${values.size} " +
                "selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )
        val filtered = values.filter { value ->
            value !is String || policy.displayMode.includes(value in selectedPackages, selectedPackages.isNotEmpty())
        }
        if (filtered.size == values.size) {
            record(
                "HIT role=$roleName kind=$kind count=${values.size} " +
                    "selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
            )
            return@Hooker original
        }

        record(
            "FILTER role=$roleName kind=$kind before=${values.size} after=${filtered.size} " +
                "selectedPackages=${selectedPackages.size} mode=${policy.displayMode}"
        )
        ArrayList(filtered)
    }

    private fun persistAuthoritySnapshot(kind: IntentKind, values: List<*>) {
        val now = System.currentTimeMillis()
        val records = values.asSequence()
            .filterIsInstance<String>()
            .distinct()
            .mapNotNull { packageName ->
                val rule = runCatching { SyntheticEntryKeys.packageScopedRule(kind, packageName) }.getOrNull()
                    ?: return@mapNotNull null
                if (!rule.isValid()) return@mapNotNull null
                RuntimeObservedEntryStore.observePackage(kind, packageName)
                ObservedEntryRecord(
                    kind = kind.name,
                    packageName = packageName,
                    syntheticClass = rule.className,
                    observedAt = now,
                )
            }
            .toList()
        if (persistence.replaceKind(kind, records)) {
            record("AUTHORITY_SNAPSHOT kind=$kind count=${records.size}")
        }
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

    private fun looksLikeRoleController(packageName: String): Boolean {
        val value = packageName.lowercase()
        return "permissioncontroller" in value || "rolecontroller" in value || "role.controller" in value
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.RoleController"
        const val HOOK_ID = "lc-role-controller"

        val ROLE_KIND_BY_NAME = mapOf(
            "android.app.role.ASSISTANT" to IntentKind.ASSISTANT,
            "android.app.role.HOME" to IntentKind.HOME,
            "android.app.role.BROWSER" to IntentKind.BROWSER,
            "android.app.role.CALL_SCREENING" to IntentKind.CALL_SCREENING,
        )
        val ROLE_KINDS = ROLE_KIND_BY_NAME.values.toSet()
        val ROLE_MODEL_CLASSES = listOf(
            "com.android.role.controller.model.Role",
            "com.android.permissioncontroller.role.model.Role",
        )
    }
}
