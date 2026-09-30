package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorityCandidatePolicyTest {
    @Test fun rawVpnAppOpsCandidateIsNotTreatedAsFinalSettingsAuthority() {
        val item = candidate(
            IntentKind.VPN,
            "pkg.vpn",
            "@vpn",
            listOf("VPN_APP_OPS mode=allow package_level=true"),
        )
        assertTrue(AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single().unavailable)
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

    @Test fun inputMethodManagerResultIsAlreadyAuthoritative() {
        val item = candidate(
            IntentKind.INPUT_METHOD,
            "pkg.ime",
            "pkg.ime.Service",
            listOf("INPUT_METHOD_MANAGER id=pkg.ime/.Service"),
        )
        assertFalse(AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single().unavailable)
    }

    @Test fun packageManagerAuthoritativeKindsRemainUntouched() {
        val item = candidate(
            IntentKind.NOTIFICATION_LISTENER,
            "pkg.notify",
            "pkg.notify.Listener",
            listOf("SERVICE_ENTRY action=android.service.notification.NotificationListenerService"),
        )
        assertFalse(AuthorityCandidatePolicy.normalize(listOf(item), 1_000L).single().unavailable)
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
