package com.yagay.ListCleaner.xposed

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Read-only inventory from the real HOME process, which already holds Android's
 * Shortcut Host permission. Never launches shortcut intents, grants permissions,
 * changes default HOME, or installs a second filtering/cleanup hook.
 *
 * Application/package scope is deliberately bounded: unknown launchers still
 * use the existing read-only ShortcutService/Root fallback.
 */
class LauncherShortcutObserverModule : XposedModule() {
    @Volatile private var processName = ""
    @Volatile private var started = false
    private val main = Handler(Looper.getMainLooper())
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "ListCleaner-LauncherShortcuts").apply { isDaemon = true }
    }
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val persistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in LAUNCHERS ||
            processName.substringBefore(':') != param.packageName
        ) return
        // PackageReady timing varies with the LSPosed implementation and OEM
        // Application.attach. Retry obtaining the existing HOME application,
        // rather than hooking every Application in the launcher process.
        fun bootstrap(remaining: Int) {
            if (started) return
            val context = currentApplication()
            if (context == null) {
                if (remaining > 0) main.postDelayed({ bootstrap(remaining - 1) }, 500L)
                else record("SCAN_UNAVAILABLE reason=application_not_ready")
                return
            }
            val launcher = context.getSystemService(LauncherApps::class.java)
            if (launcher == null) {
                record("SCAN_UNAVAILABLE reason=no_launcher_service")
                return
            }
            // A HOME role may be granted only after user unlock or launcher
            // selection. Start the observer now, but the scan itself will not
            // query without host permission. Periodic retries handle that case.
            val host = runCatching { launcher.hasShortcutHostPermission() }.getOrDefault(false)
            if (!host) record("OBSERVER_DEFERRED reason=shortcut_host_permission_missing")
            started = true
            start(context.applicationContext, launcher)
        }
        main.post { bootstrap(12) }
    }

    private fun start(context: Context, launcher: LauncherApps) {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = refreshSoon()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshSoon()
            override fun onPackageChanged(packageName: String, user: UserHandle) = refreshSoon()
            override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                refreshSoon()
            override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                refreshSoon()
            override fun onShortcutsChanged(
                packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle,
            ) = refreshSoon()
        }
        runCatching { launcher.registerCallback(callback, main) }.onFailure {
            record("CALLBACK_UNAVAILABLE error=${it.javaClass.simpleName}")
        }
        // Query once on startup, then refresh on broadcasts and periodically in
        // case a ShortcutService event was missed while the launcher was idle.
        refreshSoon()
        worker.scheduleWithFixedDelay({ scan(context, launcher) },
            REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES)
        record("OBSERVER_READY launcher=${context.packageName}")
    }

    private fun refreshSoon() {
        if (!worker.isShutdown) worker.execute {
            // Coalesce frequent callbacks without blocking launcher/UI threads.
            if (System.currentTimeMillis() - lastScan >= MIN_SCAN_INTERVAL_MS) {
                val context = currentApplication()
                val launcher = context?.getSystemService(LauncherApps::class.java)
                if (context != null && launcher != null) scan(context, launcher)
            }
        }
    }

    @Volatile private var lastScan: Long = 0L

    private fun scan(context: Context, launcher: LauncherApps) {
        if (!runCatching { launcher.hasShortcutHostPermission() }.getOrDefault(false)) {
            record("SCAN_SKIPPED reason=host_permission_missing")
            return
        }
        val profiles = runCatching { launcher.profiles }.getOrElse {
            record("SCAN_FAILED reason=profile_query error=${it.javaClass.simpleName}")
            return
        }
        if (profiles.isEmpty()) return
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        )
        var count = 0
        profiles.distinctBy { it }.forEach { user ->
            val userId = ReflectionAccess.userId(user) ?: return@forEach
            val shortcuts = try {
                launcher.getShortcuts(query, user)
            } catch (error: Exception) {
                record("SCAN_FAILED user=$userId error=${error.javaClass.simpleName}")
                return@forEach
            } ?: run {
                record("SCAN_FAILED user=$userId reason=null_response")
                return@forEach
            }
            // An unexpected over-limit result is incomplete; never overwrite
            // last verified snapshot with a clipped selection.
            if (shortcuts.size > MAX_SHORTCUTS) {
                record("SCAN_SKIPPED user=$userId reason=oversized count=${shortcuts.size}")
                return@forEach
            }
            val now = System.currentTimeMillis()
            val records = shortcuts.asSequence()
                .filter { it.isEnabled && (it.isDynamic || it.isDeclaredInManifest ||
                    it.isPinned || it.isCached) }
                .mapNotNull { shortcut ->
                    val pkg = shortcut.`package`?.takeIf(PackageIdentity::valid)
                        ?: return@mapNotNull null
                    val id = shortcut.id?.takeIf { it.isNotBlank() && it.length <= 300 }
                        ?: return@mapNotNull null
                    val activity = shortcut.activity?.className
                    ObservedEntryRecord(
                        kind = IntentKind.SHORTCUT_ITEM.name,
                        packageName = pkg,
                        syntheticClass = SyntheticEntryKeys.shortcutItemClass(activity, id),
                        label = shortcut.shortLabel?.toString().orEmpty().take(160),
                        activityClass = activity,
                        observedAt = now,
                        userId = userId,
                    ).validatedOrNull()
                }.distinctBy { it.key }.toList()
            if (persistence.replaceKind(IntentKind.SHORTCUT_ITEM, records, userId)) {
                count += records.size
                record("SCAN_OK user=$userId shortcuts=${records.size}")
            }
        }
        lastScan = System.currentTimeMillis()
        record("SCAN_FINISHED profiles=${profiles.size} shortcuts=$count")
    }

    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.LauncherObserver"
        const val REFRESH_MINUTES = 4L
        const val MIN_SCAN_INTERVAL_MS = 4_000L
        const val MAX_SHORTCUTS = 1024
        val LAUNCHERS = setOf(
            "com.android.launcher", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home", "com.sec.android.app.launcher",
        )
    }
}
