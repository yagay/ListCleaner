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

/**
 * Single source of truth for candidate retention and UI visibility.
 *
 * Discovery state (available/unavailable/restricted), selection state and the current UI filter
 * are deliberately evaluated here so refreshes cannot accidentally change list membership through
 * a second, slightly different visibility rule elsewhere.
 */
internal object CandidateVisibilityPolicy {
    private fun isObservedShortcutHistory(item: ComponentCandidate): Boolean =
        item.rule.kind in setOf(IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM) &&
            item.evidence.any { it.startsWith("AUTHORITY_OBSERVED ") &&
                "source=cache_history" in it }

    fun retainAfterRefresh(item: ComponentCandidate, selected: Boolean): Boolean =
        !item.unavailable || selected || isObservedShortcutHistory(item)

    fun visible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter,
                preferences: UiPreferences = UiPreferencesRuntime.current,
                nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!matchesSelectionFilter(selected, uiFilter)) return false
        val historical = isObservedShortcutHistory(item)
        if (historical && !selected) {
            if (!preferences.showShortcutHistory) return false
            val days = preferences.historyVisibilityDays
            if (days > 0) {
                val seen = item.evidence.firstOrNull { it.startsWith("observedAt=") }
                    ?.substringAfter('=')?.toLongOrNull() ?: return false
                if (seen <= 0L || seen > nowMillis || nowMillis - seen > days * 86_400_000L) return false
            }
        }
        if (preferences.candidateSource != UiCandidateSource.ALL) {
            val matchesSource = when (preferences.candidateSource) {
                UiCandidateSource.ALL -> true
                UiCandidateSource.OBSERVED ->
                    item.evidence.any { it.startsWith("AUTHORITY_OBSERVED ") &&
                        ("source=recent_runtime" in it || "source=live" in it) }
                UiCandidateSource.PROACTIVE ->
                    item.evidence.any { it.startsWith("ACTIVE_SHORTCUT_CANDIDATE ") }
                UiCandidateSource.HISTORY -> historical
            }
            if (!matchesSource) return false
        }

        return when {
            item.isCatalogCandidate -> true
            // Restricted discoveries stay isolated from ALL/Unselected even if configured.
            item.restricted -> selected &&
                (uiFilter == UiFilter.SHOW_SELECTED || uiFilter == UiFilter.LOCKED)
            // A configured rule may temporarily disappear from runtime discovery after refresh.
            // Keep it manageable in ALL/Selected/Locked, but never in Unselected.
            // Historical Direct Share rows remain searchable, but retain unavailable=true
            // and never claim a current system Chooser result.
            item.unavailable && isObservedShortcutHistory(item) ->
                uiFilter == UiFilter.ALL || uiFilter == UiFilter.HIDE_SELECTED ||
                    (selected && uiFilter == UiFilter.SHOW_SELECTED)
            item.unavailable -> selected && uiFilter != UiFilter.HIDE_SELECTED
            else -> false
        }
    }

    private fun matchesSelectionFilter(selected: Boolean, uiFilter: UiFilter): Boolean = when (uiFilter) {
        UiFilter.ALL -> true
        UiFilter.HIDE_SELECTED -> !selected
        UiFilter.SHOW_SELECTED -> selected
        UiFilter.LOCKED -> true
    }
}

/** Compatibility facade for diagnostics; all decisions still come from CandidateVisibilityPolicy. */
internal fun catalogVisible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter): Boolean =
    CandidateVisibilityPolicy.visible(item, selected, uiFilter)

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
    uiFilter: UiFilter,
    sortMode: UiAppSort = UiAppSort.SELECTED_FIRST,
    preferences: UiPreferences = UiPreferencesRuntime.current,
): List<AppGroup> = groups.mapNotNull { group ->
    val matching = group.components.filter { item ->
        val isSelected = item.rule in selected
        CandidateVisibilityPolicy.visible(item, isSelected, uiFilter, preferences) &&
            (filter == null || item.rule.kind == filter) &&
            item.matchesQuery(query)
    }
    if (matching.isEmpty()) null else group.copy(components = matching)
}.sortedWith(
    when (sortMode) {
        UiAppSort.SELECTED_FIRST -> compareBy<AppGroup> { appSelectionRank(it, selected) }
            .thenBy { it.components.firstOrNull()?.normalizedAppLabel ?: it.appLabel.lowercase() }
            .thenBy { it.packageName }
        UiAppSort.NAME -> compareBy<AppGroup> {
            it.components.firstOrNull()?.normalizedAppLabel ?: it.appLabel.lowercase()
        }.thenBy { it.packageName }
        UiAppSort.PACKAGE -> compareBy<AppGroup> { it.packageName.lowercase() }
    }
)

internal fun groupCandidates(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter,
    sortMode: UiAppSort = UiAppSort.SELECTED_FIRST,
    preferences: UiPreferences = UiPreferencesRuntime.current,
): List<AppGroup> = filterAppGroups(baseAppGroups(candidates), selected, filter, query, uiFilter, sortMode, preferences)

fun retainConfiguredCandidates(
    items: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    unavailableEvidence: String = "Configured but not observed during this scan; this does not mean the app is uninstalled"
): List<ComponentCandidate> {
    val kept = items.filter { item ->
        CandidateVisibilityPolicy.retainAfterRefresh(item, item.rule in selected)
    }
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
