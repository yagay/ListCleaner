package com.yagay.ListCleaner.domain

/** Same category and rule mode as filtering. A partially hidden app keeps surviving entries. */
fun priorityCandidates(
    candidates: List<ComponentCandidate>, selected: Set<ComponentRule>, mode: DisplayMode, kind: IntentKind,
    extraSelected: Set<ComponentRule> = emptySet()
): List<ComponentCandidate> {
    val effectiveSelected = selected + extraSelected
    val hasSelection = effectiveSelected.any { it.kind == kind }
    return candidates.filter {
        it.rule.kind == kind && it.isCatalogCandidate && mode.includes(it.rule in effectiveSelected, hasSelection)
    }
}

/**
 * Count distinct applications the priority editor can actually reorder in each category.
 *
 * The rule page counts individual components; reusing those counts in the priority editor
 * inflates totals when an app exposes multiple resolver activities. Respect the same mode,
 * candidate availability and optional OPEN/domain scope as [priorityAppGroups].
 * Search, app-type and prioritized/unprioritized filters are intentionally excluded: these
 * category totals describe the full editable scope, not just the currently displayed rows.
 */
fun priorityCategoryAppPackages(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    mode: DisplayMode,
    focusedKind: IntentKind? = null,
    focusedCandidates: List<ComponentCandidate>? = null,
    focusedExtraSelected: Set<ComponentRule> = emptySet(),
): Map<IntentKind, Set<String>> =
    IntentKind.entries.filter { it.isSelectableEntryKind() }.associateWith { kind ->
        val inFocusedScope = kind == focusedKind && focusedCandidates != null
        priorityCandidates(
            candidates = if (inFocusedScope) requireNotNull(focusedCandidates) else candidates,
            selected = selected,
            mode = mode,
            kind = kind,
            extraSelected = if (inFocusedScope) focusedExtraSelected else emptySet(),
        ).asSequence().map { it.rule.packageName }.toSet()
    }

/** An app with entries in several kinds must count only once in its top-level group. */
fun priorityGroupAppCounts(categoryApps: Map<IntentKind, Set<String>>): Map<EntryGroup, Int> =
    EntryGroup.entries.associateWith { group ->
        group.kinds().asSequence().flatMap { categoryApps[it].orEmpty().asSequence() }.toSet().size
    }

fun priorityCategoryAppCounts(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    mode: DisplayMode,
    focusedKind: IntentKind? = null,
    focusedCandidates: List<ComponentCandidate>? = null,
    focusedExtraSelected: Set<ComponentRule> = emptySet(),
): Map<IntentKind, Int> =
    priorityCategoryAppPackages(candidates, selected, mode, focusedKind, focusedCandidates, focusedExtraSelected)
        .mapValues { it.value.size }

/** Swap visible neighbors while preserving hidden saved entries and their slots. */
fun moveVisiblePriority(current: List<String>, visible: List<String>, pkg: String, offset: Int): List<String> {
    if (offset != -1 && offset != 1) return current
    val orderedVisible = current.filter { it in visible }
    val from = orderedVisible.indexOf(pkg)
    val to = from + offset
    if (from < 0 || to !in orderedVisible.indices) return current
    val a = current.indexOf(pkg)
    val b = current.indexOf(orderedVisible[to])
    return current.toMutableList().apply { this[a] = current[b]; this[b] = current[a] }
}

/** Insert at the target's visible position, preserving all hidden saved slots. */
fun moveVisiblePriorityTo(current: List<String>, visible: List<String>, pkg: String, target: String): List<String> {
    val shown = visible.toSet()
    val ordered = current.filter { it in shown }.toMutableList()
    val from = ordered.indexOf(pkg)
    val to = ordered.indexOf(target)
    if (from < 0 || to < 0 || from == to) return current
    ordered.add(to, ordered.removeAt(from))
    var index = 0
    return current.map { if (it in shown) ordered[index++] else it }
}
