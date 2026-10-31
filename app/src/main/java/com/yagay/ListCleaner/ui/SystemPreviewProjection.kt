package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isPackageScopedEntry

/**
 * Preview-only local estimation. Does not mutate rules or claim to represent
 * the framework's true post-filter result. Applies confirmed selections only.
 */
internal object SystemPreviewProjection {
    fun estimate(
        kind: IntentKind,
        scanned: List<ComponentCandidate>,
        saved: Set<ComponentRule>,
        globalMode: DisplayMode,
        overrides: Map<IntentKind, DisplayMode>,
        probe: SystemProbeResult?,
    ): PreviewListResult? {
        val catalog = scanned.asSequence()
            .filter { it.rule.kind == kind && it.isCatalogCandidate }
            .map {
                SystemProbeItem(it.activityLabel, it.rule.packageName,
                    it.rule.className, it.appLabel)
            }
            .distinctBy { it.component }.toList()
        // Some system APIs only expose a filtered inventory; this fallback
        // may be incomplete and is always explicitly marked as estimated.
        val known = catalog.ifEmpty {
            probe?.diagnosticCandidates?.ifEmpty { probe.items }.orEmpty()
        }
        if (known.isEmpty()) return null

        val kindRules = saved.filter { it.kind == kind }
        val selectedIds = kindRules.mapTo(hashSetOf()) { it.id }
        val selectedPackages = kindRules.mapTo(hashSetOf()) { it.packageName }
        val scoped = kind.isPackageScopedEntry()
        val mode = overrides[kind] ?: globalMode
        val visible = known.filter { item ->
            val selected = if (scoped) item.packageName in selectedPackages
                else ComponentRule(kind, item.packageName, item.className).id in selectedIds
            mode.includes(selected, kindRules.isNotEmpty())
        }
        return PreviewListResult(visible, PreviewListSource.ESTIMATED)
    }
}
