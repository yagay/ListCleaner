package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.supportsPriorityEditing

/** Pure routing decision; mode changes are presentation-only, never scan/rule mutations. */
internal object HomeLayoutPolicy {
    fun showCategoryHome(
        layout: UiHomeLayout,
        kind: IntentKind?,
        query: String,
        browsingAll: Boolean,
    ): Boolean = layout == UiHomeLayout.CATEGORY_CARDS &&
        kind == null && query.isBlank() && !browsingAll

    fun showCategoryFilterBar(layout: UiHomeLayout): Boolean =
        layout == UiHomeLayout.CLASSIC_LIST

    /** Sorting shares the cleanup layout selection; a kind must be chosen in card mode. */
    fun showPriorityCategoryHome(layout: UiHomeLayout, selectedKind: IntentKind?, query: String): Boolean =
        showCategoryHome(layout, selectedKind, query, browsingAll = false)

    /** Hide unsupported Desktop/System surfaces from the classic sorting category menu. */
    val sortableKinds: Set<IntentKind> =
        IntentKind.entries.filter { it.supportsPriorityEditing() }.toSet()

    fun priorityKind(requested: IntentKind?): IntentKind =
        requested?.takeIf { it in sortableKinds } ?: IntentKind.SHARE

    fun startWithComponentList(
        layout: UiHomeLayout,
        componentStart: UiComponentStart,
    ): Boolean = layout == UiHomeLayout.CLASSIC_LIST ||
        componentStart == UiComponentStart.ALL_COMPONENTS
}
