package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.yagay.ListCleaner.domain.*

/** Cross-profile registrations and user-scoped history. Final role/manager eligibility stays provisional. */
internal class ProfileEntryDiscovery(private val context: Context, private val query: ProfilePackageQuery) {
    fun scan(): List<ComponentCandidate> = buildList {
        SYSTEM_SERVICE_ENTRY_DEFINITIONS.forEach { definition ->
            query.query(Intent(definition.action), PackageManager.GET_META_DATA, "query-services").forEach { resolved ->
                val service = resolved.serviceInfo ?: return@forEach
                if (definition.acceptsPermission(service.permission)) add(candidate(definition.kind, service))
            }
        }
        listOf(
            IntentKind.HOME to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            IntentKind.ASSISTANT to Intent(Intent.ACTION_ASSIST),
            IntentKind.BROWSER to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
        ).forEach { (kind, intent) ->
            query.query(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { resolved ->
                resolved.activityInfo?.let { add(candidate(kind, it)) }
            }
        }
        listOf(
            IntentKind.DOCUMENT_PROVIDER to "android.content.action.DOCUMENTS_PROVIDER",
            IntentKind.PHOTO_SOURCE to "android.content.action.CLOUD_MEDIA_PROVIDER",
        ).forEach { (kind, action) ->
            runCatching { query.query(Intent(action), PackageManager.GET_META_DATA, "query-content-providers") }
                .onFailure { android.util.Log.w("ListCleaner.Profile", "Provider query unavailable for user=${query.userId}", it) }
                .getOrDefault(emptyList()).forEach { resolved ->
                resolved.providerInfo?.let { add(candidate(kind, it)) }
            }
        }
        ObservedEntryCache(context).snapshot().filter { it.userId == query.userId }.forEach { observed ->
            val kind = runCatching { IntentKind.valueOf(observed.kind) }.getOrNull() ?: return@forEach
            if (query.packageInfo(observed.packageName) == null) return@forEach
            add(ComponentCandidate(
                rule = ComponentRule(kind, observed.packageName, observed.syntheticClass),
                appLabel = observed.packageName, activityLabel = observed.label,
                appIcon = null,
                evidence = listOf("AUTHORITY_OBSERVED source=cache_history userId=${query.userId}",
                    "observedAt=${observed.observedAt}"),
                unavailable = true,
            ))
        }
    }

    private fun candidate(kind: IntentKind, info: ComponentInfo): ComponentCandidate {
        val rule = if (kind.isPackageScopedEntry()) SyntheticEntryKeys.packageScopedRule(kind, info.packageName)
            else ComponentRule(kind, info.packageName, info.name)
        return ComponentCandidate(
            rule = rule,
            appLabel = runCatching { info.applicationInfo.loadLabel(context.packageManager).toString() }
                .getOrDefault(info.packageName),
            activityLabel = runCatching { info.loadLabel(context.packageManager).toString() }.getOrDefault(info.name),
            appIcon = null,
            appType = info.applicationInfo.listCleanerAppType(),
            restricted = !info.exported,
            evidence = listOf("PROFILE_REGISTRATION userId=${query.userId} finalEligibility=unverified"),
        )
    }
}
