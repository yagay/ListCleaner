package com.yagay.ListCleaner.domain

/**
 * Keeps Direct Share targets consistent with the app-level Share resolver that Android shows below
 * them. Unknown target packages are preserved so reflection/API drift fails open.
 */
internal fun directShareVisibleIndices(
    targetPackages: List<String?>,
    visibleSharePackages: Set<String>
): List<Int> = targetPackages.indices.filter { index ->
    val packageName = targetPackages[index]
    packageName == null || packageName in visibleSharePackages
}

/** Pure policy used by the Chooser hook so per-target filtering and ordering stay unit-testable. */
internal fun directShareFilteredIndices(
    targetPackages: List<String?>,
    ruleIds: List<String?>,
    visibleSharePackages: Set<String>,
    selectedRuleIds: Set<String>,
    displayMode: DisplayMode,
    priorities: List<String>,
): List<Int> {
    var kept = directShareVisibleIndices(targetPackages, visibleSharePackages)
    if (ruleIds.size == targetPackages.size && displayMode != DisplayMode.SHOW_ALL && selectedRuleIds.isNotEmpty()) {
        kept = kept.filter { index ->
            val ruleId = ruleIds[index] ?: return@filter true
            displayMode.includes(ruleId in selectedRuleIds, true)
        }
    }
    if (priorities.isEmpty() || kept.size < 2) return kept

    val rank = priorities.distinct().withIndex().associate { (index, packageName) -> packageName to index }
    return kept.sortedWith(
        compareBy<Int> { index -> rank[targetPackages[index]] ?: Int.MAX_VALUE }
            .thenBy { it }
    )
}
