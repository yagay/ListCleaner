package com.yagay.ListCleaner.xposed

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
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
                // RenderStageManager is the AOSP hand-off to the view renderer:
                // filter its presentation argument, not the notification collection.
                install(param.classLoader,
                    "com.android.systemui.statusbar.notification.collection.render.RenderStageManager",
                    "onRenderList", 1, "shade") { chain ->
                    val args = chain.args.toTypedArray()
                    val entries = args[0] as? List<*> ?: return@install chain.proceed()
                    val selected = policy().selected(SurfaceKind.NOTIFICATION_SHADE)
                    if (selected.isEmpty()) return@install chain.proceed()
                    val filtered = entries.filterNot { item ->
                        shouldHideNotification(item, selected)
                    }
                    if (filtered.size == entries.size) chain.proceed()
                    else {
                        args[0] = ArrayList(filtered)
                        chain.proceed(args)
                    }
                }
                install(param.classLoader, "com.android.systemui.qs.customize.TileAdapter",
                    "onTilesChanged", 1, "qs") { chain ->
                    val args = chain.args.toTypedArray()
                    val items = args[0] as? List<*> ?: return@install chain.proceed()
                    val selected = policy().selected(SurfaceKind.QS_EDITOR)
                    if (selected.isEmpty()) return@install chain.proceed()
                    val activeSpecs = (ReflectionAccess.readField(chain.thisObject,
                        "mCurrentSpecs") as? Collection<*>)?.filterIsInstance<String>().orEmpty()
                    val filtered = items.filter { item ->
                        val spec = ReflectionAccess.readField(item, "spec") as? String
                        val pkg = tilePackage(spec)
                        pkg == null || pkg !in selected || spec in activeSpecs
                    }
                    if (filtered.size == items.size) chain.proceed()
                    else {
                        args[0] = ArrayList(filtered)
                        chain.proceed(args)
                    }
                }
                install(param.classLoader, "com.android.systemui.media.dialog.MediaOutputController",
                    "getMediaDevices", 0, "media") { chain ->
                    val original = chain.proceed()
                    val items = original as? Collection<*> ?: return@install original
                    val selected = policy().selected(SurfaceKind.MEDIA_OUTPUT)
                    if (selected.isEmpty()) return@install original
                    val current = ReflectionAccess.invokeNoArg(chain.thisObject,
                        "getCurrentConnectedMediaDevice")
                    val filtered = items.filter { device ->
                        if (device == null || device === current) return@filter true
                        val routeId = ReflectionAccess.readString(device, "getId")
                        routeId == null || routeId !in selected
                    }
                    if (filtered.size == items.size) original else ArrayList(filtered)
                }
            }
            "com.android.launcher3" -> {
                initRules()
                install(param.classLoader, "com.android.launcher3.allapps.AllAppsStore",
                    "getApps", 0, "drawer") { chain ->
                    val original = chain.proceed()
                    val array = original as? Array<*> ?: return@install original
                    val filtered = array.filter { value ->
                        val packageName = appInfoPackage(value)
                        packageName == null ||
                            packageName !in selected(SurfaceKind.LAUNCHER_DRAWER, itemUser(value))
                    }
                    if (filtered.size == array.size) original
                    else java.lang.reflect.Array.newInstance(
                        original.javaClass.componentType, filtered.size).also { output ->
                        filtered.forEachIndexed { index, entry ->
                            java.lang.reflect.Array.set(output, index, entry)
                        }
                    }
                }
                install(param.classLoader, "com.android.launcher3.allapps.AlphabeticalAppsList",
                    "getPredictedApps", 0, "predictions") { chain ->
                    filterListResult(chain, SurfaceKind.LAUNCHER_PREDICTIONS, ::appInfoPackage)
                }
                install(param.classLoader, "com.android.quickstep.views.RecentsView",
                    "applyLoadPlan", 1, "recents") { chain ->
                    val args = chain.args.toTypedArray()
                    val groups = args[0] as? List<*> ?: return@install chain.proceed()
                    val selected = policy().selected(SurfaceKind.RECENTS)
                    if (selected.isEmpty()) return@install chain.proceed()
                    val filtered = groups.filter { group ->
                        val tasks = listOf(
                            ReflectionAccess.readField(group, "task1", "mTask1"),
                            ReflectionAccess.readField(group, "task2", "mTask2"),
                        ).filterNotNull()
                        // A split group must remain intact if any task is unselected.
                        tasks.isEmpty() || tasks.any { task ->
                            val pkg = taskPackage(task)
                            pkg == null || pkg !in selected
                        }
                    }
                    if (filtered.size == groups.size) chain.proceed()
                    else {
                        args[0] = ArrayList(filtered)
                        chain.proceed(args)
                    }
                }
            }
            "com.android.settings" -> {
                initRules()
                install(param.classLoader, "com.android.settingslib.applications.ApplicationsState\u0024Session",
                    "rebuild", -1, "settings-apps") { chain ->
                    filterListResult(chain, SurfaceKind.SETTINGS_APPS) { entry ->
                        val info = ReflectionAccess.readField(entry, "info", "mInfo") as? ApplicationInfo
                        info?.packageName
                    }
                }
            }
            "com.android.documentsui" -> {
                initRules()
                install(param.classLoader, "com.android.documentsui.RecentsLoader",
                    "loadInBackground", 0, "recents-files") { chain ->
                    val result = chain.proceed() ?: return@install null
                    val selected = policy().selected(SurfaceKind.DOCUMENT_RECENTS)
                    if (selected.isEmpty()) return@install result
                    val original = ReflectionAccess.readField(result, "cursor") as? Cursor
                        ?: return@install result
                    // Clone only a bounded, cursor-backed recents result. Preserve its
                    // column types and extras; no changes are made to the document provider.
                    if (original.count > 1024) return@install result
                    val authorityIndex = original.getColumnIndex("authority")
                    if (authorityIndex < 0) return@install result
                    val matrix = MatrixCursor(original.columnNames, original.count)
                    try {
                        if (original.moveToFirst()) do {
                            val authority = original.getString(authorityIndex) ?: ""
                            if (authority.substringBefore('/') in selected) continue
                            matrix.addRow(Array<Any?>(original.columnCount) { c ->
                                when (original.getType(c)) {
                                    Cursor.FIELD_TYPE_NULL -> null
                                    Cursor.FIELD_TYPE_INTEGER -> original.getLong(c)
                                    Cursor.FIELD_TYPE_FLOAT -> original.getDouble(c)
                                    Cursor.FIELD_TYPE_BLOB -> original.getBlob(c)
                                    else -> original.getString(c)
                                }
                            })
                        } while (original.moveToNext())
                        matrix.setExtras(original.extras)
                        ReflectionAccess.writeField(result, matrix, "cursor")
                        original.close()
                        result
                    } catch (failure: Exception) {
                        matrix.close()
                        result
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

    private fun filterListResult(
        chain: XposedInterface.Chain,
        kind: SurfaceKind,
        pkgOf: (Any?) -> String?,
    ): Any? {
        val original = chain.proceed()
        val list = original as? List<*> ?: return original
        val selected = policy().selected(kind)
        if (selected.isEmpty()) return original
        val filtered = list.filter { entry ->
            val pkg = pkgOf(entry)
            pkg == null || pkg !in selected
        }
        return if (filtered.size == list.size) original else ArrayList(filtered)
    }

    private fun shouldHideNotification(entry: Any?, selected: Set<String>): Boolean {
        // Never hide an AOSP GroupEntry unless every child is filtered. This
        // avoids losing unrelated children when a group summary belongs to a
        // selected app and preserves group render invariants.
        val children = ReflectionAccess.invokeNoArg(entry, "getChildren") as? Collection<*>
        if (children != null) return children.isNotEmpty() &&
            children.all { notificationPackage(it) in selected }
        return notificationPackage(entry) in selected
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

    private fun tilePackage(spec: String?): String? {
        val component = spec?.takeIf { it.startsWith("custom(") && it.endsWith(")") }
            ?.removePrefix("custom(")?.removeSuffix(")") ?: return null
        return ComponentName.unflattenFromString(component)?.packageName
    }

    private fun appInfoPackage(value: Any?): String? {
        val component = (ReflectionAccess.readField(value, "componentName") as? ComponentName)
            ?: (ReflectionAccess.readField(value, "intent") as? Intent)?.component
        return component?.packageName
    }

    private fun itemUser(value: Any?): Int =
        (ReflectionAccess.readField(value, "user") as? UserHandle)?.let {
            ReflectionAccess.invokeNoArg(it, "getIdentifier") as? Int
        } ?: (Process.myUid() / 100000)

    private fun taskPackage(value: Any?): String? {
        val key = ReflectionAccess.readField(value, "key", "mKey") ?: return null
        val intent = ReflectionAccess.readField(key, "baseIntent", "mBaseIntent") as? Intent
        return intent?.component?.packageName
    }

    private fun install(loader: ClassLoader, className: String, name: String,
                        params: Int, id: String,
                        callback: (XposedInterface.Chain) -> Any?) {
        val target = runCatching { Class.forName(className, false, loader) }.getOrNull()
            ?: return
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
                        try { callback(chain) }
                        catch (t: Throwable) {
                            Log.w("ListCleaner.AospSurface", "Fail-open surface $id", t)
                            chain.proceed()
                        }
                    }
                )
            }.onFailure { installed.remove(key) }
        }
    }
}
