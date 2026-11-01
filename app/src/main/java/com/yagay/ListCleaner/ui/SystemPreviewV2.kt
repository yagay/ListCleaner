package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot

/**
 * A single, source-first preview pipeline. A broad catalog scan is NEVER an input.
 *
 * The current system tab uses a strictly request-bound framework observation (or the
 * current PackageManager candidate query, labelled as such). Drafts are simulated only
 * over the exact query inventory. No history or scan cache can silently replace the result.
 */
internal enum class PreviewV2Source {
    ADAPTER_OBSERVED, FILTERED_OBSERVED, DOCUMENT_ROOTS,
    ENABLED, INTENT_CANDIDATES, ROLE_ELIGIBILITY, ROLE_POLICY_PROJECTED,
    ROLE_CANDIDATES, ROLE_HOLDER, DRAFT_SIMULATION, HISTORY, ROOT_STATE,
    ROOT_DRAFT_SIMULATION, POLICY_PROJECTED, UNAVAILABLE
}

internal data class PreviewV2Result(
    val items: List<SystemProbeItem>,
    val source: PreviewV2Source,
) {
    val isObserved: Boolean get() = source in setOf(
        PreviewV2Source.ADAPTER_OBSERVED, PreviewV2Source.FILTERED_OBSERVED,
        PreviewV2Source.DOCUMENT_ROOTS, PreviewV2Source.ENABLED,
        PreviewV2Source.ROLE_ELIGIBILITY,
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
        rootSnapshot: RootPreviewSnapshot? = null,
    ): PreviewV2Result {
        val kind = request.entryKind
        if (kind == null) {
            val rootKind = request.componentKind ?: return unavailable()
            val snapshot = rootSnapshot?.takeIf { it.userId == request.userId }
                ?: return unavailable()
            return PreviewV2Result(snapshot.visible(rootKind),
                if (snapshot.pendingDraft) PreviewV2Source.ROOT_DRAFT_SIMULATION
                else PreviewV2Source.ROOT_STATE)
        }
        if (probe == null || probe.failure) return unavailable()
        // Historical private targets are useful even when Android offers NO public live API.
        if (historyRequested) {
            val history = probe.historicalItems.distinctBy(SystemProbeItem::component)
            return if (history.isEmpty()) unavailable() else
                PreviewV2Result(history, PreviewV2Source.HISTORY)
        }
        if (!probe.supported) return unavailable()
        val apiItems = probe.items.distinctBy(SystemProbeItem::component)
        val adapter = PreviewContracts.forEntry(kind).adapter
        fun projected(items: List<SystemProbeItem>, source: PreviewV2Source): PreviewV2Result {
            val visible = EffectivePreviewPolicy.visible(kind, items, selected, globalMode, overrides)
            return PreviewV2Result(visible, when {
                pendingDraft -> PreviewV2Source.DRAFT_SIMULATION
                visible != items && source in setOf(
                    PreviewV2Source.ADAPTER_OBSERVED, PreviewV2Source.FILTERED_OBSERVED,
                    PreviewV2Source.ENABLED, PreviewV2Source.DOCUMENT_ROOTS,
                ) -> PreviewV2Source.POLICY_PROJECTED
                else -> source
            })
        }

        if (adapter == PreviewAdapter.ROLE_CONTROLLER) {
            // RoleController's getQualifyingPackagesAsUser() (after ListCleaner Hook
            // filtering) supplies the actual eligibility decision. Its snapshot has
            // a separate storage key and is NEVER an ACTION_ASSIST resolver return.
            val eligible = probe.roleEligibilityReturn?.takeIf { capture ->
                capture.source == "ROLE_CONTROLLER_RETURN" &&
                    capture.kind == kind.name &&
                    capture.requestAction == "android.app.role." + kind.name &&
                    capture.callerPackage.substringBefore(':') in setOf(
                        "com.android.permissioncontroller", "com.google.android.permissioncontroller"
                    ) &&
                    capture.callerUserId == request.userId &&
                    !capture.truncated &&
                    atMillis - capture.capturedAtMillis in 0L..MAX_AGE_MS
            }
            val candidates = eligible?.let { toItems(it, probe.diagnosticCandidates) }
                ?: probe.diagnosticCandidates.distinctBy(SystemProbeItem::packageName)
            val visible = EffectivePreviewPolicy.visible(
                kind, candidates, selected, globalMode, overrides,
            )
            if (pendingDraft) {
                return PreviewV2Result(visible, PreviewV2Source.DRAFT_SIMULATION)
            }
            if (eligible != null) {
                return PreviewV2Result(
                    visible,
                    if (visible == candidates) PreviewV2Source.ROLE_ELIGIBILITY
                    else PreviewV2Source.ROLE_POLICY_PROJECTED,
                )
            }
            if (candidates.isNotEmpty()) {
                // These are potential registrations, never the final Android role list.
                return PreviewV2Result(visible, PreviewV2Source.ROLE_CANDIDATES)
            }
            // A RoleManager holder represents the current default, not every eligible
            // app, and can also be outdated relative to the local cleanup rules.
            val holders = probe.enabledItems.ifEmpty { apiItems }
            return if (holders.isNotEmpty()) {
                val visibleHolders = EffectivePreviewPolicy.visible(
                    kind, holders, selected, globalMode, overrides,
                )
                PreviewV2Result(visibleHolders,
                    if (visibleHolders == holders) PreviewV2Source.ROLE_HOLDER
                    else PreviewV2Source.ROLE_POLICY_PROJECTED)
            } else unavailable()
        }

        // Pending rules are only a simulation, and must not pretend the OS has applied
        // them. Use exact current request candidates (or a matched manager inventory).
        if (pendingDraft) {
            val candidates = (probe.diagnosticCandidates.takeIf { it.isNotEmpty() }
                ?: apiItems).distinctBy(SystemProbeItem::component)
            return projected(candidates, PreviewV2Source.DRAFT_SIMULATION)
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
            return projected(toItems(requireNotNull(ordered), apiItems),
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
            return projected(toItems(returned, apiItems),
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
            return projected(roots + apps, PreviewV2Source.DOCUMENT_ROOTS)
        }

        if (probe.evidence == ProbeEvidence.ENABLED) {
            return projected(probe.enabledItems.ifEmpty { apiItems }
                .distinctBy(SystemProbeItem::component), PreviewV2Source.ENABLED)
        }

        // Candidate lists may still differ from the actual Resolver/Chooser UI.
        // This distinction is visible and is NEVER called a verified screen.
        if (adapter in setOf(
                PreviewAdapter.RESOLVER, PreviewAdapter.SYSTEM_SERVICES,
                PreviewAdapter.PROVIDER_REGISTRY,
            ) || (adapter == PreviewAdapter.DOCUMENTS_UI &&
                kind != IntentKind.DOCUMENT_ROOT && probe.diagnosticCandidates.isNotEmpty())
        ) return projected(
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

    private fun unavailable() = PreviewV2Result(emptyList(), PreviewV2Source.UNAVAILABLE)
}
