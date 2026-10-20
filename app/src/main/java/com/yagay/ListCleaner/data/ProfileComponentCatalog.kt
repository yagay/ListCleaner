package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.listCleanerAppType
import kotlinx.coroutines.delay

/**
 * Cross-user component discovery does not use the manager app's own PackageManager context.
 * Only Android's root-accessible package shell is allowed to query a different user.
 * All operations are re-discovered and verified for the same User ID.
 */
class ProfileComponentCatalog(private val context: Context) {
    private val pm = context.packageManager
    private data class UserPackageState(
        val appEnabled: Boolean,
        val enabled: Set<String>,
        val disabled: Set<String>,
    )
    private val userLine = Regex("(?m)^\\s*User\\s+([0-9]+):")
    private val componentLine = Regex("^([A-Za-z_][A-Za-z0-9_.]*)/([A-Za-z_.$][A-Za-z0-9_.$]*)$")

    private fun shell(script: String): String? = runCatching {
        ComponentRootCommand.runReadOnly(script).takeIf {
            it.exitCode == 0 && !it.timedOut && !it.output.contains("[output truncated]") &&
                !it.output.contains("[output incomplete]")
        }?.output
    }.getOrNull()

    private fun installedPackages(userId: Int): Set<String>? =
        shell("pm list packages --user $userId")?.lineSequence()
            ?.map { it.trim().removePrefix("package:") }
            ?.filter(PackageIdentity::valid)?.toSet()

    private fun query(userId: Int, kind: CleanupKind): Set<ComponentName>? {
        val command = when (kind) {
            CleanupKind.LAUNCHER, CleanupKind.SHORTCUT -> "query-activities"
            CleanupKind.TILE -> "query-services"
            CleanupKind.WIDGET -> "query-receivers"
        }
        val intent = when (kind) {
            CleanupKind.LAUNCHER -> "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            CleanupKind.TILE -> "-a android.service.quicksettings.action.QS_TILE"
            CleanupKind.SHORTCUT -> "-a android.intent.action.CREATE_SHORTCUT"
            CleanupKind.WIDGET -> "-a android.appwidget.action.APPWIDGET_UPDATE"
        }
        val output = shell("cmd package $command --brief --components --query-flags 512 --user $userId $intent")
            ?: return null
        return output.lineSequence().mapNotNull { raw ->
            val match = componentLine.matchEntire(raw.trim()) ?: return@mapNotNull null
            val packageName = match.groupValues[1]
            val className = match.groupValues[2].let {
                if (it.startsWith(".")) packageName + it else it
            }
            if (!ComponentStatePolicy.valid(packageName, className, userId)) null
            else ComponentName(packageName, className)
        }.toSet()
    }

    private fun readPackageState(userId: Int, packageName: String): UserPackageState? {
        if (!PackageIdentity.valid(packageName)) return null
        // Whitelisted package names contain no shell metacharacters.
        // Return only this user's state block; full dumpsys output often exceeds the bounded
        // 16 KiB command reader and would otherwise silently omit late user profiles.
        val command = "dumpsys package $packageName | awk -v wanted='$userId' " +
            "'/^[[:space:]]*User [0-9]+:/ { " +
            "if (active) exit; active = ($2 == wanted \":\"); } active { print; }'"
        val output = shell(command) ?: return null
        val headers = userLine.findAll(output).toList()
        val headerIndex = headers.indexOfFirst { it.groupValues[1] == userId.toString() }
        if (headerIndex < 0) return null
        val begin = headers[headerIndex].range.last + 1
        val end = headers.getOrNull(headerIndex + 1)?.range?.first ?: output.length
        val section = output.substring(begin, end)
        if (!Regex("\\binstalled=true\\b").containsMatchIn(section)) return null
        val appState = Regex("\\benabled=([0-4])\\b").find(section)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val enabled = linkedSetOf<String>()
        val disabled = linkedSetOf<String>()
        var destination: MutableSet<String>? = null
        section.lineSequence().forEach { line ->
            val value = line.trim()
            destination = when (value) {
                "enabledComponents:" -> enabled
                "disabledComponents:" -> disabled
                else -> if (line.startsWith("      ") || line.startsWith("\t")) destination else null
            }
            if (value !in setOf("enabledComponents:", "disabledComponents:") &&
                value.matches(Regex("[A-Za-z0-9_.$/]+"))) {
                destination?.add(value)
            }
        }
        return UserPackageState(appState !in setOf(2, 3, 4), enabled, disabled)
    }

