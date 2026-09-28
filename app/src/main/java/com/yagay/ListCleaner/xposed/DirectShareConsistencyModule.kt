package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.directShareFilteredIndices
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps Android's Direct Share row consistent with the ordinary Share resolver and applies
 * per-conversation rules in the Chooser process. system_server only observes Direct Share targets.
 */
class DirectShareConsistencyModule : XposedModule() {
    private data class ParsedTarget(
        val packageName: String?,
        val rule: ComponentRule?,
    )

    @Volatile private var processName = ""
    @Volatile private var fallbackMode = DisplayMode.HIDE_SELECTED
    @Volatile private var fallbackRules: Set<String> = emptySet()
    @Volatile private var fallbackPriorities: List<String> = emptyList()
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

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

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != FRAMEWORK_PACKAGE && param.packageName != INTENT_RESOLVER_PACKAGE) return
        initializePreferences()
        installChooserHooks(param.classLoader)
    }

    private fun initializePreferences() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }
        refreshFallback("init")
    }

    @Synchronized
    private fun refreshFallback(reason: String) {
        runCatching {
            val encoded = preferences.getString(RuleRepository.KEY_CONFIG, null) ?: return@runCatching
            if (encoded.length > RuleRepository.MAX_BACKUP_CHARS) return@runCatching
            val config = Json { ignoreUnknownKeys = true }
                .decodeFromString(ModuleConfig.serializer(), encoded).validated()
            fallbackMode = config.mode
            fallbackRules = config.rules.filter { it.kind == IntentKind.DIRECT_SHARE }
                .mapTo(linkedSetOf()) { it.id }
            fallbackPriorities = config.priorities.apps[IntentKind.DIRECT_SHARE].orEmpty()
            record("POLICY_READ reason=$reason rules=${fallbackRules.size}")
        }.onFailure { Log.w(TAG, "Unable to refresh Direct Share policy", it) }
    }

    private fun effectivePolicy(): RuntimeComponentPolicySnapshot {
        val runtime = RuntimeComponentPolicy.snapshot()
        return if (runtime.authoritative) runtime else RuntimeComponentPolicySnapshot(
            displayMode = fallbackMode,
            entryRules = fallbackRules,
            entryPriorities = if (fallbackPriorities.isEmpty()) emptyMap()
            else mapOf(IntentKind.DIRECT_SHARE to fallbackPriorities),
        )
    }

    private fun installChooserHooks(classLoader: ClassLoader) {
        var installed = 0
        CHOOSER_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter(::isDirectShareDeliveryMethod)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(directShareHooker())
                        installed++
                        record("HOOK_INSTALLED method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED method=$key error=${it.javaClass.name}")
                    }
                }
        }
        record("HOOKS_READY new=$installed total=${installedMethods.size}")
    }

    private fun isDirectShareDeliveryMethod(method: Method): Boolean =
        method.name == "sendShareShortcutInfoList" &&
            method.parameterTypes.isNotEmpty() &&
            List::class.java.isAssignableFrom(method.parameterTypes[0])

    private fun directShareHooker() = XposedInterface.Hooker { chain ->
        val targets = chain.args.getOrNull(0) as? List<*>
            ?: return@Hooker chain.proceed()
        if (targets.isEmpty()) return@Hooker chain.proceed()

        val receiver = chain.thisObject ?: return@Hooker chain.proceed()
        val visibleSharePackages = visibleSharePackages(receiver, chain.args)
            ?: return@Hooker chain.proceed()

        val parsed = targets.map(::parseDirectShareTarget)
        val policy = effectivePolicy()
        val selected = policy.entryRules.asSequence().mapNotNull(ComponentRule::fromId)
            .filter { it.kind == IntentKind.DIRECT_SHARE }
            .mapTo(linkedSetOf()) { it.id }
        val priorities = policy.entryPriorities[IntentKind.DIRECT_SHARE].orEmpty()
        val keptIndices = directShareFilteredIndices(
            targetPackages = parsed.map { it.packageName },
            ruleIds = parsed.map { it.rule?.id },
            visibleSharePackages = visibleSharePackages,
            selectedRuleIds = selected,
            displayMode = policy.displayMode,
            priorities = priorities,
        )

        if (keptIndices.size == targets.size && keptIndices.indices.all { keptIndices[it] == it }) {
            return@Hooker chain.proceed()
        }

        val replacement = chain.args.toTypedArray()
        replacement[0] = keptIndices.map { targets[it] }

        pairedPredictionListIndex(chain.args, targets.size)?.let { index ->
            val paired = chain.args[index] as List<*>
            replacement[index] = keptIndices.map { paired[it] }
        }

        record(
            "FILTER before=${targets.size} after=${keptIndices.size} " +
                "visibleApps=${visibleSharePackages.size} selected=${selected.size} priorities=${priorities.size}"
        )
        chain.proceed(replacement)
    }

    private fun parseDirectShareTarget(value: Any?): ParsedTarget {
        if (value == null) return ParsedTarget(null, null)
        val shortcut = runCatching {
            findNoArgMethod(value.javaClass, "getShortcutInfo")?.invoke(value) as? ShortcutInfo
        }.getOrNull()
        val target = runCatching {
            findNoArgMethod(value.javaClass, "getTargetComponent")?.invoke(value) as? ComponentName
        }.getOrNull()
        val packageName = target?.packageName ?: shortcut?.`package`
        val shortcutId = shortcut?.id?.takeIf { it.isNotBlank() }
        val shortcutPackage = shortcut?.`package`?.takeIf { it.isNotBlank() }
        val rule = if (shortcutId != null && shortcutPackage != null) {
            ComponentRule(
                IntentKind.DIRECT_SHARE,
                shortcutPackage,
                SyntheticEntryKeys.directShareClass(target?.className ?: shortcut?.activity?.className, shortcutId),
            ).takeIf(ComponentRule::isValid)
        } else null
        return ParsedTarget(packageName, rule)
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.apply { isAccessible = true }

    /**
     * Current Android passes ChooserListAdapter as arg1. Older implementations passed the resolved
     * app list directly. Supporting both paths keeps this hook fail-open across resolver variants.
     */
    private fun visibleSharePackages(receiver: Any, args: List<Any?>): Set<String>? {
        val second = args.getOrNull(1)
        if (second is List<*>) return second.mapNotNull(::displayTargetPackage).toSet()

        val adapter = second?.takeIf { it.javaClass.name.contains("ChooserListAdapter") }
            ?: args.drop(1).firstOrNull { it?.javaClass?.name?.contains("ChooserListAdapter") == true }
            ?: return null

        val method = generateSequence(receiver.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == "getDisplayResolveInfos" &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0].isAssignableFrom(adapter.javaClass)
            }?.apply { isAccessible = true }
            ?: return null

        val values = runCatching { method.invoke(receiver, adapter) as? List<*> }
            .getOrNull() ?: return null
        return values.mapNotNull(::displayTargetPackage).toSet()
    }

    private fun displayTargetPackage(value: Any?): String? {
        when (value) {
            is ResolveInfo -> return value.activityInfo?.packageName
            is ComponentName -> return value.packageName
            null -> return null
        }

        runCatching {
            val method = value.javaClass.methods.firstOrNull {
                it.name == "getResolveInfo" && it.parameterCount == 0
            } ?: value.javaClass.declaredMethods.firstOrNull {
                it.name == "getResolveInfo" && it.parameterCount == 0
            }?.apply { isAccessible = true }
            (method?.invoke(value) as? ResolveInfo)?.activityInfo?.packageName
        }.getOrNull()?.let { return it }

        return runCatching {
            val method = value.javaClass.methods.firstOrNull {
                it.name == "getResolvedComponentName" && it.parameterCount == 0
            } ?: value.javaClass.declaredMethods.firstOrNull {
                it.name == "getResolvedComponentName" && it.parameterCount == 0
            }?.apply { isAccessible = true }
            (method?.invoke(value) as? ComponentName)?.packageName
        }.getOrNull()
    }

    private fun pairedPredictionListIndex(args: List<Any?>, expectedSize: Int): Int? =
        args.indices.drop(1).firstOrNull { index ->
            val list = args[index] as? List<*> ?: return@firstOrNull false
            if (list.size != expectedSize || list.isEmpty()) return@firstOrNull false
            list.firstOrNull { it != null }?.javaClass?.name == APP_TARGET_CLASS
        }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.DirectShare"
        const val HOOK_ID = "lc-direct-share-consistency"
        const val FRAMEWORK_PACKAGE = "android"
        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
        const val APP_TARGET_CLASS = "android.app.prediction.AppTarget"

        val CHOOSER_CLASSES = listOf(
            "com.android.intentresolver.ChooserActivity",
            "com.android.internal.app.ChooserActivity"
        )
    }
}
