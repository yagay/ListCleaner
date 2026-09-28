package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Binder
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
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
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Non-destructive filters for entry surfaces that are not ordinary Activity resolver results.
 *
 * - Launcher shortcuts: filters ShortcutService results by their owning launcher Activity.
 * - Storage locations: filters DocumentsProvider discovery used by the Storage Access Framework.
 *
 * No package/component state is changed. Unknown/OEM result shapes fail open.
 */
class EntrySurfaceFilterModule : XposedModule() {
    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)
    private data class ParceledListAccessor(val getList: Method, val constructor: Constructor<*>)

    @Volatile private var processName = ""
    @Volatile private var fallbackMode = DisplayMode.HIDE_SELECTED
    @Volatile private var fallbackRules: Set<String> = emptySet()
    @Volatile private var fallbackPriorities: Map<IntentKind, List<String>> = emptyMap()
    @Volatile private var fallbackManagerAppId = -1

    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val parceledListAccessorCache = ConcurrentHashMap<Class<*>, ParceledListAccessor>()

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
        installShortcutHooks(param.classLoader)
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
            fallbackRules = specialRules(config.rules.map { it.id }.toSet())
            fallbackPriorities = config.priorities.apps.filterKeys(::isSpecialKind)
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

    private fun isSpecialKind(kind: IntentKind): Boolean =
        kind == IntentKind.LAUNCHER_SHORTCUT || kind == IntentKind.DOCUMENT_PROVIDER

    private fun specialRules(ids: Set<String>): Set<String> = ids.filterTo(linkedSetOf()) { id ->
        ComponentRule.fromId(id)?.kind?.let(::isSpecialKind) == true
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

    private fun installShortcutHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName(SHORTCUT_LOCAL_SERVICE, false, classLoader)
        }.getOrElse {
            record("SHORTCUT_CLASS_UNAVAILABLE error=${it.javaClass.name}")
            return
        }
        var installed = 0
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { method ->
                method.name == "getShortcuts" && List::class.java.isAssignableFrom(method.returnType)
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = "SHORTCUT#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(SHORTCUT_HOOK_ID).intercept(shortcutHooker())
                    installed++
                    record("SHORTCUT_HOOK_INSTALLED method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("SHORTCUT_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        record("SHORTCUT_HOOKS_READY new=$installed")
    }

    private fun shortcutHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        if (values.isEmpty()) return@Hooker original

        val policy = effectivePolicy()
        val kind = IntentKind.LAUNCHER_SHORTCUT
        val selected = selectedForKind(kind, policy)
        val priorities = policy.entryPriorities[kind].orEmpty()
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker original

        var removed = 0
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            values
        } else {
            values.filter { value ->
                val shortcut = value as? ShortcutInfo ?: return@filter true
                val activity = shortcut.activity ?: return@filter true
                val rule = ComponentRule(kind, activity.packageName, activity.className)
                val include = rule.isValid() && includes(rule, selected, policy)
                if (!include) removed++
                include
            }
        }

        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> (value as? ShortcutInfo)?.`package` ?: "" },
            { 0 },
        )
        if (removed == 0 && ordered == values) return@Hooker original
        record(
            "SHORTCUT_RESULT before=${values.size} after=${ordered.size} removed=$removed " +
                "priorities=${priorities.size}"
        )
        ordered
    }

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

        val policy = effectivePolicy()
        if (ManagerIdentity.matches(Binder.getCallingUid(), policy.managerAppId)) {
            return@Hooker chain.proceed()
        }

        val kind = IntentKind.DOCUMENT_PROVIDER
        val selected = selectedForKind(kind, policy)
        val priorities = policy.entryPriorities[kind].orEmpty()
        if (selected.isEmpty() && priorities.isEmpty()) return@Hooker chain.proceed()

        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        var removed = 0
        val filtered = if (policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            result.values
        } else {
            result.values.filter { value ->
                val provider = (value as? ResolveInfo)?.providerInfo ?: return@filter true
                val rule = ComponentRule(kind, provider.packageName, provider.name)
                val include = rule.isValid() && includes(rule, selected, policy)
                if (!include) removed++
                include
            }
        }
        if (result.values.isNotEmpty() && filtered.isEmpty()) {
            record("DOCUMENT_RESTORE_ALL before=${result.values.size} reason=avoid_empty_provider_surface")
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
            "DOCUMENT_RESULT before=${result.values.size} after=${ordered.size} removed=$removed " +
                "priorities=${priorities.size}"
        )
        runCatching { result.rebuild(ordered) }.getOrElse { original }
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> extractParceledListSlice(original)
        else -> null
    }

    private fun extractParceledListSlice(original: Any): ListResult? {
        val accessor = parceledListAccessorCache.computeIfAbsent(original.javaClass) { clazz ->
            val getList = clazz.methods.firstOrNull { it.name == "getList" && it.parameterCount == 0 }
                ?.apply { isAccessible = true }
                ?: throw NoSuchMethodException("${clazz.name}#getList()")
            val constructor = clazz.declaredConstructors.firstOrNull { ctor ->
                ctor.parameterTypes.size == 1 && List::class.java.isAssignableFrom(ctor.parameterTypes[0])
            }?.apply { isAccessible = true }
                ?: throw NoSuchMethodException("${clazz.name}(List)")
            ParceledListAccessor(getList, constructor)
        }
        val values = accessor.getList.invoke(original) as? List<*> ?: return null
        return ListResult(values) { filtered -> accessor.constructor.newInstance(filtered) }
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.EntrySurface"
        const val SHORTCUT_HOOK_ID = "lc-entry-shortcuts"
        const val DOCUMENT_HOOK_ID = "lc-entry-documents"
        const val SHORTCUT_LOCAL_SERVICE = "com.android.server.pm.ShortcutService\$LocalService"
        const val PER_USER_RANGE = 100_000

        val PMS_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
        )
    }
}
