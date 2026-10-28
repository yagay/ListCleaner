package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.yagay.ListCleaner.domain.*

internal data class ProfileEntryScan(
    val candidates: List<ComponentCandidate>,
    val failedSources: List<String>,
    val unavailableSources: List<String> = emptyList(),
)

/** Cross-profile registration scans remain independent: a failed authority is not an empty authority. */
internal class ProfileEntryDiscovery(private val context: Context, private val query: ProfilePackageQuery) {
    fun scan(): ProfileEntryScan {
        val failures = mutableListOf<String>()
        val unavailable = mutableListOf<String>()
        // Fetch all special entry resolver queries in bounded batches first.
        query.prefetchCommands(
            SYSTEM_SERVICE_ENTRY_DEFINITIONS.map {
                Triple(Intent(it.action), PackageManager.GET_META_DATA, "query-services")
            } + listOf(
                Triple(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY, "query-activities"),
                Triple(Intent(Intent.ACTION_ASSIST), PackageManager.MATCH_DEFAULT_ONLY, "query-activities"),
                Triple(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
                    PackageManager.MATCH_DEFAULT_ONLY, "query-activities"),
            )
        )
        val items = buildList {
            SYSTEM_SERVICE_ENTRY_DEFINITIONS.forEach { definition ->
                val matches = runCatching {
                    query.query(Intent(definition.action), PackageManager.GET_META_DATA, "query-services")
                }.onFailure { failure ->
                    failures += "service:${definition.kind.name}:${failure.javaClass.simpleName}"
                }.getOrDefault(emptyList())
                matches.forEach { resolved ->
                    val service = resolved.serviceInfo
                    if (service != null && definition.acceptsPermission(service.permission)) {
                        add(candidate(definition.kind, service))
                    }
                }
            }
            listOf(
                IntentKind.HOME to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                IntentKind.ASSISTANT to Intent(Intent.ACTION_ASSIST),
                IntentKind.BROWSER to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            ).forEach { (kind, intent) ->
                val matches = runCatching { query.query(intent, PackageManager.MATCH_DEFAULT_ONLY) }
                    .onFailure { failure ->
                        failures += "role:${kind.name}:${failure.javaClass.simpleName}"
                    }.getOrDefault(emptyList())
                matches.forEach { resolved ->
                    resolved.activityInfo?.let { add(candidate(kind, it)) }
                }
            }
            // AOSP PackageManagerShellCommand supports query-activities/services/receivers,
            // but not query-content-providers. Do not repeatedly run a nonexistent command,
            // or misreport an unsupported authority query as an empty provider list.
            unavailable += "provider:DOCUMENT_PROVIDER:shell_api_unavailable"
            unavailable += "provider:PHOTO_SOURCE:shell_api_unavailable"
            ObservedEntryCache(context).snapshot().filter { it.userId == query.userId }.forEach { observed ->
                val kind = runCatching { IntentKind.valueOf(observed.kind) }.getOrNull()
                if (kind != null && query.packageInfo(observed.packageName) != null) {
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
        }
        return ProfileEntryScan(items, failures.distinct(), unavailable.distinct())
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
