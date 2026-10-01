package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorityCandidatePolicyTest {
    @Test fun rawVpnAppOpsCandidateRemainsVisibleAsProvisional() {
        val item = candidate(
            IntentKind.VPN,
            "pkg.vpn",
            "@vpn",
            listOf("VPN_APP_OPS mode=allow package_level=true"),
        )
        val normalized = AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single()
        assertFalse(normalized.unavailable)
        assertTrue(normalized.evidence.any { "provisional=true" in it })
    }

    @Test fun recentObservedAuthoritySnapshotBecomesLive() {
        val now = 50_000L
        val item = candidate(
            IntentKind.VPN,
            "pkg.vpn",
            "@vpn",
            listOf(
                "AUTHORITY_OBSERVED kind=VPN source=cache_history",
                "observedAt=${now - 1_000L}",
            ),
            unavailable = true,
        )
        assertFalse(AuthorityCandidatePolicy.normalize(listOf(item), now).single().unavailable)
    }

    @Test fun staleObservedAuthoritySnapshotStaysHistorical() {
        val now = AuthorityCandidatePolicy.SNAPSHOT_FRESH_MILLIS + 10_000L
        val item = candidate(
            IntentKind.ASSISTANT,
            "pkg.assistant",
            "@assistant",
            listOf(
                "AUTHORITY_OBSERVED kind=ASSISTANT source=cache_history",
                "observedAt=1",
            ),
            unavailable = true,
        )
        assertTrue(AuthorityCandidatePolicy.normalize(listOf(item), now).single().unavailable)
    }

    @Test fun activeScanKindsRemainUntouched() {
        listOf(IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY, IntentKind.NOTIFICATION_LISTENER).forEach { kind ->
            val item = candidate(
                kind,
                "pkg.$kind",
                "pkg.$kind.Service",
                listOf("ACTIVE_DISCOVERY"),
            )
            val normalized = AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single()
            assertFalse("$kind should remain visible", normalized.unavailable)
            assertFalse(normalized.evidence.any { "provisional=true" in it })
        }
    }

    @Test fun everyAuthorityUpgradeKindKeepsActiveFallbackVisible() {
        ENTRY_SURFACE_DEFINITIONS.values
            .filter { it.availabilityMode == EntryAvailabilityMode.AUTHORITY_UPGRADE }
            .forEach { definition ->
                val item = candidate(
                    definition.kind,
                    "pkg.${definition.kind.name.lowercase()}",
                    "pkg.${definition.kind.name.lowercase()}.Entry",
                    listOf("ACTIVE_FALLBACK"),
                )
                val normalized = AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single()
                assertFalse("${definition.kind} fallback must remain visible", normalized.unavailable)
                assertTrue(
                    "${definition.kind} must be marked provisional",
                    normalized.evidence.any { "provisional=true" in it },
                )
            }
    }

    private fun candidate(
        kind: IntentKind,
        packageName: String,
        className: String,
        evidence: List<String>,
        unavailable: Boolean = false,
    ) = ComponentCandidate(
        rule = ComponentRule(kind, packageName, className),
        appLabel = packageName,
        activityLabel = className,
        evidence = evidence,
        unavailable = unavailable,
    )
}
