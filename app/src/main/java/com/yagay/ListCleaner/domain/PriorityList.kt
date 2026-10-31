package com.yagay.ListCleaner.domain

enum class PriorityListFilter { ALL, UNSELECTED, SELECTED }

data class PriorityAppGroup(
    val packageName: String,
    val components: List<ComponentCandidate>,
    val rank: Int?,
    val appType: AppType
)

/** Priority apps lead in saved order; remaining apps use their default alphabetic order. */
fun priorityAppGroups(
    candidates: List<ComponentCandidate>, selected: Set<ComponentRule>, mode: DisplayMode,
    kind: IntentKind, saved: List<String>, query: String, filter: PriorityListFilter,
    extraSelected: Set<ComponentRule> = emptySet(),
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL
): List<PriorityAppGroup> {
    // Exclude hidden application types BEFORE grouping and ranking. This is
    // a presentation-only filter; saved priority IDs, including system apps,
    // remain in storage and retain their order when the filter changes.
    val apps = priorityCandidates(candidates, selected, mode, kind, extraSelected)
        .filter { appTypeFilter.matches(it.appType) }
        .groupBy { it.rule.packageName }
    val ranks = saved.filter { it in apps }.mapIndexed { index, pkg -> pkg to index + 1 }.toMap()
    val needle = query.trim().lowercase()
    val groups = apps.mapNotNull { (pkg, components) ->
        val rank = ranks[pkg]
        if (filter == PriorityListFilter.SELECTED && rank == null ||
            filter == PriorityListFilter.UNSELECTED && rank != null) return@mapNotNull null
        val matching = components.filter { it.matchesNormalizedQuery(needle) }
            .sortedWith(compareBy({ it.activityLabel.lowercase() }, { it.rule.id }))
        if (matching.isEmpty()) null else PriorityAppGroup(pkg, matching, rank, matching.first().appType)
    }
    return groups.sortedWith(compareBy<PriorityAppGroup>({ it.rank ?: Int.MAX_VALUE },
        { it.components.first().appLabel.lowercase() }, { it.packageName }))
}
