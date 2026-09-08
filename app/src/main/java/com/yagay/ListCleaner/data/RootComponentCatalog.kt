package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import kotlinx.coroutines.delay

enum class CleanupKind(val action: String) {
    TILE("android.service.quicksettings.action.QS_TILE"),
    SHORTCUT("android.intent.action.CREATE_SHORTCUT"),
    WIDGET("android.appwidget.action.APPWIDGET_UPDATE")
}

data class RootComponent(
    val kind: CleanupKind, val component: ComponentName, val user: Int,
    val label: String, val owner: String, val icon: Bitmap?,
    val overrideState: Int?, val enabled: Boolean?, val applicationEnabled: Boolean?,
    val blocked: String? = null
) {
    val id: String get() = "$user|${kind.name}|${component.flattenToString()}"
}

data class RootComponentScan(
    val items: List<RootComponent> = emptyList(),
    val warning: String = "",
    val observedAt: Long = 0
)

/** Only reads candidate metadata/settings. Root is requested only for explicit state changes. */
class RootComponentCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val user = android.os.Process.myUid() / 100_000
    private val icons = android.util.LruCache<String, Bitmap>(128)
    private val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or PackageManager.MATCH_ALL or
        PackageManager.GET_META_DATA
    @Volatile var lastOperation = "No component operation"
        private set

    fun requireRoot() {
        lastOperation = "at=${System.currentTimeMillis()} status=checking_root"
        try {
            ComponentRootCommand.requireRoot()
            lastOperation = "at=${System.currentTimeMillis()} status=root_granted"
        } catch (failure: ComponentRootCommand.RootAccessException) {
            lastOperation = "at=${System.currentTimeMillis()} status=root_unavailable reason=${failure.reason}"
            throw failure
        }
    }

    private fun kindTitle(kind: CleanupKind): String = context.getString(when (kind) {
        CleanupKind.TILE -> R.string.cleanup_tile
        CleanupKind.SHORTCUT -> R.string.cleanup_shortcut
        CleanupKind.WIDGET -> R.string.cleanup_widget
    })

    @Suppress("DEPRECATION")
    private fun query(kind: CleanupKind): List<ComponentInfo> = when (kind) {
        CleanupKind.TILE -> pm.queryIntentServices(Intent(kind.action), flags).mapNotNull { it.serviceInfo }
            .filter { it.permission == "android.permission.BIND_QUICK_SETTINGS_TILE" && it.exported }
        CleanupKind.SHORTCUT -> pm.queryIntentActivities(Intent(kind.action), flags).mapNotNull { it.activityInfo }
            .filter { it.exported }
        CleanupKind.WIDGET -> pm.queryBroadcastReceivers(Intent(kind.action), flags).mapNotNull { it.activityInfo }
            .filter { (it.metaData?.getInt("android.appwidget.provider", 0) ?: 0) != 0 }
    }

    @Suppress("DEPRECATION")
    fun scan(): RootComponentScan {
        icons.evictAll()
        val errors = mutableListOf<String>()
        val items = CleanupKind.entries.flatMap { kind ->
            try {
                query(kind).distinctBy { ComponentName(it.packageName, it.name) }.map { info -> read(kind, info) }
            } catch (failure: Exception) {
                Log.e(TAG, "${kind.name} component scan failed", failure)
                errors += context.getString(R.string.root_kind_scan_failed, kindTitle(kind))
                emptyList()
            }
        }.sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }, { it.id }))
        return RootComponentScan(items, errors.joinToString("\n"), System.currentTimeMillis())
    }

    private fun read(kind: CleanupKind, info: ComponentInfo): RootComponent {
        val component = ComponentName(info.packageName, info.name)
        val raw = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
        val enabled = raw?.let { ComponentStatePolicy.enabled(it, info.enabled) }
        val appEnabled = runCatching {
            ComponentStatePolicy.enabled(pm.getApplicationEnabledSetting(info.packageName), info.applicationInfo.enabled)
        }.getOrNull()
        val blocked = when {
            !ComponentStatePolicy.valid(component.packageName, component.className, user) -> context.getString(R.string.root_component_invalid)
            info.packageName in setOf(context.packageName, "android", "com.android.systemui") ||
                info.applicationInfo.uid % 100_000 < 10_000 -> context.getString(R.string.root_component_protected)
            raw == null || enabled == null || appEnabled == null -> context.getString(R.string.root_state_read_failed)
            !appEnabled -> context.getString(R.string.root_owner_disabled)
            else -> null
        }
        return RootComponent(
            kind, component, user,
            runCatching { info.loadLabel(pm).toString() }.getOrDefault(component.shortClassName),
            runCatching { info.applicationInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName),
            icons.get(info.packageName) ?: runCatching { info.applicationInfo.loadIcon(pm).toBitmap(96, 96) }
                .getOrNull()?.also { icons.put(info.packageName, it) },
            raw, enabled, appEnabled, blocked
        )
    }

    /** Re-discover before mutation: no arbitrary component strings from UI/imports/root output. */
    suspend fun change(target: RootComponent, enable: Boolean): String {
        require(target.user == user) { context.getString(R.string.root_user_changed) }
        val info = query(target.kind).firstOrNull {
            ComponentName(it.packageName, it.name) == target.component
        } ?: error(context.getString(R.string.root_component_missing))
        val fresh = read(target.kind, info)
        check(fresh.blocked == null) { fresh.blocked ?: context.getString(R.string.root_operation_not_allowed) }
        check(fresh.overrideState == target.overrideState && fresh.enabled == target.enabled) {
            context.getString(R.string.root_component_changed)
        }
        if (fresh.enabled == enable) return context.getString(R.string.root_already_target_state)
        val script = ComponentStatePolicy.command(target.component.packageName, target.component.className, user, enable)
        lastOperation = "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable status=started"
        val result = try {
            ComponentRootCommand.run(script)
        } catch (failure: Exception) {
            lastOperation += " error=${failure.javaClass.name}"
            Log.e(TAG, "Root component command failed for ${target.id}", failure)
            throw IllegalStateException(context.getString(R.string.root_command_incomplete), failure)
        }
        val expected = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        var observed: Int? = null
        repeat(5) {
            if (observed != expected) {
                observed = runCatching { pm.getComponentEnabledSetting(target.component) }.getOrNull()
                if (observed != expected) delay(100)
            }
        }
        lastOperation = "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable " +
            "exit=${result.exitCode} timeout=${result.timedOut} observed=$observed\n${result.output}"
        Log.i(TAG, "COMPONENT_STATE ${target.id} exit=${result.exitCode} observed=$observed")
        check(!result.timedOut && result.exitCode == 0 && observed == expected) {
            context.getString(
                R.string.root_operation_unconfirmed,
                result.exitCode,
                observed?.toString() ?: context.getString(R.string.common_unknown)
            )
        }
        return context.getString(if (enable) R.string.root_verified_enabled else R.string.root_verified_disabled)
    }

    private companion object {
        const val TAG = "ListCleaner.RootCatalog"
    }
}
