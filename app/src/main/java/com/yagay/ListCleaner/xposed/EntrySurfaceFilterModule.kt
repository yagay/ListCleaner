package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.SharedPreferences
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
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Non-destructive filtering for the Storage Access Framework DocumentsProvider surface.
 *
 * The retired app-level LAUNCHER_SHORTCUT path intentionally does not install any hook here;
 * individual launcher shortcuts are owned solely by [ShortcutSurfaceModule]. Unknown/OEM result
 * shapes fail open and no package/component state is changed.
 */
class EntrySurfaceFilterModule : XposedModule() {
    @Volatile private var processName = ""
    @Volatile private var fallbackMode = DisplayMode.HIDE_SELECTED
    @Volatile private var fallbackRules: Set<String> = emptySet()
    @Volatile private var fallbackPriorities: Map<IntentKind, List<String>> = emptyMap()
    @Volatile private var fallbackManagerAppId = -1

    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refreshFallback("preference changed")
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        initializePreferences()
        installDocumentProviderHooks(param.classLoader)
    }

    private fun initializePreferences() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }
            .onFailure { Log.w(TAG, "Unable to register entry preference listener", it) }
        refreshFallback("init")
    }

    @Synchronized
    private fun refreshFallback(reason: String) {
        runCatching {
            val encoded = preferences.getString(RuleRepository.KEY_CONFIG, null) ?: return@runCatching
            if (encoded.length > RuleRepository.MAX_BACKUP_CHARS) return@runCatching
            val config = Json { ignoreUnknownKeys = true }
                .decodeFromString(ModuleConfig.serializer(), encoded)
                .validated()
            fallbackMode = config.mode
            fallbackManagerAppId = config.managerAppId
            fallbackRules = config.rules.filter { it.kind == IntentKind.DOCUMENT_PROVIDER }
                .mapTo(linkedSetOf()) { it.id }
            fallbackPriorities = config.priorities.apps.filterKeys { it == IntentKind.DOCUMENT_PROVIDER }
            record(
                "POLICY_READ reason=$reason rules=${fallbackRules.size} mode=$fallbackMode " +
                    "priorities=${fallbackPriorities.mapValues { it.value.size }}"
            )
        }.onFailure {
            Log.w(TAG, "Unable to refresh entry policy; keeping previous snapshot", it)
        }
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        return if (runtime.authoritative) runtime else RuntimeComponentPolicySnapshot(
            managerAppId = fallbackManagerAppId,
            displayMode = fallbackMode,
            entryRules = fallbackRules,
            entryPriorities = fallbackPriorities,
        )
    }

    private fun selectedForKind(kind: IntentKind, policy: RuntimeComponentPolicySnapshot): Set<String> =
        policy.entryRules.asSequence()
            .mapNotNull(ComponentRule::fromId)
            .filter { it.kind == kind }
            .map { it.id }
            .toSet()

    private fun includes(
        rule: ComponentRule,
        selectedForKind: Set<String>,
        policy: RuntimeComponentPolicySnapshot,
    ): Boolean = policy.displayMode.includes(
        selected = rule.id in selectedForKind,
        hasSelection = selectedForKind.isNotEmpty(),
    )

    private fun installDocumentProviderHooks(classLoader: ClassLoader) {
        var installed = 0
        PMS_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter(::isDocumentProviderQuery)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "DOCUMENT#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(DOCUMENT_HOOK_ID).intercept(documentProviderHooker())
                        installed++
                        record("DOCUMENT_HOOK_INSTALLED method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("DOCUMENT_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                    }
                }
        }
        record("DOCUMENT_HOOKS_READY new=$installed")
    }

    private fun isDocumentProviderQuery(method: Method): Boolean {
        if (method.name !in setOf("queryIntentContentProviders", "queryIntentContentProvidersAsUser")) return false
        if (method.parameterTypes.none { Intent::class.java.isAssignableFrom(it) }) return false
        return List::class.java.isAssignableFrom(method.returnType) ||
            method.returnType.name.endsWith("ParceledListSlice")
    }

    private fun documentProviderHooker() = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
            ?: return@Hooker chain.proceed()
        val effective = intent.selector ?: intent
        if (effective.action != DocumentsContract.PROVIDER_INTERFACE) return@Hooker chain.proceed()

        val callerUid = Binder.getCallingUid()
        if (!FilterPolicy.ordinaryAppCaller(callerUid)) return@Hooker chain.proceed()

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(callerUid, policy.managerAppId)) return@Hooker chain.proceed()

        val kind = IntentKind.DOCUMENT_PROVIDER
        val selected = selectedForKind(kind, policy)
        val priorities = policy.entryPriorities[kind].orEmpty()
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = listResults.extract(original) ?: return@Hooker original
        var removed = 0
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            result.values
        } else {
            result.values.filter { value ->
                val provider = (value as? ResolveInfo)?.providerInfo ?: return@filter true
                val rule = ComponentRule(kind, provider.packageName, provider.name)
                if (!rule.isValid()) return@filter true
                val include = includes(rule, selected, policy)
                if (!include) removed++
                include
            }
        }
        if (result.values.isNotEmpty() && filtered.isEmpty()) {
            record("DOCUMENT_RESTORE_ALL callerUid=$callerUid before=${result.values.size} reason=avoid_empty_provider_surface")
            return@Hooker original
        }

        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> (value as? ResolveInfo)?.providerInfo?.packageName ?: "" },
            { value -> ((value as? ResolveInfo)?.providerInfo?.applicationInfo?.uid ?: 0) / PER_USER_RANGE },
        )
        if (removed == 0 && ordered == result.values) return@Hooker original
        record(
            "DOCUMENT_RESULT callerUid=$callerUid before=${result.values.size} after=${ordered.size} " +
                "removed=$removed priorities=${priorities.size}"
        )
        result.rebuild(ordered)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.EntrySurface"
        const val DOCUMENT_HOOK_ID = "lc-entry-documents"
        const val PER_USER_RANGE = 100_000

        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
        )
    }
}
