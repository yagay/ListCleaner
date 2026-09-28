package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryProtocol
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.listCleanerAppType

/** Discovery for system-managed entry surfaces that are not ordinary Activity resolver results. */
internal class SpecialEntryDiscovery(private val context: Context) {
    private val pm = context.packageManager
    private val flags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA

    fun scan(): List<ComponentCandidate> = (
        launcherShortcutActivities() +
            observedShortcutEntries() +
            documentProviders() +
            systemServiceEntries()
        ).distinctBy { it.rule.id }
        .sortedWith(compareBy<ComponentCandidate>({ it.rule.kind.ordinal }, { it.appLabel.lowercase() }, { it.activityLabel.lowercase() }))

    @Suppress("DEPRECATION")
    private fun launcherShortcutActivities(): List<ComponentCandidate> {
        val managerUid = android.os.Process.myUid()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, flags).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val app = activity.applicationInfo ?: return@mapNotNull null
            if (!activity.enabled || !app.enabled) return@mapNotNull null
            val rule = ComponentRule(IntentKind.LAUNCHER_SHORTCUT, activity.packageName, activity.name)
            if (!rule.isValid()) return@mapNotNull null
            val restricted = FilterPolicy.catalogRestricted(activity.exported, app.uid, managerUid)
            ComponentCandidate(
                rule = rule,
                appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(activity.packageName),
                activityLabel = runCatching { resolved.loadLabel(pm).toString() }
                    .getOrDefault(activity.name.substringAfterLast('.')),
                appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                appType = app.listCleanerAppType(),
                evidence = buildList {
                    add("LAUNCHER_SHORTCUT source=MAIN+LAUNCHER activity=${activity.packageName}/${activity.name}")
                    activity.targetActivity?.takeIf { it.isNotBlank() }?.let { add("activityAlias=${activity.name} targetActivity=$it") }
                    if (restricted) add("RESTRICTED non-exported foreign launcher activity")
                },
                restricted = restricted,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun observedShortcutEntries(): List<ComponentCandidate> {
        val intent = Intent(ObservedEntryProtocol.ACTION).setPackage(ObservedEntryProtocol.PACKAGE)
        return runCatching { pm.queryIntentActivities(intent, flags) }.getOrDefault(emptyList()).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val kind = activity.metaData?.getString(ObservedEntryProtocol.META_KIND)
                ?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() }
                ?.takeIf { it == IntentKind.SHORTCUT_ITEM || it == IntentKind.DIRECT_SHARE }
                ?: return@mapNotNull null
            val rule = ComponentRule(kind, activity.packageName, activity.name)
            if (!rule.isValid()) return@mapNotNull null
            val app = runCatching { pm.getApplicationInfo(activity.packageName, 0) }.getOrNull()
            ComponentCandidate(
                rule = rule,
                appLabel = app?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }
                    ?: activity.packageName,
                activityLabel = resolved.nonLocalizedLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: activity.name.substringAfterLast('.'),
                appIcon = app?.let { runCatching { it.loadIcon(pm).toBitmap(96, 96) }.getOrNull() },
                appType = app?.listCleanerAppType() ?: AppType.USER,
                evidence = buildList {
                    add("OBSERVED_${kind.name} source=system_server")
                    activity.metaData?.getString(ObservedEntryProtocol.META_ACTIVITY)
                        ?.takeIf { it.isNotBlank() }?.let { add("activity=$it") }
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun documentProviders(): List<ComponentCandidate> {
        val managerUid = android.os.Process.myUid()
        val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
        return runCatching { pm.queryIntentContentProviders(intent, flags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val provider = resolved.providerInfo ?: return@mapNotNull null
                val app = provider.applicationInfo ?: return@mapNotNull null
                if (!provider.enabled || !app.enabled) return@mapNotNull null
                val rule = ComponentRule(IntentKind.DOCUMENT_PROVIDER, provider.packageName, provider.name)
                if (!rule.isValid()) return@mapNotNull null
                val restricted = FilterPolicy.catalogRestricted(provider.exported, app.uid, managerUid)
                ComponentCandidate(
                    rule = rule,
                    appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(provider.packageName),
                    activityLabel = runCatching { provider.loadLabel(pm).toString() }
                        .getOrDefault(provider.name.substringAfterLast('.')),
                    appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    appType = app.listCleanerAppType(),
                    evidence = buildList {
                        add("DOCUMENT_PROVIDER source=${DocumentsContract.PROVIDER_INTERFACE}")
                        provider.authority?.takeIf { it.isNotBlank() }?.let { add("authority=$it") }
                        if (restricted) add("RESTRICTED non-exported foreign documents provider")
                    },
                    restricted = restricted,
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun systemServiceEntries(): List<ComponentCandidate> {
        val managerUid = android.os.Process.myUid()
        return SYSTEM_SERVICE_ENTRY_DEFINITIONS.flatMap { definition ->
            runCatching { pm.queryIntentServices(Intent(definition.action), flags) }
                .getOrDefault(emptyList())
                .mapNotNull { resolved ->
                    val service = resolved.serviceInfo ?: return@mapNotNull null
                    val app = service.applicationInfo ?: return@mapNotNull null
                    if (!service.enabled || !app.enabled) return@mapNotNull null
                    if (definition.requiredPermission != null && service.permission != definition.requiredPermission) {
                        return@mapNotNull null
                    }
                    val rule = ComponentRule(definition.kind, service.packageName, service.name)
                    if (!rule.isValid()) return@mapNotNull null
                    val restricted = FilterPolicy.catalogRestricted(service.exported, app.uid, managerUid)
                    ComponentCandidate(
                        rule = rule,
                        appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(service.packageName),
                        activityLabel = runCatching { service.loadLabel(pm).toString() }
                            .getOrDefault(service.name.substringAfterLast('.')),
                        appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                        appType = app.listCleanerAppType(),
                        evidence = buildList {
                            add("SERVICE_ENTRY action=${definition.action}")
                            service.permission?.takeIf { it.isNotBlank() }?.let { add("permission=$it") }
                            if (restricted) add("RESTRICTED non-exported foreign service")
                        },
                        restricted = restricted,
                    )
                }
        }
    }
}
