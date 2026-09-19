package com.yagay.ListCleaner.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import com.yagay.ListCleaner.domain.listCleanerAppType
import kotlinx.coroutines.delay

enum class CleanupKind(val action: String) {
    TILE("android.service.quicksettings.action.QS_TILE"),
    SHORTCUT("android.intent.action.CREATE_SHORTCUT"),
    WIDGET("android.appwidget.action.APPWIDGET_UPDATE")
}

enum class ComponentDiscoverySource {
    PACKAGE_MANAGER,
    APP_WIDGET_MANAGER,
    LAUNCHER_APPS
}

private data class DiscoveredComponent(
    val info: ComponentInfo,
    val sources: Set<ComponentDiscoverySource>
)

data class RootComponent(
    val kind: CleanupKind, val component: ComponentName, val user: Int,
    val label: String, val owner: String, val icon: Bitmap?,
    val appType: AppType,
    val overrideState: Int?, val enabled: Boolean?, val applicationEnabled: Boolean?,
    val blocked: String? = null,
    val discoverySources: Set<ComponentDiscoverySource> = emptySet()
) {
    val id: String get() = "$user|${kind.name}|${component.flattenToString()}"
}

data class RootComponentScan(
    val items: List<RootComponent> = emptyList(),
    val warning: String = "",
    val observedAt: Long = 0
)

/**
 * Read-only discovery for components List Cleaner can safely map back to Android components.
 *
 * Public subsystem registries are preferred when Android exposes them:
 * - widgets: AppWidgetManager registry + manifest fallback
 * - shortcut creation entries: LauncherApps config activities + legacy ACTION_CREATE_SHORTCUT
 * - tiles: standard TileService manifest contract
 *
 * Root is requested only for an explicit component state change.
 */
class RootComponentCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val user = Process.myUid() / PER_USER_RANGE
    private val icons = android.util.LruCache<String, Bitmap>(128)
    private val ownerLabels = android.util.LruCache<String, String>(256)
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

    private fun componentKey(info: ComponentInfo): ComponentName =
        ComponentName(info.packageName, info.name)

    private fun mergeDiscovered(vararg groups: List<DiscoveredComponent>): List<DiscoveredComponent> {
        val merged = linkedMapOf<ComponentName, DiscoveredComponent>()
        groups.asSequence().flatten().forEach { candidate ->
            val key = componentKey(candidate.info)
            val previous = merged[key]
            merged[key] = if (previous == null) {
                candidate
            } else {
                previous.copy(sources = previous.sources + candidate.sources)
            }
        }
        return merged.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun queryTiles(): List<DiscoveredComponent> =
        pm.queryIntentServices(Intent(CleanupKind.TILE.action), flags)
            .mapNotNull { it.serviceInfo }
            .filter {
                it.permission == BIND_QUICK_SETTINGS_TILE && it.exported
            }
            .map {
                DiscoveredComponent(it, setOf(ComponentDiscoverySource.PACKAGE_MANAGER))
            }

    @Suppress("DEPRECATION")
    private fun queryLegacyShortcutActivities(): List<DiscoveredComponent> =
        pm.queryIntentActivities(Intent(CleanupKind.SHORTCUT.action), flags)
            .mapNotNull { it.activityInfo }
            .filter { it.exported }
            .map {
                DiscoveredComponent(it, setOf(ComponentDiscoverySource.PACKAGE_MANAGER))
            }

    @Suppress("DEPRECATION")
    private fun queryShortcutConfigActivities(): List<DiscoveredComponent> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val activities = runCatching {
            launcherApps.getShortcutConfigActivityList(null, Process.myUserHandle())
        }.onFailure {
            Log.w(TAG, "LauncherApps shortcut config discovery unavailable", it)
        }.getOrDefault(emptyList())

        return activities.mapNotNull { launcherInfo ->
            val info = runCatching {
                pm.getActivityInfo(launcherInfo.componentName, flags)
            }.getOrNull() ?: return@mapNotNull null
            if (!info.exported) return@mapNotNull null
            DiscoveredComponent(info, setOf(ComponentDiscoverySource.LAUNCHER_APPS))
        }
    }

    @Suppress("DEPRECATION")
    private fun queryManifestWidgets(): List<DiscoveredComponent> =
        pm.queryBroadcastReceivers(Intent(CleanupKind.WIDGET.action), flags)
            .mapNotNull { it.activityInfo }
            .filter {
                (it.metaData?.getInt(APP_WIDGET_PROVIDER_META_DATA, 0) ?: 0) != 0
            }
            .map {
                DiscoveredComponent(it, setOf(ComponentDiscoverySource.PACKAGE_MANAGER))
            }

    @Suppress("DEPRECATION")
    private fun queryRegisteredWidgets(): List<DiscoveredComponent> {
        val manager = AppWidgetManager.getInstance(context)
        val providers = runCatching {
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
        }.onFailure {
            Log.w(TAG, "AppWidgetManager provider discovery unavailable", it)
        }.getOrDefault(emptyList())

        return providers.mapNotNull { providerInfo ->
            val component = providerInfo.provider ?: return@mapNotNull null
            val info = runCatching {
                pm.getReceiverInfo(component, flags)
            }.getOrNull() ?: return@mapNotNull null
            DiscoveredComponent(info, setOf(ComponentDiscoverySource.APP_WIDGET_MANAGER))
        }
    }

    private fun query(kind: CleanupKind): List<DiscoveredComponent> = when (kind) {
        CleanupKind.TILE -> queryTiles()
        CleanupKind.SHORTCUT -> mergeDiscovered(
            queryShortcutConfigActivities(),
            queryLegacyShortcutActivities()
        )
        CleanupKind.WIDGET -> mergeDiscovered(
            queryRegisteredWidgets(),
            queryManifestWidgets()
        )
    }

    fun scan(): RootComponentScan {
        val errors = mutableListOf<String>()
        val appEnabledCache = mutableMapOf<String, Boolean?>()
        val items = CleanupKind.entries.flatMap { kind ->
            try {
                query(kind).map { discovered ->
                    read(kind, discovered, appEnabledCache)
                }
            } catch (failure: Exception) {
                Log.e(TAG, "${kind.name} component scan failed", failure)
                errors += context.getString(R.string.root_kind_scan_failed, kindTitle(kind))
                emptyList()
            }
        }.sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }, { it.id }))
        return RootComponentScan(items, errors.joinToString("\n"), System.currentTimeMillis())
    }

    /**
     * Refresh only components just mutated.
     *
     * Discovery is performed once per affected category instead of once per target. This matters
     * for bulk operations where AppWidgetManager/LauncherApps enumeration can otherwise be repeated
     * dozens of times.
     */
    fun refreshItems(previous: RootComponentScan, targets: Collection<RootComponent>): RootComponentScan {
        if (targets.isEmpty() || previous.items.isEmpty()) return previous

        val targetIds = targets.mapTo(hashSetOf()) { it.id }
        val discoveredByKind = targets.asSequence()
            .map { it.kind }
            .distinct()
            .associateWith { kind ->
                query(kind).associateBy { componentKey(it.info) }
            }
        val appEnabledCache = mutableMapOf<String, Boolean?>()

        val refreshed = previous.items.map { item ->
            if (item.id !in targetIds) {
                item
            } else {
                discoveredByKind[item.kind]
                    ?.get(item.component)
                    ?.let { read(item.kind, it, appEnabledCache) }
                    ?: item
            }
        }
        return previous.copy(items = refreshed, observedAt = System.currentTimeMillis())
    }

    private fun read(
        kind: CleanupKind,
        discovered: DiscoveredComponent,
        appEnabledCache: MutableMap<String, Boolean?>? = null
    ): RootComponent {
        val info = discovered.info
        val component = componentKey(info)
        val raw = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
        val enabled = raw?.let { ComponentStatePolicy.enabled(it, info.enabled) }
        val appEnabled = if (appEnabledCache != null && info.packageName in appEnabledCache) {
            appEnabledCache[info.packageName]
        } else {
            runCatching {
                ComponentStatePolicy.enabled(
                    pm.getApplicationEnabledSetting(info.packageName),
                    info.applicationInfo.enabled
                )
            }.getOrNull().also { appEnabledCache?.put(info.packageName, it) }
        }
        val blocked = when {
            !ComponentStatePolicy.valid(component.packageName, component.className, user) ->
                context.getString(R.string.root_component_invalid)
            info.packageName == context.packageName ||
                info.packageName == "android" ||
                info.packageName == "com.android.systemui" ||
                info.applicationInfo.uid % PER_USER_RANGE < Process.FIRST_APPLICATION_UID ->
                context.getString(R.string.root_component_protected)
            raw == null || enabled == null || appEnabled == null ->
                context.getString(R.string.root_state_read_failed)
            !appEnabled -> context.getString(R.string.root_owner_disabled)
            else -> null
        }
        return RootComponent(
            kind = kind,
            component = component,
            user = user,
            label = runCatching { info.loadLabel(pm).toString() }
                .getOrDefault(component.shortClassName),
            owner = ownerLabels.get(info.packageName) ?: runCatching {
                info.applicationInfo.loadLabel(pm).toString()
            }.getOrDefault(info.packageName).also {
                ownerLabels.put(info.packageName, it)
            },
            icon = icons.get(info.packageName) ?: runCatching {
                info.applicationInfo.loadIcon(pm).toBitmap(96, 96)
            }.getOrNull()?.also { icons.put(info.packageName, it) },
            appType = info.applicationInfo.listCleanerAppType(),
            overrideState = raw,
            enabled = enabled,
            applicationEnabled = appEnabled,
            blocked = blocked,
            discoverySources = discovered.sources
        )
    }

    /** Re-discover before mutation: no arbitrary component strings from UI/imports/root output. */
    suspend fun change(target: RootComponent, enable: Boolean): String {
        require(target.user == user) { context.getString(R.string.root_user_changed) }
        val discovered = query(target.kind).firstOrNull {
            componentKey(it.info) == target.component
        } ?: error(context.getString(R.string.root_component_missing))
        val fresh = read(target.kind, discovered)
        check(fresh.blocked == null) {
            fresh.blocked ?: context.getString(R.string.root_operation_not_allowed)
        }
        check(fresh.overrideState == target.overrideState && fresh.enabled == target.enabled) {
            context.getString(R.string.root_component_changed)
        }
        if (fresh.enabled == enable) return context.getString(R.string.root_already_target_state)

        val script = ComponentStatePolicy.command(
            target.component.packageName,
            target.component.className,
            user,
            enable
        )
        lastOperation =
            "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable status=started"
        val result = try {
            ComponentRootCommand.run(script)
        } catch (failure: Exception) {
            lastOperation += " error=${failure.javaClass.name}"
            Log.e(TAG, "Root component command failed for ${target.id}", failure)
            throw IllegalStateException(context.getString(R.string.root_command_incomplete), failure)
        }

        val expected = if (enable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        var observed: Int? = null
        repeat(5) {
            if (observed != expected) {
                observed = runCatching {
                    pm.getComponentEnabledSetting(target.component)
                }.getOrNull()
                if (observed != expected) delay(100)
            }
        }
        lastOperation =
            "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable " +
                "exit=${result.exitCode} timeout=${result.timedOut} observed=$observed\n${result.output}"
        Log.i(TAG, "COMPONENT_STATE ${target.id} exit=${result.exitCode} observed=$observed")
        check(!result.timedOut && result.exitCode == 0 && observed == expected) {
            context.getString(
                R.string.root_operation_unconfirmed,
                result.exitCode,
                observed?.toString() ?: context.getString(R.string.common_unknown)
            )
        }
        return context.getString(
            if (enable) R.string.root_verified_enabled else R.string.root_verified_disabled
        )
    }

    private companion object {
        const val TAG = "ListCleaner.RootCatalog"
        const val PER_USER_RANGE = 100_000
        const val BIND_QUICK_SETTINGS_TILE = "android.permission.BIND_QUICK_SETTINGS_TILE"
        const val APP_WIDGET_PROVIDER_META_DATA = "android.appwidget.provider"

    }
}
