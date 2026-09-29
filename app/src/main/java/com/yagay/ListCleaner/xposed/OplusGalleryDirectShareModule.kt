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

/** OxygenOS Gallery embeds its own Direct Share row instead of launching Android's Chooser. */
class OplusGalleryDirectShareModule : XposedModule() {
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
        if (param.packageName != OPLUS_GALLERY_PACKAGE) return
        fallback.start()
        installAdapterHooks(param.classLoader)
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

    private fun installAdapterHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName(OPLUS_GALLERY_ADAPTER_CLASS, false, classLoader)
        }.getOrElse {
            record("ADAPTER_CLASS_UNAVAILABLE class=$OPLUS_GALLERY_ADAPTER_CLASS error=${it.javaClass.name}")
            return
        }

        var installed = 0
        clazz.declaredMethods.asSequence()
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    Intent::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = method.toGenericString()
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(HOOK_ID).intercept(adapterRefreshHooker())
                    installed++
                    record("HOOK_INSTALLED method=$key")
                }.onFailure {
                    installedMethods.remove(key)
                    record(
                        "HOOK_FAILED method=$key error=${it.javaClass.name} " +
                            "message=${it.message?.take(160) ?: "none"}"
                    )
                }
            }
        record("HOOKS_READY new=$installed total=${installedMethods.size}")
    }

    private fun adapterRefreshHooker() = XposedInterface.Hooker { chain ->
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
            record("OPLUS_LISTS_EMPTY adapter=${adapter.javaClass.name}")
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
            val ruleIds = parsed.map { parsedTarget ->
                val rule = parsedTarget.rule
                when {
                    rule == null -> null
                    rule.id in selected -> rule.id
                    else -> rule.id
                }
            }
            val keptIndices = directShareFilteredIndices(
                targetPackages = targetPackages,
                ruleIds = ruleIds,
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
                record("OPLUS_NOTIFY_FAILED error=${it.javaClass.name}")
            }
            record(
                "OPLUS_FILTER lists=$changedLists before=$beforeCount after=$afterCount parsed=$parsedCount " +
                    "selected=${selected.size} priorities=${priorities.size} observed=${observed.size} persisted=$persisted"
            )
        } else {
            record(
                "OPLUS_HIT lists=${lists.size} parsed=$parsedCount selected=${selected.size} " +
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
        val shortcut = invokeNoArg(value, "getDirectShareShortcutInfo") as? ShortcutInfo
            ?: invokeNoArg(value, "getShortcutInfo") as? ShortcutInfo
        val resolveInfo = invokeNoArg(value, "getResolveInfo") as? ResolveInfo
        val chooserComponent = invokeNoArg(value, "getChooserTargetComponentName") as? ComponentName
        val targetIntent = invokeNoArg(value, "getTargetIntent") as? Intent
        val component = chooserComponent
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
                SyntheticEntryKeys.directShareClass(component?.className, shortcutId),
            ).takeIf(ComponentRule::isValid)
        } else null
        val observed = if (rule != null) {
            ObservedEntryRecord(
                kind = IntentKind.DIRECT_SHARE.name,
                packageName = rule.packageName,
                syntheticClass = rule.className,
                label = shortcut?.shortLabel?.toString().orEmpty(),
                activityClass = component?.className,
                observedAt = System.currentTimeMillis(),
            ).validatedOrNull()
        } else null
        return ParsedTarget(packageName, rule, observed)
    }

    private fun invokeNoArg(value: Any, name: String): Any? = runCatching {
        findNoArgMethod(value.javaClass, name)?.invoke(value)
    }.getOrNull()

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
        const val TAG = "ListCleaner.OplusDirectShare"
        const val HOOK_ID = "lc-oplus-gallery-direct-share"
        const val OPLUS_GALLERY_PACKAGE = "com.oneplus.gallery"
        const val OPLUS_GALLERY_ADAPTER_CLASS =
            "com.oplus.gallery.sharepage.widget.HorizontalDirectShareRecyclerViewAdapter"
    }
}
