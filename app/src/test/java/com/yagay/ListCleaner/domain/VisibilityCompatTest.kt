package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class VisibilityCompatTest {
    private fun candidate(kind: IntentKind, pkg: String, cls: String) =
        ComponentCandidate(ComponentRule(kind, pkg, "$pkg.$cls"), pkg, cls)

    @Test fun partialAppSelectionNeverBecomesVisibilityTarget() {
        val a = candidate(IntentKind.SHARE, "com.example.a", "ShareOne")
        val b = candidate(IntentKind.SHARE, "com.example.a", "ShareTwo")
        val other = candidate(IntentKind.SHARE, "com.example.b", "Share")

        val partial = deriveFullySelectedPackages(listOf(a, b, other), setOf(a.rule, other.rule))
        assertFalse("half-selected app must not be hidden package-wide",
            "com.example.a" in partial[VisibilityScope.SHARE].orEmpty())
        assertTrue("fully selected one-component app should qualify",
            "com.example.b" in partial[VisibilityScope.SHARE].orEmpty())

        val full = deriveFullySelectedPackages(listOf(a, b, other), setOf(a.rule, b.rule, other.rule))
        assertTrue("com.example.a" in full[VisibilityScope.SHARE].orEmpty())
    }

    @Test fun selectedScopesAreUnionAndDefaultIsDisabled() {
        val packages = mapOf(
            VisibilityScope.SHARE to setOf("com.share"),
            VisibilityScope.BROWSER to setOf("com.browser"),
            VisibilityScope.OPEN to setOf("com.open")
        )
        assertTrue(VisibilityCompatConfig(fullPackages = packages).activePackages().isEmpty())
        assertEquals(
            setOf("com.share", "com.browser"),
            VisibilityCompatConfig(setOf(VisibilityScope.SHARE, VisibilityScope.BROWSER), packages).activePackages()
        )
        assertEquals(
            setOf("com.share", "com.browser", "com.open"),
            VisibilityCompatConfig(setOf(VisibilityScope.ALL), packages).activePackages()
        )
    }

    @Test fun unavailableOrRestrictedCandidatesDoNotCreateHalfState() {
        val visible = candidate(IntentKind.BROWSER, "com.example", "Browser")
        val unavailable = candidate(IntentKind.BROWSER, "com.example", "OldBrowser").copy(unavailable = true)
        val restricted = candidate(IntentKind.BROWSER, "com.example", "PrivateBrowser").copy(restricted = true)
        val derived = deriveFullySelectedPackages(listOf(visible, unavailable, restricted), setOf(visible.rule))
        assertEquals(setOf("com.example"), derived[VisibilityScope.BROWSER])
    }
}
