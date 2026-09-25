package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.normalizeBrowserHost

data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val appType: AppType,
    val components: List<ComponentCandidate>
)

private fun appSelectionRank(group: AppGroup, selected: Set<ComponentRule>): Int {
    val selectedCount = group.components.count { it.rule in selected }
    return when {
        group.components.isNotEmpty() && selectedCount == group.components.size -> 0
        selectedCount > 0 -> 1
        else -> 2
    }
}

internal fun baseAppGroups(candidates: List<ComponentCandidate>): List<AppGroup> =
    candidates.groupBy { it.rule.packageName }.map { (_, all) ->
        val first = all.first()
        AppGroup(
            first.rule.packageName,
            first.appLabel,
            first.appIcon,
            first.appType,
            all.sortedBy { it.rule.kind.ordinal }
        )
    }

internal fun filterAppGroups(
    groups: List<AppGroup>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = groups.mapNotNull { group ->
    val matching = group.components.filter {
        val isSelected = it.rule in selected
        val matchesUiFilter = when (uiFilter) {
            UiFilter.ALL -> true
            UiFilter.HIDE_SELECTED -> !isSelected
            UiFilter.SHOW_SELECTED -> isSelected
            UiFilter.LOCKED -> true
        }
        catalogVisible(it, isSelected, uiFilter) && matchesUiFilter &&
            (filter == null || it.rule.kind == filter) && it.matchesQuery(query)
    }
    if (matching.isEmpty()) null else group.copy(components = matching)
}.sortedWith(
    compareBy<AppGroup> { appSelectionRank(it, selected) }
        .thenBy { it.components.firstOrNull()?.normalizedAppLabel ?: it.appLabel.lowercase() }
        .thenBy { it.packageName }
)

fun groupCandidates(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = filterAppGroups(baseAppGroups(candidates), selected, filter, query, uiFilter)

fun retainConfiguredCandidates(
    items: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    unavailableEvidence: String = "Configured but not observed during this scan; this does not mean the app is uninstalled"
): List<ComponentCandidate> {
    val kept = items.filter { !it.unavailable || it.rule in selected }
    val ids = kept.map { it.rule.id }.toSet()
    return kept + selected.filter { it.id !in ids }.map { rule ->
        ComponentCandidate(
            rule,
            rule.packageName,
            rule.className.substringAfterLast('.'),
            evidence = listOf(unavailableEvidence),
            unavailable = true
        )
    }
}

internal fun availableDeepLinkHosts(
    configuredHosts: Set<String>,
    discoveredHosts: Set<String>,
    candidates: List<ComponentCandidate>
): Set<String> {
    val normalizedDiscovered = discoveredHosts.mapNotNull(::normalizeBrowserHost).toSet()
    val matched = candidates.asSequence()
        .filter { it.rule.kind == IntentKind.DEEP_LINK && it.isCatalogCandidate }
        .flatMap { it.browserHosts.asSequence() }
        .mapNotNull(::normalizeBrowserHost)
        .filter { it in normalizedDiscovered }
        .toSet()
    return configuredHosts.mapNotNull(::normalizeBrowserHost).toSet() + matched
}

internal fun catalogVisible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter): Boolean =
    item.isCatalogCandidate || (selected && (uiFilter == UiFilter.SHOW_SELECTED || uiFilter == UiFilter.LOCKED))
