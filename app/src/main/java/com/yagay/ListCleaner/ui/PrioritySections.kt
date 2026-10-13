package com.yagay.ListCleaner.ui

/**
 * Always display prioritized entries first, ordered by their saved rank.
 * Other entries keep the existing candidate order.
 */
internal fun <T> orderedPriorityItems(
    items: List<T>,
    rankOf: (T) -> Int?
): List<T> = items.filter { rankOf(it) != null }
    .sortedWith(compareBy { rankOf(it) })
    .plus(items.filter { rankOf(it) == null })

/** Section breaks only when transitioning from priority to default items. */
internal fun <T> beginsPrioritySection(
    items: List<T>,
    index: Int,
    rankOf: (T) -> Int?
): Boolean = index == 0 ||
    (rankOf(items[index - 1]) != null) != (rankOf(items[index]) != null)
