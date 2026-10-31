package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.effectiveResolverPriorities
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.matchOpenPreset
import com.yagay.ListCleaner.domain.prioritizeApps

/**
 * Local, read-only approximation: filter with confirmed rules, then apply precisely the same
 * stable priority promotion used in the Resolver hook. Never claim to capture final OS UI rank,
 * usage predictions, pinned resolver targets, or unobserved sharesheet targets.
 */
internal object SystemPreviewProjection {
    fun estimate(
        kind: IntentKind,
        scanned: List<ComponentCandidate>,
        saved: Set<ComponentRule>,
        globalMode: DisplayMode,
        overrides: Map<IntentKind, DisplayMode>,
        probe: SystemProbeResult?,
        priorities: PriorityConfig = PriorityConfig(),
        openTypes: OpenTypeConfig = OpenTypeConfig(),
        browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
        requestHost: String? = null,
    ): PreviewListResult? {
        val catalog = scanned.asSequence()
            .filter { it.rule.kind == kind && it.isCatalogCandidate }
            .map { SystemProbeItem(it.activityLabel, it.rule.packageName, it.rule.className, it.appLabel) }
            .distinctBy { it.component }
            .toList()

        // Prefer the order returned for THIS probe Intent, before priority promotion.
        // The full discovery cache is alphabetized and includes unrelated MIME scenarios.
        // PM data may already have been filtered by the Hook: this remains an estimate.
        val queried = probe?.diagnosticCandidates?.takeIf { it.isNotEmpty() }
            ?: probe?.items?.takeIf { it.isNotEmpty() }
        val known = queried ?: catalog
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

        val preset = if (kind == IntentKind.OPEN) {
            matchOpenPreset(kind, scenario.mimeType, null, customDefinitions = openTypes.customDefinitions)
        } else null
        val configured = effectiveResolverPriorities(
            kind, preset, requestHost, priorities, openTypes, browserLinks,
        )
        // SystemEntryProbe rejects foreign profiles; the preview candidates here all
        // belong to that one requesting user. Keep other profile slots untouched at runtime.
        val ordered = prioritizeApps(visible, configured, SystemProbeItem::packageName) { 0 }
        return PreviewListResult(ordered, PreviewListSource.ESTIMATED)
    }
}

/**
 * The regular preview always shows one best-effort result.
 * A verified/authority-backed result must keep its captured order unchanged.
 * API-only or unavailable sources may use the clearly-labelled local estimate.
 * Diagnostic tabs (inside Details) still retain their independent provenance.
 */
internal fun preferredPreviewResult(
    mode: PreviewListMode,
    observed: PreviewListResult,
    estimated: PreviewListResult?,
): PreviewListResult = when {
    mode == PreviewListMode.ESTIMATED ->
        estimated ?: PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
    mode == PreviewListMode.CURRENT &&
        observed.source in setOf(PreviewListSource.API_RETURN, PreviewListSource.UNAVAILABLE) &&
        estimated != null -> estimated
    else -> observed
}
