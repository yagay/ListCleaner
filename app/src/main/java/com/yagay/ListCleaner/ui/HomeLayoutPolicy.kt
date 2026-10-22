package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind

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

    fun startWithComponentList(
        layout: UiHomeLayout,
        componentStart: UiComponentStart,
    ): Boolean = layout == UiHomeLayout.CLASSIC_LIST ||
        componentStart == UiComponentStart.ALL_COMPONENTS
}
