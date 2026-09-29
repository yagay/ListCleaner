package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import com.yagay.ListCleaner.domain.selectedKinds

internal data class RuntimeRuleSnapshot(
    val configured: Set<String>,
    val displayMode: DisplayMode,
    val priorities: PriorityConfig,
    val openTypes: OpenTypeConfig,
    val browserLinks: BrowserLinkConfig,
    val diagnostic: Boolean,
    val managerAppId: Int = -1,
    val digest: String = "",
    val hiddenFromApps: Set<String> = emptySet(),
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
) {
    private val selectedKinds: Set<IntentKind> = selectedKinds(configured)
    val allSelectedPackages: Set<String> = visibilityCompat.activePackages()

    init {
        // Publish every selectable verified rule. Specialized Xposed entries select only the kinds
        // they own, but they must not lose rules merely because a kind was historically classified
        // as an ordinary resolver surface. Assistant/RoleController is the concrete example.
        val selectableRules = configured.filterTo(linkedSetOf()) { ComponentRule.fromId(it) != null }
        val selectablePriorities = priorities.apps.filterKeys(IntentKind::isSelectableEntryKind)
        RuntimeComponentPolicy.publishEntryRules(
            displayMode = displayMode,
            entryRules = selectableRules,
            entryPriorities = selectablePriorities,
        )
    }

    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}
