package com.yagay.ListCleaner.xposed

import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ENTRY_SURFACE_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryAuthority
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.runtimeDefinition
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

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = ROLE_KINDS,
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
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else if (looksLikeRoleController(param.packageName)) {
            record("ROLE_MODEL_UNAVAILABLE package=${param.packageName} tried=${ROLE_MODEL_CLASSES.joinToString(",")}")
        }
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

        authorityWriter.replacePackages(
            kind = kind,
            packages = values.filterIsInstance<String>(),
            publishLive = true,
        )

        val policy = policyProvider.snapshot()
        val selectedPackages = policy.selectedPackages(kind)
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

    private fun roleName(role: Any): String? =
        (ReflectionAccess.invokeNoArg(role, "getName") as? String)
            ?: (ReflectionAccess.readField(role, "mName", "name") as? String)

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

        val ROLE_KINDS = ENTRY_SURFACE_DEFINITIONS.values.asSequence()
            .filter { it.authority == EntryAuthority.ROLE_CONTROLLER }
            .mapTo(linkedSetOf()) { it.kind }
        val ROLE_KIND_BY_NAME = ROLE_KINDS.mapNotNull { kind ->
            kind.runtimeDefinition()?.roleName?.let { it to kind }
        }.toMap()
        val ROLE_MODEL_CLASSES = listOf(
            "com.android.role.controller.model.Role",
            "com.android.permissioncontroller.role.model.Role",
        )
    }
}
