package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.isPackageScopedEntry

/**
 * A single, source-first preview pipeline. A broad catalog scan is NEVER an input.
 *
 * The current system tab uses a strictly request-bound framework observation (or the
 * current PackageManager candidate query, labelled as such). Drafts are simulated only
 * over the exact query inventory. No history or scan cache can silently replace the result.
 */
internal enum class PreviewV2Source {
    ADAPTER_OBSERVED, FILTERED_OBSERVED, DOCUMENT_ROOTS,
    ENABLED, INTENT_CANDIDATES, DRAFT_SIMULATION, HISTORY, UNAVAILABLE
}

internal data class PreviewV2Result(
    val items: List<SystemProbeItem>,
    val source: PreviewV2Source,
) {
    val isObserved: Boolean get() = source in setOf(
        PreviewV2Source.ADAPTER_OBSERVED, PreviewV2Source.FILTERED_OBSERVED,
        PreviewV2Source.DOCUMENT_ROOTS, PreviewV2Source.ENABLED,
    )
}

/** Pure decision engine: no Compose, Android Context, preference writes, or shell commands. */
internal object SystemPreviewV2 {
    private const val MAX_AGE_MS = 120_000L

    fun resolve(
        request: SystemProbeRequest,
        probe: SystemProbeResult?,
        atMillis: Long,
        queryStartedAt: Long? = null,
        systemLaunchedAt: Long? = null,
        managerUid: Int = -1,
        expectedAction: String? = null,
        expectedMime: String? = null,
        expectedHost: String? = null,
        pendingDraft: Boolean = false,
        selected: Set<ComponentRule> = emptySet(),
        globalMode: DisplayMode = DisplayMode.HIDE_SELECTED,
        overrides: Map<IntentKind, DisplayMode> = emptyMap(),
        historyRequested: Boolean = false,
    ): PreviewV2Result {
        if (probe == null || !probe.supported || probe.failure) return unavailable()
        val kind = request.entryKind
        val apiItems = probe.items.distinctBy(SystemProbeItem::component)
        if (kind == null) return when {
            historyRequested -> unavailable()
            apiItems.isNotEmpty() -> PreviewV2Result(apiItems, PreviewV2Source.INTENT_CANDIDATES)
            else -> unavailable()
        }

        if (historyRequested) {
            val history = probe.historicalItems.distinctBy(SystemProbeItem::component)
            return if (history.isEmpty()) unavailable() else
                PreviewV2Result(history, PreviewV2Source.HISTORY)
        }

        // An uncommitted selection is NOT active in Android. Project it only over the
        // matching current API inventory; never combine it with broad scan results.
        if (pendingDraft) {
            if (kind.previewAuthority() !in setOf(
                    PreviewAuthority.RESOLVER_CANDIDATES, PreviewAuthority.ROLE_CANDIDATES,
                )) return unavailable()
            val candidates = (probe.diagnosticCandidates.takeIf { it.isNotEmpty() }
                ?: apiItems).distinctBy(SystemProbeItem::component)
            return PreviewV2Result(
                simulate(kind, candidates, selected, overrides[kind] ?: globalMode),
                PreviewV2Source.DRAFT_SIMULATION,
            )
        }

        val ordered = probe.orderedReturn
        if (systemLaunchedAt != null && exactCapture(
                ordered, kind, request.userId, atMillis, systemLaunchedAt,
                expectedAction, expectedMime, expectedHost,
            ) && ordered?.source in setOf("CHOOSER_ADAPTER_ORDER", "RESOLVER_RANKED_ORDER")
            && ordered?.callerPackage?.substringBefore(':') in setOf(
                "android", "system", "com.android.intentresolver",
            )
        ) {
            return PreviewV2Result(toItems(requireNotNull(ordered), apiItems),
                PreviewV2Source.ADAPTER_OBSERVED)
        }

        // The manager-initiated PackageManager query can be attributed to the tested
        // request. Never treat a capture from another caller or another MIME as ours.
        val returned = probe.runtimeReturn
        if (managerUid >= 0 && returned != null && returned.callerUid == managerUid &&
            returned.source == "PACKAGE_MANAGER_FILTERED_RETURN" &&
            queryStartedAt != null && exactCapture(
                returned, kind, request.userId, atMillis, queryStartedAt,
                expectedAction, expectedMime, expectedHost,
            )
        ) {
            return PreviewV2Result(toItems(returned, apiItems),
                PreviewV2Source.FILTERED_OBSERVED)
        }

        // Private DocumentsUI roots are only usable when the picker verifier matched
        // the tested operation and launch; a registration is not a displayed root.
        val picker = probe.matchingRootsSnapshot
        if (kind in setOf(IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
                IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT) &&
            probe.pickerProbeStatus == PickerProbeStatus.OBSERVED_AFTER_TEST &&
            picker != null && picker.userId == request.userId &&
            picker.requestKind == kind.name && !picker.truncated &&
            systemLaunchedAt != null && picker.capturedAt >= systemLaunchedAt
        ) {
            val roots = picker.roots.map {
                SystemProbeItem(it.label, it.packageName,
                    it.syntheticId + "@user" + (it.userId?.toString() ?: "unknown"),
                    it.label)
            }
            val apps = picker.apps.map {
                SystemProbeItem(it.label, it.packageName,
                    it.className + "@user" + (it.userId?.toString() ?: "unknown"),
                    it.label)
            }
            return PreviewV2Result(roots + apps, PreviewV2Source.DOCUMENT_ROOTS)
        }

        if (probe.evidence == ProbeEvidence.ENABLED) {
            return PreviewV2Result(probe.enabledItems.ifEmpty { apiItems }
                .distinctBy(SystemProbeItem::component), PreviewV2Source.ENABLED)
        }

        // Candidate lists may still differ from the actual Resolver/Chooser UI.
        // This distinction is visible and is NEVER called a verified screen.
        if (kind.previewAuthority() in setOf(
                PreviewAuthority.RESOLVER_CANDIDATES, PreviewAuthority.ROLE_CANDIDATES,
                PreviewAuthority.SERVICE_CANDIDATES, PreviewAuthority.PROVIDER_CANDIDATES,
            )
        ) return PreviewV2Result(
            (probe.diagnosticCandidates.takeIf { it.isNotEmpty() } ?: apiItems)
                .distinctBy(SystemProbeItem::component),
            PreviewV2Source.INTENT_CANDIDATES,
        )
        return unavailable()
    }

