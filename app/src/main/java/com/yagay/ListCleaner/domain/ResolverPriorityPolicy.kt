package com.yagay.ListCleaner.domain

/**
 * Shared priority-source selection for the runtime Resolver hook and the read-only preview.
 * Sorting itself must continue to use [prioritizeApps] so that unranked entries stay stable.
 *
 * The caller must provide the actual request's MIME-derived OPEN preset and DEEP_LINK host.
 * An unrelated editor filter must not change the order of a different system Intent.
 */
fun effectiveResolverPriorities(
    kind: IntentKind,
    preset: OpenPreset?,
    browserHost: String?,
    priorities: PriorityConfig,
    openTypes: OpenTypeConfig,
    browserLinks: BrowserLinkConfig,
): List<String> {
    val typed = if (kind == IntentKind.OPEN && preset != null) {
        openTypes.priorities[preset].orEmpty()
    } else emptyList()
    if (typed.isNotEmpty()) return typed

    val hostPriority = if (kind == IntentKind.DEEP_LINK && browserHost != null) {
        browserLinks.priority(browserHost)
    } else emptyList()
    return if (hostPriority.isNotEmpty()) hostPriority else
        priorities.apps[if (kind in DOCUMENTS_UI_SOURCE_KINDS) IntentKind.DOCUMENT_PROVIDER else kind].orEmpty()
}
