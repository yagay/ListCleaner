package com.yagay.ListCleaner

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.ui.availableDeepLinkHosts
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkHostVisibilityTest {
    private fun candidate(
        kind: IntentKind,
        host: String,
        unavailable: Boolean = false,
        restricted: Boolean = false
    ) = ComponentCandidate(
        rule = ComponentRule(kind, "com.example.$host", "com.example.Target"),
        appLabel = "Example",
        activityLabel = "Target",
        browserHosts = setOf(host),
        unavailable = unavailable,
        restricted = restricted
    )

    @Test fun automaticDomainsNeedAnActualDeepLinkCandidate() {
        val result = availableDeepLinkHosts(
            configuredHosts = emptySet(),
            discoveredHosts = setOf("github.com", "empty.example", "browser.example"),
            candidates = listOf(
                candidate(IntentKind.DEEP_LINK, "github.com"),
                candidate(IntentKind.BROWSER, "browser.example")
            )
        )

        assertEquals(setOf("github.com"), result)
    }

    @Test fun configuredDomainsRemainManageableWithoutMatchingApps() {
        val result = availableDeepLinkHosts(
            configuredHosts = setOf("manual.example"),
            discoveredHosts = setOf("missing.example"),
            candidates = emptyList()
        )

        assertEquals(setOf("manual.example"), result)
    }

    @Test fun unavailableOrRestrictedCandidatesDoNotExposeAutomaticDomains() {
        val result = availableDeepLinkHosts(
            configuredHosts = emptySet(),
            discoveredHosts = setOf("old.example", "private.example"),
            candidates = listOf(
                candidate(IntentKind.DEEP_LINK, "old.example", unavailable = true),
                candidate(IntentKind.DEEP_LINK, "private.example", restricted = true)
            )
        )

        assertEquals(emptySet<String>(), result)
    }
}
