package com.yagay.ListCleaner.xposed

import android.app.Notification
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.domain.supportsSurfaceOrdering
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * AOSP-only presentation filtering. Hooks return copies of UI lists, never mutate an Android
 * service's canonical registry, stop tasks, cancel notifications, revoke URI access or disable apps.
 * Every path is gated on the actual AOSP owner class/method and fails open on unexpected shapes.
 */
class AospSurfaceFilterModule : XposedModule() {
    private val installed = ConcurrentHashMap.newKeySet<String>()
    private val preferences by lazy { getRemotePreferences(RuleRepository.REMOTE_PREFS) }
    private val json = Json { ignoreUnknownKeys = true }
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refreshConfig()
    }
    @Volatile private var profiles: Map<Int, SurfaceFilterConfig> = emptyMap()
    @Volatile private var process = ""
    override fun onModuleLoaded(param: ModuleLoadedParam) { process = param.processName }
    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        when (param.packageName) {
            "com.android.systemui" -> {
                initRules()
                // The Root Tiles editor writes the actual per-component SystemUI
                // label override. Intercept the presented CustomTile state, not
                // the TileService's APK resources or stored Tile registry.
                install(param.classLoader,
                    "com.android.systemui.qs.external.CustomTile",
                    "handleUpdateState", 2, "qs-system-label-state") { chain ->
                    val result = proceedOriginal(chain)
                    val renamed = customTileLabel(chain.thisObject) ?: return@install result
                    val state = chain.args.firstOrNull() ?: return@install result
                    val oldLabel = ReflectionAccess.readField(state, "label")
                    if (ReflectionAccess.writeField(state, renamed, "label")) {
                        val description = ReflectionAccess.readField(state, "contentDescription")
                        if (description == null || description == oldLabel) {
                            ReflectionAccess.writeField(state, renamed, "contentDescription")
                        }
                    }
                    result
                }
                install(param.classLoader,
                    "com.android.systemui.qs.external.CustomTile",
                    "getTileLabel", 0, "qs-system-label-editor") { chain ->
                    val original = proceedOriginal(chain)
                    customTileLabel(chain.thisObject) ?: original
                }
                // RenderStageManager is the AOSP hand-off to the view renderer:
                // filter its presentation argument, not the notification collection.
                install(param.classLoader,
                    "com.android.systemui.statusbar.notification.collection.render.RenderStageManager",
                    "onRenderList", 1, "shade") { chain ->
                    val args = chain.args.toTypedArray()
                    val entries = args[0] as? List<*> ?: return@install proceedOriginal(chain)
                    if (profiles.values.all {
                            it.selected(SurfaceKind.NOTIFICATION_SHADE).isEmpty() &&
                                it.hiddenNotificationChannels.isEmpty() &&
                                it.hiddenNotificationCategories.isEmpty()
                        }) return@install proceedOriginal(chain)
                    val filtered = entries.filterNot { item ->
                        shouldHideNotification(item)
                    }
                    if (filtered.size == entries.size) proceedOriginal(chain)
                    else {
                        args[0] = ArrayList(filtered)
                        proceedOriginal(chain, args)
                    }
                }
                install(param.classLoader, "com.android.systemui.qs.customize.TileAdapter",
                    "onTilesChanged", 1, "qs") { chain ->
                    val args = chain.args.toTypedArray()
                    val items = args[0] as? List<*> ?: return@install proceedOriginal(chain)
                    val user = Process.myUid() / 100000
                    val policy = policy(user)
                    // Root > Tiles owns component-level candidate sorting.
                    // Ignore retired Lists QS_EDITOR package hide/order policies,
                    // while keeping their values readable in old backup files.
                    val componentRank = policy.tileOrder.withIndex()
                        .associate { it.value to it.index }
                    if (componentRank.isEmpty()) return@install proceedOriginal(chain)
                    val activeSpecs = (ReflectionAccess.readField(chain.thisObject,
                        "mCurrentSpecs") as? Collection<*>)?.filterIsInstance<String>()
                        ?: return@install proceedOriginal(chain)
                    // Existing tiles and built-in suggestions are never moved.
                    // Preserve the original positions of those entries.
                    val positions = items.indices.filter { index ->
                        val spec = ReflectionAccess.readField(items[index], "spec") as? String
                        spec !in activeSpecs && tileComponent(spec) != null
                    }
                    val arranged = items.toMutableList()
                    val ranked = positions.map { items[it] }.sortedWith(
                        compareBy { item ->
                            val spec = ReflectionAccess.readField(item, "spec") as? String
                            componentRank[tileComponent(spec)?.flattenToString()] ?: Int.MAX_VALUE
                        },
                    )
                    positions.forEachIndexed { i, index -> arranged[index] = ranked[i] }
                    if (arranged == items) proceedOriginal(chain)
                    else {
                        args[0] = ArrayList(arranged)
                        proceedOriginal(chain, args)
                    }
                }

            }
            // Launcher3 and Quickstep presentation filtering was retired.
            // Legacy LAUNCHER_DRAWER / LAUNCHER_PREDICTIONS / RECENTS rules stay
            // serialized for old backups, but no runtime hooks consume them.
            "com.android.settings" -> {
                initRules()
                // AOSP ManageApplications.ApplicationsAdapter receives both
                // synchronous rebuild results and async onRebuildComplete callbacks.
                // Restrict the filtering to this presentation adapter, never the
                // SettingsLib app inventory or other permission/role pages.
                install(param.classLoader,
                    "com.android.settings.applications.manageapplications.ManageApplications" +
                        '$' + "ApplicationsAdapter",
                    "onRebuildComplete", 1, "settings-apps") { chain ->
                    val args = chain.args.toTypedArray()
                    val rows = args.singleOrNull() as? List<*>
                        ?: return@install proceedOriginal(chain)
                    val filtered = rows.filter { entry ->
                        val info = ReflectionAccess.readField(entry, "info", "mInfo") as? ApplicationInfo
                        val user = info?.uid?.takeIf { it >= 0 }?.div(100000)
                            ?: return@filter true
                        info.packageName !in selected(SurfaceKind.SETTINGS_APPS, user)
                    }
                    val ordered = prioritizedByProfile(
                        SurfaceKind.SETTINGS_APPS, filtered,
                        { entry ->
                            (ReflectionAccess.readField(entry, "info", "mInfo") as? ApplicationInfo)?.packageName
                        },
                        { entry ->
                            (ReflectionAccess.readField(entry, "info", "mInfo") as? ApplicationInfo)
                                ?.uid?.takeIf { it >= 0 }?.div(100000)
                        },
                    )
                    if (ordered == rows) proceedOriginal(chain)
                    else {
                        diagnostic("FILTER settings-apps total=${rows.size} hidden=${rows.size - filtered.size}")
                        args[0] = ArrayList(ordered)
                        proceedOriginal(chain, args)
                    }
                }
            }

        }
    }

    private fun initRules() {
        runCatching { preferences.registerOnSharedPreferenceChangeListener(listener) }
        refreshConfig()
    }

    @Synchronized private fun refreshConfig() {
        try {
            val raw = preferences.getString(RuleRepository.KEY_CONFIG, null)
            if (raw.isNullOrEmpty() || raw.length > RuleRepository.MAX_BACKUP_CHARS) {
                profiles = emptyMap()
                return
            }
            val config = json.decodeFromString(ModuleConfig.serializer(), raw).validated()
            profiles = mapOf(0 to config.surfaceFilters) +
                config.userSpaces.mapValues { (_, v) -> v.surfaceFilters }
        } catch (failure: Exception) {
            Log.w("ListCleaner.AospSurface", "Ignoring unreadable surface policy", failure)
            profiles = emptyMap()
        }
    }

    private fun policy(userId: Int = Process.myUid() / 100000): SurfaceFilterConfig =
        profiles[userId] ?: SurfaceFilterConfig()
    private fun selected(kind: SurfaceKind, userId: Int): Set<String> =
        policy(userId).selected(kind)

    /** Stable per-profile ordering without moving items into another Android user slot. */
    private fun prioritizedByProfile(
        kind: SurfaceKind, items: List<*>,
        pkgOf: (Any?) -> String?, userOf: (Any?) -> Int?,
    ): List<Any?> {
        if (!kind.supportsSurfaceOrdering()) return items
        val result = items.toMutableList()
        items.indices.groupBy { userOf(items[it]) }.forEach { (user, positions) ->
            if (user == null) return@forEach
            val ranks = policy(user).priorities(kind).withIndex()
                .associate { it.value to it.index }
            if (ranks.isEmpty()) return@forEach
            val ordered = positions.map { items[it] }
                .sortedWith(compareBy({ ranks[pkgOf(it)] ?: Int.MAX_VALUE }))
            positions.forEachIndexed { index, position -> result[position] = ordered[index] }
        }
        return result
    }

    private fun shouldHideNotification(entry: Any?): Boolean {
        // Never hide an AOSP GroupEntry unless every child is filtered. This
        // avoids losing unrelated children when a group summary belongs to a
        // selected app and preserves group render invariants.
        val children = ReflectionAccess.invokeNoArg(entry, "getChildren") as? Collection<*>
        if (children != null) return children.isNotEmpty() &&
            children.all { child -> notificationSelected(child) }
        return notificationSelected(entry)
    }

    private fun notificationSelected(entry: Any?): Boolean {
        val representative = ReflectionAccess.invokeNoArg(entry, "getRepresentativeEntry")
            ?: entry
        val sbn = ReflectionAccess.invokeNoArg(representative, "getSbn")
            as? android.service.notification.StatusBarNotification ?: return false
        if (notificationPackage(entry) == null) return false
        val policy = policy(sbn.userId)
        val category = sbn.notification.category
        val categoryKey = category?.takeIf { it in setOf(
            Notification.CATEGORY_PROMO, Notification.CATEGORY_MESSAGE,
            Notification.CATEGORY_EMAIL, Notification.CATEGORY_SOCIAL,
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_EVENT, Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM,
        ) } ?: "(other)"
        return sbn.packageName in policy.selected(SurfaceKind.NOTIFICATION_SHADE) ||
            policy.isChannelHidden(sbn.packageName, sbn.notification.channelId.orEmpty()) ||
            categoryKey in policy.hiddenNotificationCategories
    }

    private fun notificationPackage(entry: Any?): String? {
        val representative = ReflectionAccess.invokeNoArg(entry, "getRepresentativeEntry")
            ?: entry
        val sbn = ReflectionAccess.invokeNoArg(representative, "getSbn")
            as? android.service.notification.StatusBarNotification ?: return null
        val notification = sbn.notification
        if (notification.category in setOf(Notification.CATEGORY_CALL,
                Notification.CATEGORY_ALARM, Notification.CATEGORY_SERVICE) ||
            notification.flags and (Notification.FLAG_NO_CLEAR or
                Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONGOING_EVENT) != 0) return null
        return sbn.packageName
    }

    /** CustomTile uses an independent component and Android user, unlike package-only QS filtering. */
    private fun customTileLabel(tile: Any?): String? {
        val receiver = tile ?: return null
        val component = (ReflectionAccess.invokeNoArg(receiver, "getComponent") as? ComponentName)
            ?: (ReflectionAccess.readField(receiver, "mComponent") as? ComponentName)
            ?: run {
                val spec = (ReflectionAccess.invokeNoArg(receiver, "getTileSpec") as? String)
                    ?: (ReflectionAccess.readField(receiver, "mSpec") as? String)
                val flattened = spec?.takeIf { it.startsWith("custom(") && it.endsWith(")") }
                    ?.removePrefix("custom(")?.removeSuffix(")")
                ComponentName.unflattenFromString(flattened ?: return null)
            } ?: return null
        val rawUser = ReflectionAccess.invokeNoArg(receiver, "getUser")
            ?: ReflectionAccess.readField(receiver, "mUser", "mUserId")
        val user = when (rawUser) {
            is Number -> rawUser.toInt()
            is UserHandle -> ReflectionAccess.userId(rawUser)
            else -> null
        } ?: Process.myUid() / 100000
        return policy(user).tileLabels[component.flattenToString()]
    }

    private fun tileComponent(spec: String?): ComponentName? {
        val component = spec?.takeIf { it.startsWith("custom(") && it.endsWith(")") }
            ?.removePrefix("custom(")?.removeSuffix(")") ?: return null
        return ComponentName.unflattenFromString(component)
    }

    private data class CompletedOriginal(val value: Any?)
    private val lastOriginal = ThreadLocal<CompletedOriginal?>()

    /** Always keep the last successful AOSP call for fail-open recovery, including null. */
    private fun proceedOriginal(
        chain: XposedInterface.Chain,
        args: Array<Any?>? = null,
    ): Any? {
        val result = if (args == null) chain.proceed() else chain.proceed(args)
        lastOriginal.set(CompletedOriginal(result))
        return result
    }

    private fun diagnostic(event: String) {
        Log.i("ListCleaner.AospSurface", "process=$process $event")
    }

    private fun install(loader: ClassLoader, className: String, name: String,
                        params: Int, id: String,
                        callback: (XposedInterface.Chain) -> Any?) {
        val target = runCatching { Class.forName(className, false, loader) }.getOrNull()
            ?: run {
                diagnostic("HOOK_MISSING id=$id class=$className")
                return
            }
        var count = 0
        ReflectionAccess.hierarchyMethods(target).filter { m ->
            m.name == name && !Modifier.isAbstract(m.modifiers) &&
                (params < 0 || m.parameterCount == params)
        }.forEach { method ->
            val key = method.toGenericString()
            if (!installed.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId("lc-aosp-$id").intercept(
                    XposedInterface.Hooker { chain ->
                        lastOriginal.remove()
                        try { callback(chain) }
                        catch (t: Throwable) {
                            Log.w("ListCleaner.AospSurface", "Fail-open surface $id", t)
                            lastOriginal.get()?.value ?: if (lastOriginal.get() != null) null
                                else chain.proceed()
                        } finally {
                            lastOriginal.remove()
                        }
                    }
                )
                count++
                diagnostic("HOOK_INSTALLED id=$id method=$key")
            }.onFailure {
                installed.remove(key)
                diagnostic("HOOK_FAILED id=$id method=$key error=${it.javaClass.simpleName}")
            }
        }
        if (count == 0) diagnostic("HOOK_NOT_READY id=$id method=$name class=$className")
    }
}
