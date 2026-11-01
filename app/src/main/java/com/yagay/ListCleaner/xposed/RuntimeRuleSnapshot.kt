package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.selectedKinds

/** Resolver-facing immutable snapshot. Construction is side-effect free. */
internal data class RuntimeRuleSnapshot(
    val configured: Set<String>,
    val displayMode: DisplayMode,
    val modeOverrides: Map<IntentKind, DisplayMode> = emptyMap(),
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
    /** Pre-index once per generation, rather than inspecting rule IDs inside hot resolver hooks. */
    val documentUiPackages: Set<String> = configured.asSequence()
        .mapNotNull(com.yagay.ListCleaner.domain.ComponentRule::fromId)
        .filter { it.kind == IntentKind.DOCUMENT_PROVIDER }
        .mapTo(linkedSetOf()) { it.packageName }

    fun modeFor(kind: IntentKind): DisplayMode = modeOverrides[kind] ?: displayMode
    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}