    private fun stateFor(component: ComponentName, state: UserPackageState): Int {
        val names = setOf(component.className, component.shortClassName,
            component.flattenToShortString(), component.flattenToString())
        return when {
            state.disabled.any { it in names } -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            state.enabled.any { it in names } -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
    }

    @Suppress("DEPRECATION")
    private fun manifestInfo(kind: CleanupKind, component: ComponentName, userId: Int): ComponentInfo {
        val packageName = component.packageName
        val source = runCatching {
            when (kind) {
                CleanupKind.LAUNCHER, CleanupKind.SHORTCUT ->
                    pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                CleanupKind.TILE -> pm.getServiceInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                CleanupKind.WIDGET -> pm.getReceiverInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull()
        if (source != null) return source
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = userId * 100000 + 10000
        }
        val info: ComponentInfo = if (kind == CleanupKind.TILE) ServiceInfo() else ActivityInfo()
        info.packageName = packageName
        info.name = component.className
        info.applicationInfo = appInfo
        info.enabled = true
        info.exported = true
        return info
    }

    fun scan(userId: Int): RootComponentScan {
        require(userId in 0..99999)
        val installed = installedPackages(userId)
            ?: return RootComponentScan(warning = context.getString(R.string.root_scan_failed))
        val packageStates = mutableMapOf<String, UserPackageState?>()
        val errors = mutableListOf<String>()
        val items = CleanupKind.entries.flatMap { kind ->
            val resolved = query(userId, kind)
            if (resolved == null) {
                errors += kind.name
                emptyList()
            } else resolved.asSequence().filter { it.packageName in installed }.map { component ->
                val state = packageStates.getOrPut(component.packageName) {
                    readPackageState(userId, component.packageName)
                }
                val info = manifestInfo(kind, component, userId)
                val override = state?.let { stateFor(component, it) }
                val enabled = override?.let { ComponentStatePolicy.enabled(it, info.enabled) }
                val blocked = when {
                    component.packageName in setOf(context.packageName, "android", "com.android.systemui") ->
                        context.getString(R.string.root_component_protected)
                    override == null || state?.appEnabled != true ->
                        context.getString(R.string.root_state_read_failed)
                    else -> null
                }
                RootComponent(
                    kind = kind, component = component, user = userId,
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(component.shortClassName),
                    owner = runCatching { info.applicationInfo.loadLabel(pm).toString() }
                        .getOrDefault(component.packageName),
                    icon = runCatching { info.applicationInfo.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    appType = info.applicationInfo.listCleanerAppType(),
                    overrideState = override, enabled = enabled,
                    applicationEnabled = state?.appEnabled, blocked = blocked,
                    discoverySources = setOf(ComponentDiscoverySource.PACKAGE_MANAGER)
                )
            }.toList()
        }.sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }, { it.id }))
        return RootComponentScan(items, errors.joinToString(), System.currentTimeMillis())
    }

    suspend fun change(target: RootComponent, enable: Boolean): String {
        val userId = target.user
        require(userId in 0..99999)
        val component = target.component
        require(ComponentStatePolicy.valid(component.packageName, component.className, userId))
        val allowed = query(userId, target.kind) ?: error(context.getString(R.string.root_scan_failed))
        check(component in allowed && component.packageName in (installedPackages(userId) ?: emptySet())) {
            context.getString(R.string.root_component_missing)
        }
        val before = readPackageState(userId, component.packageName)
            ?: error(context.getString(R.string.root_state_read_failed))
        check(before.appEnabled && stateFor(component, before) == target.overrideState) {
            context.getString(R.string.root_component_changed)
        }
        val result = ComponentRootCommand.run(ComponentStatePolicy.command(
            component.packageName, component.className, userId, enable
        ))
        check(result.exitCode == 0 && !result.timedOut) {
            context.getString(R.string.root_command_incomplete)
        }
        val expected = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        var observed: Int? = null
        repeat(5) {
            if (observed != expected) {
                observed = readPackageState(userId, component.packageName)?.let { stateFor(component, it) }
                if (observed != expected) delay(100)
            }
        }
        Log.i("ListCleaner.ProfileRoot", "component=${target.id} expected=$expected observed=$observed")
        check(observed == expected) {
            context.getString(R.string.root_operation_unconfirmed, result.exitCode,
                observed?.toString() ?: context.getString(R.string.common_unknown))
        }
        return context.getString(if (enable) R.string.root_verified_enabled else R.string.root_verified_disabled)
    }
}
