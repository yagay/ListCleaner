package com.yagay.ListCleaner.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.media.MediaRouter2
import android.os.UserHandle
import android.os.UserManager
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.runtime.ActiveNotificationRow

/**
 * AOSP platform-owned discovery sources per presentation surface.
 * These are eligible targets, not claims that the currently displayed UI has
 * shown them; ephemeral prediction, recents and media candidates can change.
 */
internal object SurfaceCandidateDiscovery {
    data class Result(val items: List<Pair<String, String>>, val description: String)

    fun query(
        context: Context,
        kind: SurfaceKind,
        userId: Int,
        currentNotifications: List<ActiveNotificationRow>,
    ): Result {
        val pm = context.packageManager
        val user = runCatching {
            context.getSystemService(UserManager::class.java)?.userProfiles
                ?.firstOrNull { handle ->
                    runCatching {
                        (handle.javaClass.getMethod("getIdentifier").invoke(handle) as? Int) == userId
                    }.getOrDefault(false)
                }
        }.getOrNull()
        val ownUser = android.os.Process.myUid() / 100000
        fun appLabel(pkg: String): String = runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)
        val pairs: List<Pair<String, String>>
        val source: String
        when (kind) {
            SurfaceKind.NOTIFICATION_SHADE -> {
                pairs = currentNotifications.filter { it.userId == userId }
                    .map { it.packageName }.distinct().map { it to appLabel(it) }
                source = "NotificationListenerService: currently posted notifications only"
            }
            SurfaceKind.LAUNCHER_DRAWER, SurfaceKind.LAUNCHER_PREDICTIONS,
            SurfaceKind.RECENTS -> {
                pairs = if (user != null) runCatching {
                    val launcher = context.getSystemService(LauncherApps::class.java)
                    launcher?.getActivityList(null, user).orEmpty()
                        .map { it.componentName.packageName }
                        .distinct().map { it to appLabel(it) }
                }.getOrDefault(emptyList()) else emptyList()
                source = if (user == null) "LauncherApps: selected profile is not accessible"
                    else "LauncherApps: eligible launchable apps (predictions and recent tasks are transient)"
            }
            SurfaceKind.QS_EDITOR -> {
                pairs = if (userId == ownUser) runCatching {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(
                        Intent("android.service.quicksettings.action.QS_TILE"),
                        PackageManager.GET_META_DATA
                    ).mapNotNull { it.serviceInfo }
                        .filter { it.exported &&
                            it.permission == "android.permission.BIND_QUICK_SETTINGS_TILE" }
                        .map { it.packageName }.distinct().map { it to appLabel(it) }
                }.getOrDefault(emptyList()) else emptyList()
                source = "PackageManager: registered TileService providers in current user"
            }
            SurfaceKind.DOCUMENT_RECENTS -> {
                pairs = if (userId == ownUser) runCatching {
                    @Suppress("DEPRECATION")
                    pm.queryIntentContentProviders(
                        Intent("android.content.action.DOCUMENTS_PROVIDER"),
                        PackageManager.GET_META_DATA
                    ).mapNotNull { it.providerInfo }
                        .filter { it.exported &&
                            it.readPermission == "android.permission.MANAGE_DOCUMENTS" }
                        .flatMap { info ->
                            info.authority.orEmpty().split(';').filter { it.isNotBlank() }
                                .map { it to appLabel(info.packageName) }
                        }.distinctBy { it.first }
                }.getOrDefault(emptyList()) else emptyList()
                source = "PackageManager: registered DocumentsProvider authorities; recent rows are transient"
            }
            SurfaceKind.MEDIA_OUTPUT -> {
                pairs = if (userId == ownUser) runCatching {
                    MediaRouter2.getInstance(context).routes
                        .map { it.id to it.name.toString() }.distinctBy { it.first }
                }.getOrDefault(emptyList()) else emptyList()
                source = "MediaRouter2: routes visible to ListCleaner, not all SystemUI routes"
            }
            SurfaceKind.SETTINGS_APPS -> {
                pairs = if (userId == ownUser) runCatching {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .map { it.packageName to it.loadLabel(pm).toString() }
                        .distinctBy { it.first }
                }.getOrDefault(emptyList()) else emptyList()
                source = "PackageManager: installed applications; Settings filters may narrow the visible subset"
            }
        }
        return Result(pairs.distinctBy { it.first }.sortedBy { it.second.lowercase() }, source)
    }
}
