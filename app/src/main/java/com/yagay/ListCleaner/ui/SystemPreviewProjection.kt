package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DirectShareType
import com.yagay.ListCleaner.domain.DirectShareTypeRules
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.effectiveResolverPriorities
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.matchOpenPreset
import com.yagay.ListCleaner.domain.prioritizeApps

/**
 * Local, read-only approximation: combine same-Intent queried targets with eligible scan
 * evidence, filter using effective editor selection, then apply precisely the same
 * stable priority promotion used in the Resolver hook. Never claim to capture final OS UI rank,
 * usage predictions, pinned resolver targets, or unobserved sharesheet targets.
 */
internal object SystemPreviewProjection {
    /** Scanned targets belong to many Intents; never label their union a system result. */
    private fun matchesMimeEvidence(candidate: ComponentCandidate, mime: String?, action: String): Boolean {
        val requested = mime?.lowercase()?.substringBefore(';') ?: return true
        val tagged = candidate.evidence.filter { line ->
            (line.startsWith(action) || line.startsWith("BROAD $action") ||
                line.startsWith("CUSTOM ") || line.startsWith("REAL_FILE ")) &&
                "mime=" in line
        }
        if (tagged.isEmpty()) return candidate.evidence.none { "mime=" in it }
        return tagged.any { line ->
            val found = line.substringAfter("mime=", "").substringBefore(' ').lowercase()
            found == requested || found == "*/*" ||
                (found.endsWith("/*") && requested.startsWith(found.substringBefore('/') + "/")) ||
                (requested.endsWith("/*") && found.startsWith(requested.substringBefore('/') + "/"))
        }
    }

    private fun scanMatchesRequest(
        candidate: ComponentCandidate,
        kind: IntentKind,
        scenario: SystemProbeScenario,
        preset: OpenPreset?,
        host: String?,
        directType: DirectShareType?,
        customDefinitions: Map<OpenPreset, com.yagay.ListCleaner.domain.CustomOpenDefinition>,
        mimeOverride: String?,
        selected: Set<ComponentRule>,
    ): Boolean = when (kind) {
        IntentKind.OPEN -> if (preset != null) candidate.matchesOpenPreset(preset, customDefinitions)
            else matchesMimeEvidence(candidate, scenario.mimeType, "VIEW")
        IntentKind.SHARE, IntentKind.SHARE_MULTIPLE ->
            matchesMimeEvidence(candidate, scenario.mimeType, if (kind == IntentKind.SHARE) "SEND " else "SEND_MULTIPLE ")
        IntentKind.DIRECT_SHARE -> directType == null ||
            directType in DirectShareTypeRules.observed(candidate.evidence)
        IntentKind.DEEP_LINK -> {
            val currentHost = host ?: "example.com"
            candidate.matchesBrowserHost(currentHost) || (host != null && candidate.rule in selected)
        }
        IntentKind.EDIT -> matchesMimeEvidence(candidate, scenario.mimeType, "EDIT ")
        else -> true
    }

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
        requestedPreset: OpenPreset? = null,
        directShareType: DirectShareType? = null,
        scopeSelected: Set<ComponentRule>? = null,
        mimeOverride: String? = null,
    ): PreviewListResult? {
        val selectedRules = scopeSelected ?: saved
        val scopedCatalog = scanned.asSequence()
            .filter { it.rule.kind == kind && it.isCatalogCandidate }
            .filter { scanMatchesRequest(
                it, kind, scenario, requestedPreset, requestHost, directShareType,
                openTypes.customDefinitions, mimeOverride, selectedRules,
            ) }
            .map { SystemProbeItem(it.activityLabel, it.rule.packageName, it.rule.className, it.appLabel) }
            .distinctBy { it.component }
            .toList()

        // PackageManager can return fewer rows than discovery because it queries only one
        // MIME, URI scheme and caller. Keep query order first, then evidence-matched scanned
        // extras. This union is only an estimate, never an observed system-visible list.
        val queried = probe?.diagnosticCandidates?.takeIf { it.isNotEmpty() }
            ?: probe?.items?.takeIf { it.isNotEmpty() }
        val known = (queried.orEmpty() + scopedCatalog).distinctBy(SystemProbeItem::component)
        if (known.isEmpty()) return null

        val kindRules = selectedRules.filter { it.kind == kind }
        val selectedIds = kindRules.mapTo(hashSetOf()) { it.id }
        val selectedPackages = kindRules.mapTo(hashSetOf()) { it.packageName }
        val scoped = kind.isPackageScopedEntry()
        val mode = overrides[kind] ?: globalMode
        val visible = known.filter { item ->
            val selected = if (scoped) item.packageName in selectedPackages
                else ComponentRule(kind, item.packageName, item.className).id in selectedIds
            mode.includes(selected, kindRules.isNotEmpty())
        }

        val preset = if (kind == IntentKind.OPEN) requestedPreset ?: matchOpenPreset(
            kind, mimeOverride ?: scenario.mimeType, null,
            customDefinitions = openTypes.customDefinitions,
        ) else null
        val configured = if (mode == DisplayMode.SHOW_ALL) emptyList() else
            effectiveResolverPriorities(
                kind, preset, requestHost, priorities, openTypes, browserLinks,
            )
        // One requesting Android user only; cross-profile results cannot be projected here.
        val ordered = prioritizeApps(visible, configured, SystemProbeItem::packageName) { 0 }
        return PreviewListResult(ordered, PreviewListSource.ESTIMATED)
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
    kind: IntentKind? = null,
    hasPendingDraft: Boolean = false,
    directShareType: DirectShareType? = null,
): PreviewListResult = when {
    mode == PreviewListMode.CURRENT && hasPendingDraft && estimated != null -> estimated
    mode == PreviewListMode.CURRENT && kind == IntentKind.DIRECT_SHARE &&
        observed.source == PreviewListSource.HISTORY && estimated != null -> estimated
    mode == PreviewListMode.CURRENT && kind == IntentKind.DIRECT_SHARE &&
        observed.source == PreviewListSource.HISTORY && directShareType != null ->
        PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
    mode == PreviewListMode.ESTIMATED ->
        estimated ?: PreviewListResult(emptyList(), PreviewListSource.UNAVAILABLE)
    mode == PreviewListMode.CURRENT &&
        observed.source in setOf(PreviewListSource.API_RETURN, PreviewListSource.UNAVAILABLE) &&
        estimated != null -> estimated
    else -> observed
}
