package com.yagay.ListCleaner.xposed

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Process
import android.util.Log
import android.view.View
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Vendor-neutral compatibility layer for apps that embed their own Direct Share row. */
class EmbeddedDirectShareModule : XposedModule() {
    private data class ParsedTarget(
        val packageName: String?,
        val rule: ComponentRule?,
        val observed: ObservedEntryRecord?,
    )

    private data class HiddenViewState(
        val visibility: Int,
        val layoutHeight: Int?,
    )

    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val hiddenViews: MutableMap<View, HiddenViewState> =
        Collections.synchronizedMap(WeakHashMap())
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val observedPersistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = setOf(IntentKind.DIRECT_SHARE),
            includePriorities = true,
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
        policyProvider.start()
        profiles.forEach { profile -> installProfileHooks(profile, param.classLoader) }
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
                        hook(method).setId(HOOK_ID).intercept(adapterRefreshHooker(profile))
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
            method.returnType.name == signature.returnTypeName && parameters == signature.parameterTypeNames
        }
    }

    private fun adapterRefreshHooker(profile: EmbeddedDirectShareHostProfile) = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val adapter = chain.thisObject ?: return@Hooker original
        val policy = policyProvider.snapshot()
        val selected = policy.selected(IntentKind.DIRECT_SHARE)
        val priorities = policy.priorities(IntentKind.DIRECT_SHARE)
        val filteringActive =
            (policy.displayMode != DisplayMode.SHOW_ALL && selected.isNotEmpty()) || priorities.isNotEmpty()
        if (!filteringActive) {
            updateEmptySurface(profile, adapter, empty = false)
            return@Hooker original
        }

        val lists = mutableTargetLists(adapter)
        if (lists.isEmpty()) {
            val empty = adapterItemCount(adapter)?.let { it <= 0 } ?: false
            updateEmptySurface(profile, adapter, empty)
            record("LISTS_EMPTY profile=${profile.id} adapter=${adapter.javaClass.name} empty=$empty")
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

        val empty = adapterItemCount(adapter)?.let { it <= 0 }
            ?: (parsedCount > 0 && afterCount == 0)
        updateEmptySurface(profile, adapter, empty)

        val persisted = observedPersistence.merge(observed)
        if (changedLists > 0) {
            runCatching { ReflectionAccess.noArgMethod(adapter.javaClass, "notifyDataSetChanged")?.invoke(adapter) }
                .onFailure { record("NOTIFY_FAILED profile=${profile.id} error=${it.javaClass.name}") }
            record(
                "FILTER profile=${profile.id} lists=$changedLists before=$beforeCount after=$afterCount " +
                    "parsed=$parsedCount selected=${selected.size} priorities=${priorities.size} " +
                    "empty=$empty observed=${observed.size} persisted=$persisted"
            )
        } else {
            record(
                "HIT profile=${profile.id} lists=${lists.size} parsed=$parsedCount selected=${selected.size} " +
                    "priorities=${priorities.size} empty=$empty observed=${observed.size} persisted=$persisted"
            )
        }
        original
    }

    private fun updateEmptySurface(profile: EmbeddedDirectShareHostProfile, adapter: Any, empty: Boolean) {
        if (profile.collapseWhenEmptyResourceNames.isEmpty()) return
        val root = findHostRoot(adapter) ?: return
        val resources = root.resources
        val packageName = root.context.packageName
        var changed = 0

        profile.collapseWhenEmptyResourceNames.forEach { entryName ->
            val resourceId = resources.getIdentifier(entryName, "id", packageName)
            if (resourceId == 0) return@forEach
            val view = root.findViewById<View>(resourceId) ?: return@forEach
            val didChange = if (empty) hideView(view) else restoreView(view)
            if (didChange) changed++
        }

        if (changed > 0) record("EMPTY_SURFACE profile=${profile.id} empty=$empty changedViews=$changed")
    }

    private fun hideView(view: View): Boolean {
        synchronized(hiddenViews) {
            if (hiddenViews.containsKey(view)) return false
            hiddenViews[view] = HiddenViewState(view.visibility, view.layoutParams?.height)
        }
        view.visibility = View.GONE
        view.layoutParams?.let { params -> params.height = 0; view.layoutParams = params }
        view.requestLayout()
        return true
    }

    private fun restoreView(view: View): Boolean {
        val state = synchronized(hiddenViews) { hiddenViews.remove(view) } ?: return false
        view.layoutParams?.let { params -> state.layoutHeight?.let { params.height = it }; view.layoutParams = params }
        view.visibility = state.visibility
        view.requestLayout()
        return true
    }

    private fun findHostRoot(adapter: Any): View? {
        ReflectionAccess.hierarchyFields(adapter.javaClass).forEach { field ->
            if (!View::class.java.isAssignableFrom(field.type)) return@forEach
            val view = readField(field, adapter) as? View
            if (view != null) return view.rootView ?: view
        }
        ReflectionAccess.hierarchyFields(adapter.javaClass).forEach { field ->
            if (!Context::class.java.isAssignableFrom(field.type)) return@forEach
            val context = readField(field, adapter) as? Context ?: return@forEach
            val activity = unwrapActivity(context) ?: return@forEach
            return activity.window?.decorView
        }
        return null
    }

    private fun unwrapActivity(context: Context): Activity? {
        var current: Context? = context
        repeat(10) {
            when (val value = current) {
                is Activity -> return value
                is ContextWrapper -> {
                    val next = value.baseContext
                    if (next === value) return null
                    current = next
                }
                else -> return null
            }
        }
        return null
    }

    private fun readField(field: Field, receiver: Any): Any? = runCatching {
        field.isAccessible = true
        field.get(receiver)
    }.getOrNull()

    private fun adapterItemCount(adapter: Any): Int? =
        (ReflectionAccess.invokeNoArg(adapter, "getItemCount") as? Number)?.toInt()

    @Suppress("UNCHECKED_CAST")
    private fun mutableTargetLists(adapter: Any): List<MutableList<Any?>> {
        val result = mutableListOf<MutableList<Any?>>()
        ReflectionAccess.hierarchyFields(adapter.javaClass)
            .filter { java.util.List::class.java.isAssignableFrom(it.type) }
            .forEach { field ->
                val value = readField(field, adapter) as? MutableList<Any?> ?: return@forEach
                if (result.none { it === value }) result += value
            }
        return result
    }

    private fun parseTarget(value: Any?): ParsedTarget {
        if (value == null) return ParsedTarget(null, null, null)

        val shortcut = ReflectionAccess.invokeFirstNoArg(value, SHORTCUT_ACCESSORS) as? ShortcutInfo
        val resolveInfo = ReflectionAccess.invokeFirstNoArg(value, RESOLVE_INFO_ACCESSORS) as? ResolveInfo
        val explicitComponent = ReflectionAccess.invokeFirstNoArg(value, COMPONENT_ACCESSORS) as? ComponentName
        val targetIntent = ReflectionAccess.invokeFirstNoArg(value, INTENT_ACCESSORS) as? Intent
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
                    component?.className ?: shortcut?.activity?.className,
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

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.EmbeddedDirectShare"
        const val HOOK_ID = "lc-embedded-direct-share"

        val SHORTCUT_ACCESSORS = listOf("getDirectShareShortcutInfo", "getShortcutInfo")
        val RESOLVE_INFO_ACCESSORS = listOf("getResolveInfo")
        val COMPONENT_ACCESSORS = listOf(
            "getChooserTargetComponentName",
            "getResolvedComponentName",
            "getTargetComponent",
        )
        val INTENT_ACCESSORS = listOf("getTargetIntent", "getResolvedIntent")
    }
}
