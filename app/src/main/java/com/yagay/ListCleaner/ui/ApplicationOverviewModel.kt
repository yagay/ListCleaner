package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.OpenTypeConfig

/** Include current candidates and saved rules, including per-type and per-host rules.
 * The caller already holds the selected Android user's repository state.
 */
internal fun overviewRules(
    packageName: String,
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    openTypes: OpenTypeConfig,
    browserLinks: BrowserLinkConfig
): List<ComponentRule> {
    val scopedIds = openTypes.rules.values.asSequence().flatMap { it.asSequence() } +
        browserLinks.rules.values.asSequence().flatMap { it.asSequence() }
    return (candidates.asSequence().map { it.rule } + selected.asSequence() +
        scopedIds.mapNotNull(ComponentRule::fromId))
        .filter { it.packageName == packageName }
        .distinctBy { it.id }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.className }))
        .toList()
}

/** One priority source per row, avoiding raw enum names and category collisions. */
internal data class OverviewPriority(
    val position: Int,
    val kind: com.yagay.ListCleaner.domain.IntentKind? = null,
    val preset: com.yagay.ListCleaner.domain.OpenPreset? = null,
    val host: String? = null
) {
    val key: String get() = when {
        kind != null -> "entry|" + kind.name
        preset != null -> "type|" + preset.name
        else -> "host|" + host.orEmpty()
    }
}

internal fun overviewPriorities(
    packageName: String,
    priorities: com.yagay.ListCleaner.domain.PriorityConfig,
    openTypes: OpenTypeConfig,
    browserLinks: BrowserLinkConfig
): List<OverviewPriority> = buildList {
    priorities.apps.toSortedMap(compareBy { it.ordinal }).forEach { (kind, packages) ->
        val rank = packages.indexOf(packageName)
        if (rank >= 0) add(OverviewPriority(rank + 1, kind = kind))
    }
    openTypes.priorities.toSortedMap(compareBy { it.ordinal }).forEach { (preset, packages) ->
        val rank = packages.indexOf(packageName)
        if (rank >= 0) add(OverviewPriority(rank + 1, preset = preset))
    }
    browserLinks.priorities.toSortedMap().forEach { (host, packages) ->
        val rank = packages.indexOf(packageName)
        if (rank >= 0) add(OverviewPriority(rank + 1, host = host))
    }
}
