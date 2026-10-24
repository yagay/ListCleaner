package com.yagay.ListCleaner.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log

/** A published ShortcutInfo is not a disableable Android component. */
internal data class PublishedShortcut(
    val userId: Int,
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val enabled: Boolean,
    val manifest: Boolean,
    val dynamic: Boolean,
    val pinned: Boolean,
    val cached: Boolean
)
internal enum class ShortcutDiscoveryStatus {
    AVAILABLE, HOST_PERMISSION_REQUIRED, SERVICE_UNAVAILABLE, QUERY_FAILED
}
internal data class ShortcutDiscoveryResult(
    val status: ShortcutDiscoveryStatus,
    val shortcuts: List<PublishedShortcut> = emptyList(),
    val detail: String = "",
    val observedAt: Long = System.currentTimeMillis()
)
internal fun interface ShortcutQueryTransport {
    fun query(): ShortcutDiscoveryResult
}

/**
 * Public API transport. Does not claim launcher identity or change the HOME role.
 * Privileged transports must enforce caller authorization before returning data.
 */
internal class LauncherShortcutQueryTransport(private val context: Context) : ShortcutQueryTransport {
    override fun query(): ShortcutDiscoveryResult {
        val manager = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutDiscoveryResult(ShortcutDiscoveryStatus.SERVICE_UNAVAILABLE)
        if (!runCatching { manager.hasShortcutHostPermission() }.getOrDefault(false)) {
            return ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.HOST_PERMISSION_REQUIRED,
                detail = "Shortcut host permission is not granted"
            )
        }
        return try {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
            )
            val userId = Process.myUid() / 100000
            val records = manager.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .map { shortcut ->
                    PublishedShortcut(
                        userId = userId,
                        packageName = shortcut.`package`,
                        shortcutId = shortcut.id,
                        label = shortcut.shortLabel?.toString().orEmpty(),
                        enabled = shortcut.isEnabled,
                        manifest = shortcut.isDeclaredInManifest,
                        dynamic = shortcut.isDynamic,
                        pinned = shortcut.isPinned,
                        cached = shortcut.isCached
                    )
                }.distinctBy { Triple(it.userId, it.packageName, it.shortcutId) }
            ShortcutDiscoveryResult(ShortcutDiscoveryStatus.AVAILABLE, records)
        } catch (error: SecurityException) {
            ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.HOST_PERMISSION_REQUIRED,
                detail = error.javaClass.simpleName
            )
        } catch (error: Exception) {
            Log.w("ShortcutDiscovery", "Read-only shortcut query failed", error)
            ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.QUERY_FAILED,
                detail = error.javaClass.simpleName
            )
        }
    }
}

/** Independent discovery boundary; never maps ShortcutInfo IDs to components. */
internal class ShortcutDiscoveryBridge(private val transport: ShortcutQueryTransport) {
    fun scan(): ShortcutDiscoveryResult = transport.query()
}
