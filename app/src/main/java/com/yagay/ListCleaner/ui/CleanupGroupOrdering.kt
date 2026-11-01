package com.yagay.ListCleaner.ui

/**
 * One ordering contract for Entry candidates, Root components and managed-rule
 * lists. Presentation-only: never reorder or change persisted cleanup rules.
 * Rank is precomputed by callers and used only by SELECTED_FIRST.
 */
internal data class CleanupGroupOrderKey(
    val stableId: String,
    val displayName: String,
    val packageName: String,
    val selectionRank: Int = 2,
)

internal fun <T> orderCleanupAppGroups(
    groups: List<T>,
    sort: UiAppSort,
    keyOf: (T) -> CleanupGroupOrderKey,
): List<T> {
    val keyed = groups.map { it to keyOf(it) }
    val comparator = when (sort) {
        UiAppSort.SELECTED_FIRST -> compareBy<Pair<T, CleanupGroupOrderKey>> { it.second.selectionRank }
            .thenBy { it.second.displayName.lowercase() }
            .thenBy { it.second.stableId }
        UiAppSort.NAME -> compareBy<Pair<T, CleanupGroupOrderKey>> { it.second.displayName.lowercase() }
            .thenBy { it.second.stableId }
        UiAppSort.PACKAGE -> compareBy<Pair<T, CleanupGroupOrderKey>> { it.second.packageName.lowercase() }
            .thenBy { it.second.stableId }
    }
    return keyed.sortedWith(comparator).map { it.first }
}
