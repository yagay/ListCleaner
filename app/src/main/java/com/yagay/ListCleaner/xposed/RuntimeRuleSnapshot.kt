package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.isSpecialEntrySurface
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
        RuntimeComponentPolicy.publishEntryRules(
            displayMode = displayMode,
            entryRules = configured.filterTo(linkedSetOf()) { id ->
                ComponentRule.fromId(id)?.kind?.isSpecialEntrySurface() == true
            },
            entryPriorities = priorities.apps.filterKeys { it.isSpecialEntrySurface() },
        )
    }

    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}
