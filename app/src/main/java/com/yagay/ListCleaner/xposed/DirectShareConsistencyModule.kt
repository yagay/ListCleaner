package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.os.Process
import android.util.Log
import android.view.View
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
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
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Keeps Direct Share consistent with the ordinary Share resolver in the Chooser process. */
class DirectShareConsistencyModule : XposedModule() {
    private data class ParsedTarget(
        val packageName: String?,
        val rule: ComponentRule?,
        val observed: ObservedEntryRecord?,
    )

    private data class HiddenRowState(
        val visibility: Int,
        val layoutHeight: Int?,
    )

    @Volatile private var processName = ""
    @Volatile private var collapseEmptyDirectShare = false
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val hiddenRows: MutableMap<View, HiddenRowState> =
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
        if (param.packageName != FRAMEWORK_PACKAGE && param.packageName != INTENT_RESOLVER_PACKAGE) return
        policyProvider.start()
        installChooserHooks(param.classLoader)
        installChooserGridHooks(param.classLoader)
    }

    private fun installChooserHooks(classLoader: ClassLoader) {
        var installed = 0
        CHOOSER_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("CHOOSER_CLASS_UNAVAILABLE class=$className")
                return@forEach
            }
            ReflectionAccess.hierarchyMethods(clazz)
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

    private fun installChooserGridHooks(classLoader: ClassLoader) {
        var installed = 0
        GRID_ADAPTER_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("GRID_CLASS_UNAVAILABLE class=$className")
                return@forEach
            }
            ReflectionAccess.hierarchyMethods(clazz)
                .filter { method ->
                    method.name == "bindItemGroupViewHolder" &&
                        method.returnType == Void.TYPE &&
                        method.parameterCount == 2
                }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "grid#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(GRID_HOOK_ID).intercept(gridRowHooker())
                        installed++
                        record("GRID_HOOK_INSTALLED method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("GRID_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                    }
                }
        }
        record("GRID_HOOKS_READY new=$installed total=${installedMethods.size}")
    }

    private fun isDirectShareDeliveryMethod(method: Method): Boolean =
        method.name == "sendShareShortcutInfoList" &&
            method.parameterTypes.isNotEmpty() &&
            List::class.java.isAssignableFrom(method.parameterTypes[0])

    private fun directShareHooker() = XposedInterface.Hooker { chain ->
        collapseEmptyDirectShare = false
        val targets = chain.args.getOrNull(0) as? List<*>
            ?: return@Hooker chain.proceed()
        if (targets.isEmpty()) {
            val selected = policyProvider.snapshot().selected(IntentKind.DIRECT_SHARE)
            collapseEmptyDirectShare = selected.isNotEmpty()
            record("DELIVERY_HIT count=0 collapse=$collapseEmptyDirectShare")
            return@Hooker chain.proceed()
        }

        val parsed = targets.map(::parseDirectShareTarget)
        val observed = parsed.mapNotNull(ParsedTarget::observed)
        val persisted = observedPersistence.merge(observed)
        record(
            "DELIVERY_HIT count=${targets.size} parsed=${parsed.count { it.rule != null }} " +
                "observed=${observed.size} persisted=$persisted"
        )

        val receiver = chain.thisObject ?: return@Hooker chain.proceed()
        val visibleSharePackages = visibleSharePackages(receiver, chain.args)
            ?: return@Hooker chain.proceed()

        val policy = policyProvider.snapshot()
        val selected = policy.selected(IntentKind.DIRECT_SHARE)
        val priorities = policy.priorities(IntentKind.DIRECT_SHARE)
        val keptIndices = directShareFilteredIndices(
            targetPackages = parsed.map { it.packageName },
            ruleIds = parsed.map { it.rule?.id },
            visibleSharePackages = visibleSharePackages,
            selectedRuleIds = selected,
            displayMode = policy.displayMode,
            priorities = priorities,
        )
        collapseEmptyDirectShare = targets.isNotEmpty() && keptIndices.isEmpty()

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
            "FILTER before=${targets.size} after=${keptIndices.size} collapse=$collapseEmptyDirectShare " +
                "visibleApps=${visibleSharePackages.size} selected=${selected.size} priorities=${priorities.size}"
        )
        chain.proceed(replacement)
    }

    private fun gridRowHooker() = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        val holder = chain.args.getOrNull(1) ?: return@Hooker result
        if (!holder.javaClass.name.contains("DirectShareViewHolder")) return@Hooker result
        val itemView = viewHolderItemView(holder) ?: return@Hooker result
        val changed = if (collapseEmptyDirectShare) hideRow(itemView) else restoreRow(itemView)
        if (changed) record("EMPTY_ROW collapse=$collapseEmptyDirectShare holder=${holder.javaClass.name}")
        result
    }

    private fun viewHolderItemView(holder: Any): View? =
        ReflectionAccess.hierarchyFields(holder.javaClass)
            .firstOrNull { field ->
                field.name == "itemView" && View::class.java.isAssignableFrom(field.type)
            }
            ?.let { field -> runCatching { field.isAccessible = true; field.get(holder) as? View }.getOrNull() }

    private fun hideRow(view: View): Boolean {
        synchronized(hiddenRows) {
            if (hiddenRows.containsKey(view)) return false
            hiddenRows[view] = HiddenRowState(view.visibility, view.layoutParams?.height)
        }
        view.visibility = View.GONE
        view.layoutParams?.let { params -> params.height = 0; view.layoutParams = params }
        view.requestLayout()
        return true
    }

    private fun restoreRow(view: View): Boolean {
        val state = synchronized(hiddenRows) { hiddenRows.remove(view) } ?: return false
        view.layoutParams?.let { params -> state.layoutHeight?.let { params.height = it }; view.layoutParams = params }
        view.visibility = state.visibility
        view.requestLayout()
        return true
    }

    private fun parseDirectShareTarget(value: Any?): ParsedTarget {
        if (value == null) return ParsedTarget(null, null, null)
        val shortcut = ReflectionAccess.invokeNoArg(value, "getShortcutInfo") as? ShortcutInfo
        val target = ReflectionAccess.invokeNoArg(value, "getTargetComponent") as? ComponentName
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
        val observed = if (rule != null) {
            ObservedEntryRecord(
                kind = IntentKind.DIRECT_SHARE.name,
                packageName = rule.packageName,
                syntheticClass = rule.className,
                label = shortcut?.shortLabel?.toString().orEmpty(),
                activityClass = target?.className ?: shortcut?.activity?.className,
                observedAt = System.currentTimeMillis(),
            ).validatedOrNull()
        } else null
        return ParsedTarget(packageName, rule, observed)
    }

    private fun visibleSharePackages(receiver: Any, args: List<Any?>): Set<String>? {
        val second = args.getOrNull(1)
        if (second is List<*>) return second.mapNotNull(::displayTargetPackage).toSet()

        val adapter = second?.takeIf { it.javaClass.name.contains("ChooserListAdapter") }
            ?: args.drop(1).firstOrNull { it?.javaClass?.name?.contains("ChooserListAdapter") == true }
            ?: return null

        val method = ReflectionAccess.hierarchyMethods(receiver.javaClass)
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
        (ReflectionAccess.invokeNoArg(value, "getResolveInfo") as? ResolveInfo)
            ?.activityInfo?.packageName?.let { return it }
        return (ReflectionAccess.invokeNoArg(value, "getResolvedComponentName") as? ComponentName)?.packageName
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
        const val GRID_HOOK_ID = "lc-direct-share-empty-row"
        const val FRAMEWORK_PACKAGE = "android"
        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
        const val APP_TARGET_CLASS = "android.app.prediction.AppTarget"

        val CHOOSER_CLASSES = listOf(
            "com.android.intentresolver.ChooserActivity",
            "com.android.internal.app.ChooserActivity"
        )
        val GRID_ADAPTER_CLASSES = listOf(
            "com.android.intentresolver.grid.ChooserGridAdapter",
            "com.android.internal.app.ChooserActivity\$ChooserGridAdapter",
        )
    }
}
