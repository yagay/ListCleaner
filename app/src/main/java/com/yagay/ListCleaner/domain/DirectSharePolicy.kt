package com.yagay.ListCleaner.domain

/**
 * Keeps Direct Share targets consistent with the app-level Share resolver that Android shows below
 * them. Unknown target packages are preserved so reflection/API drift fails open.
 */
internal fun directShareVisibleIndices(
    targetPackages: List<String?>,
    visibleSharePackages: Set<String>
): List<Int> {
    if (visibleSharePackages.isEmpty()) return targetPackages.indices.toList()
    return targetPackages.indices.filter { index ->
        val packageName = targetPackages[index]
        packageName == null || packageName in visibleSharePackages
    }
}
