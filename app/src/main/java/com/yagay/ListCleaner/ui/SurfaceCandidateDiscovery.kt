package com.yagay.ListCleaner.ui

import android.content.Context
import android.content.pm.PackageManager
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.runtime.ActiveNotificationRow

/**
 * AOSP platform-owned discovery sources per presentation surface.
 * These are eligible targets, not claims that the currently displayed UI has
 * shown them; media candidates can change. Legacy Launcher3 / Quickstep kinds
 * remain decodable but are never scanned or offered in the Lists UI.
 */
internal object SurfaceCandidateDiscovery {
    data class Result(val items: List<Pair<String, String>>, val description: String)

    fun query(
        context: Context,
        kind: SurfaceKind,
        userId: Int,
        currentNotifications: List<ActiveNotificationRow>,
        remoteInstalled: Set<String>? = null,
    ): Result {
        val pm = context.packageManager
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
                // The old values are reserved in serialized backups only.
                // Never perform LauncherApps discovery for removed Lists pages.
                pairs = emptyList()
                source = "Retired Launcher3 / Quickstep surface: legacy configuration only"
            }
            SurfaceKind.QS_EDITOR, SurfaceKind.DOCUMENT_RECENTS,
            SurfaceKind.MEDIA_OUTPUT -> {
                // Retired Lists pages: keep old config IDs decodable, but do
                // not scan unused candidates or alter their system surfaces.
                // Root > Tiles uses RootComponentCatalog instead.
                pairs = emptyList()
                source = "Retired Lists surface: legacy configuration only"
            }
            SurfaceKind.SETTINGS_APPS -> {
                pairs = if (userId != ownUser) remoteInstalled.orEmpty().map { it to appLabel(it) }
                else runCatching {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .map { it.packageName to it.loadLabel(pm).toString() }
                        .distinctBy { it.first }
                }.getOrDefault(emptyList())
                source = if (userId == ownUser)
                    "PackageManager: installed applications; Settings filters may narrow the visible subset"
                    else if (remoteInstalled == null) "Other-user package scan is not yet available"
                    else "UserSpaceManager: packages installed for the selected Android user"
            }
        }
        return Result(pairs.distinctBy { it.first }.sortedBy { it.second.lowercase() }, source)
    }
}
