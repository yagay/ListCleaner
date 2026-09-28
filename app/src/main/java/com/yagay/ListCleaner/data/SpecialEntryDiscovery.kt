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
import com.yagay.ListCleaner.domain.listCleanerAppType

/**
 * Discovers non-Activity resolver surfaces that still behave like user-visible entry lists.
 *
 * These are intentionally represented as ordinary ComponentRule values so selection, locking,
 * backup/restore and search can reuse the existing manager UI. Runtime filtering is implemented by
 * dedicated system hooks; selecting one of these entries never disables the underlying component.
 */
internal class SpecialEntryDiscovery(private val context: Context) {
    private val pm = context.packageManager
    private val flags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA

    fun scan(): List<ComponentCandidate> = launcherShortcutActivities() + documentProviders()

    @Suppress("DEPRECATION")
    private fun launcherShortcutActivities(): List<ComponentCandidate> {
        val managerUid = android.os.Process.myUid()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, flags).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val app = activity.applicationInfo ?: return@mapNotNull null
            if (!activity.enabled || !app.enabled) return@mapNotNull null
            // ShortcutInfo#getActivity returns the published launcher component. Keep aliases exact
            // instead of canonicalizing to targetActivity so runtime matching remains lossless.
            val rule = ComponentRule(
                IntentKind.LAUNCHER_SHORTCUT,
                activity.packageName,
                activity.name,
            )
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
                    if (activity.targetActivity?.isNotBlank() == true) {
                        add("activityAlias=${activity.name} targetActivity=${activity.targetActivity}")
                    }
                    if (restricted) add("RESTRICTED non-exported foreign launcher activity")
                },
                restricted = restricted,
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
                val rule = ComponentRule(
                    IntentKind.DOCUMENT_PROVIDER,
                    provider.packageName,
                    provider.name,
                )
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
                        provider.readPermission?.takeIf { it.isNotBlank() }?.let { add("readPermission=$it") }
                        provider.writePermission?.takeIf { it.isNotBlank() }?.let { add("writePermission=$it") }
                        if (restricted) add("RESTRICTED non-exported foreign documents provider")
                    },
                    restricted = restricted,
                )
            }
    }
}