    private fun exactCapture(
        value: RuntimeInterfaceSnapshot?,
        kind: IntentKind,
        userId: Int,
        atMillis: Long,
        startedAt: Long,
        action: String?,
        mime: String?,
        host: String?,
    ): Boolean =
        value != null && !value.truncated && value.kind == kind.name &&
            value.callerUserId == userId &&
            atMillis - value.capturedAtMillis in 0L..MAX_AGE_MS &&
            value.capturedAtMillis >= startedAt &&
            action != null && value.requestAction == action &&
            value.requestMimeType == mime.orEmpty() &&
            value.requestHost == host.orEmpty()

    private fun toItems(
        capture: RuntimeInterfaceSnapshot,
        queried: List<SystemProbeItem>,
    ): List<SystemProbeItem> {
        val byId = queried.associateBy(SystemProbeItem::component)
        val byPackage = queried.associateBy(SystemProbeItem::packageName)
        return if (capture.components.isNotEmpty()) capture.components.map { id ->
            byId[id] ?: SystemProbeItem(
                id.substringAfter('/').substringAfterLast('.'),
                id.substringBefore('/'), id.substringAfter('/'),
                byPackage[id.substringBefore('/')]?.appLabel ?: id.substringBefore('/'),
            )
        } else capture.packages.map { pkg ->
            val existing = byPackage[pkg]
            SystemProbeItem(existing?.appLabel ?: pkg, pkg,
                "@captured/" + capture.kind, existing?.appLabel ?: pkg)
        }
    }

    private fun simulate(
        kind: IntentKind,
        candidates: List<SystemProbeItem>,
        selected: Set<ComponentRule>,
        mode: DisplayMode,
    ): List<SystemProbeItem> {
        val scoped = selected.filter { it.kind == kind }
        val byId = scoped.mapTo(hashSetOf()) { it.id }
        val byPackage = scoped.mapTo(hashSetOf()) { it.packageName }
        val packageScoped = kind.isPackageScopedEntry()
        return candidates.filter {
            val matches = if (packageScoped) it.packageName in byPackage
                else ComponentRule(kind, it.packageName, it.className).id in byId
            mode.includes(matches, scoped.isNotEmpty())
        }
    }

    private fun unavailable() = PreviewV2Result(emptyList(), PreviewV2Source.UNAVAILABLE)
}
