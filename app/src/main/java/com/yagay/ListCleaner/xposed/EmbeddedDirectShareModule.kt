package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.EmbeddedDirectShareHostProfile
import com.yagay.ListCleaner.domain.EmbeddedDirectShareProfiles
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.directShareFilteredIndices
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Vendor-neutral compatibility layer for apps that embed their own Direct Share row.
 *
 * OEM-specific knowledge is declarative in [EmbeddedDirectShareProfiles]. The filtering,
 * observation, ordering and rule-id logic remain shared with the system Direct Share path.
 */
class EmbeddedDirectShareModule : XposedModule() {
    private data class ParsedTarget(
        val packageName: String?,
        val rule: ComponentRule?,
        val observed: ObservedEntryRecord?,
    )

    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val observedPersistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val fallback by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicyFallback(
            preferences = preferences,
            selectRules = { config ->
                config.rules.filter { it.kind == IntentKind.DIRECT_SHARE }
                    .mapTo(linkedSetOf()) { it.id }
            },
            selectPriorities = { config ->
                config.priorities.apps[IntentKind.DIRECT_SHARE]
                    ?.let { mapOf(IntentKind.DIRECT_SHARE to it) }
                    .orEmpty()
            },
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        val profiles = EmbeddedDirectShareProfiles.matching(param.packageName)
        if (profiles.isEmpty()) return
        fallback.start()
        profiles.forEach { profile -> installProfileHooks(profile, param.classLoader) }
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

    private fun installProfileHooks(profile: EmbeddedDirectShareHostProfile, classLoader: ClassLoader) {
        var installed = 0
        var availableClasses = 0
        profile.adapterClasses.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
            if (clazz == null) {
                record("ADAPTER_CLASS_UNAVAILABLE profile=${profile.id} class=$className")
                return@forEach
            }
            availableClasses++

            clazz.declaredMethods.asSequence()
                .filter { method -> matchesRefreshSignature(profile, method) }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "${profile.id}#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(adapterRefreshHooker(profile.id))
                        installed++
                        record("HOOK_INSTALLED profile=${profile.id} method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record(
                            "HOOK_FAILED profile=${profile.id} method=${method.toGenericString()} " +
                                "error=${it.javaClass.name} message=${it.message?.take(160) ?: "none"}"
                        )
                    }
                }
        }
        record(
            "PROFILE_READY profile=${profile.id} classes=$availableClasses newHooks=$installed " +
                "totalHooks=${installedMethods.size}"
        )
    }

    private fun matchesRefreshSignature(profile: EmbeddedDirectShareHostProfile, method: Method): Boolean {
        val parameters = method.parameterTypes.map { it.name }
        return profile.refreshMethods.any { signature ->
            method.returnType.name == signature.returnTypeName &&
                parameters == signature.parameterTypeNames
        }
    }

    private fun adapterRefreshHooker(profileId: String) = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val adapter = chain.thisObject ?: return@Hooker original
        val policy = effectivePolicy()
        val selected = policy.selected(IntentKind.DIRECT_SHARE)
        val priorities = policy.priorities(IntentKind.DIRECT_SHARE)
        if ((policy.displayMode == DisplayMode.SHOW_ALL || selected.isEmpty()) && priorities.isEmpty()) {
            return@Hooker original
        }

        val lists = mutableTargetLists(adapter)
        if (lists.isEmpty()) {
            record("LISTS_EMPTY profile=$profileId adapter=${adapter.javaClass.name}")
            return@Hooker original
        }

        val observed = ArrayList<ObservedEntryRecord>()
        var parsedCount = 0
        var beforeCount = 0
        var afterCount = 0
        var changedLists = 0

        lists.forEach { list ->
            val values = list.toList()
            if (values.isEmpty()) return@forEach
            val parsed = values.map(::parseTarget)
            if (parsed.none { it.rule != null }) return@forEach

            parsedCount += parsed.count { it.rule != null }
            parsed.mapNotNullTo(observed, ParsedTarget::observed)
            beforeCount += values.size

            val targetPackages = parsed.map { it.packageName }
            val keptIndices = directShareFilteredIndices(
                targetPackages = targetPackages,
                ruleIds = parsed.map { it.rule?.id },
                visibleSharePackages = targetPackages.filterNotNull().toSet(),
                selectedRuleIds = selected,
                displayMode = policy.displayMode,
                priorities = priorities,
            )
            afterCount += keptIndices.size
            if (keptIndices.size == values.size && keptIndices.indices.all { keptIndices[it] == it }) {
                return@forEach
            }

            val replacement = keptIndices.map { values[it] }
            list.clear()
            list.addAll(replacement)
            changedLists++
        }

        val persisted = observedPersistence.merge(observed)
        if (changedLists > 0) {
            runCatching {
                findNoArgMethod(adapter.javaClass, "notifyDataSetChanged")?.invoke(adapter)
            }.onFailure {
                record("NOTIFY_FAILED profile=$profileId error=${it.javaClass.name}")
            }
            record(
                "FILTER profile=$profileId lists=$changedLists before=$beforeCount after=$afterCount " +
                    "parsed=$parsedCount selected=${selected.size} priorities=${priorities.size} " +
                    "observed=${observed.size} persisted=$persisted"
            )
        } else {
            record(
                "HIT profile=$profileId lists=${lists.size} parsed=$parsedCount selected=${selected.size} " +
                    "priorities=${priorities.size} observed=${observed.size} persisted=$persisted"
            )
        }
        original
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutableTargetLists(adapter: Any): List<MutableList<Any?>> {
        val result = mutableListOf<MutableList<Any?>>()
        generateSequence(adapter.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { java.util.List::class.java.isAssignableFrom(it.type) }
            .forEach { field ->
                val value = runCatching {
                    field.isAccessible = true
                    field.get(adapter)
                }.getOrNull() as? MutableList<Any?> ?: return@forEach
                if (result.none { it === value }) result += value
            }
        return result
    }

    private fun parseTarget(value: Any?): ParsedTarget {
        if (value == null) return ParsedTarget(null, null, null)

        val shortcut = invokeFirstNoArg(value, SHORTCUT_ACCESSORS) as? ShortcutInfo
        val resolveInfo = invokeFirstNoArg(value, RESOLVE_INFO_ACCESSORS) as? ResolveInfo
        val explicitComponent = invokeFirstNoArg(value, COMPONENT_ACCESSORS) as? ComponentName
        val targetIntent = invokeFirstNoArg(value, INTENT_ACCESSORS) as? Intent
        val component = explicitComponent
            ?: targetIntent?.component
            ?: resolveInfo?.activityInfo?.let { activity ->
                val packageName = activity.packageName?.takeIf(String::isNotBlank)
                val className = activity.name?.takeIf(String::isNotBlank)
                if (packageName != null && className != null) ComponentName(packageName, className) else null
            }
            ?: shortcut?.activity

        val packageName = shortcut?.`package`?.takeIf { it.isNotBlank() }
            ?: component?.packageName?.takeIf { it.isNotBlank() }
            ?: resolveInfo?.activityInfo?.packageName?.takeIf { it.isNotBlank() }
        val shortcutId = shortcut?.id?.takeIf { it.isNotBlank() }
        val shortcutPackage = shortcut?.`package`?.takeIf { it.isNotBlank() }
        val rule = if (shortcutId != null && shortcutPackage != null) {
            ComponentRule(
                IntentKind.DIRECT_SHARE,
                shortcutPackage,
                SyntheticEntryKeys.directShareClass(
                    component?.className ?: shortcut.activity?.className,
                    shortcutId,
                ),
            ).takeIf(ComponentRule::isValid)
        } else null
        val observed = if (rule != null) {
            ObservedEntryRecord(
                kind = IntentKind.DIRECT_SHARE.name,
                packageName = rule.packageName,
                syntheticClass = rule.className,
                label = shortcut?.shortLabel?.toString().orEmpty(),
                activityClass = component?.className ?: shortcut?.activity?.className,
                observedAt = System.currentTimeMillis(),
            ).validatedOrNull()
        } else null
        return ParsedTarget(packageName, rule, observed)
    }

    private fun invokeFirstNoArg(value: Any, names: List<String>): Any? {
        names.forEach { name ->
            val result = runCatching { findNoArgMethod(value.javaClass, name)?.invoke(value) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.apply { isAccessible = true }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.EmbeddedDirectShare"
        const val HOOK_ID = "lc-embedded-direct-share"

        val SHORTCUT_ACCESSORS = listOf(
            "getDirectShareShortcutInfo",
            "getShortcutInfo",
        )
        val RESOLVE_INFO_ACCESSORS = listOf(
            "getResolveInfo",
        )
        val COMPONENT_ACCESSORS = listOf(
            "getChooserTargetComponentName",
            "getResolvedComponentName",
            "getTargetComponent",
        )
        val INTENT_ACCESSORS = listOf(
            "getTargetIntent",
            "getResolvedIntent",
        )
    }
}
