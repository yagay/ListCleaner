package com.yagay.ListCleaner

import com.yagay.ListCleaner.domain.AuthorityCandidatePolicy
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.ENTRY_SURFACE_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryAvailabilityMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.ui.CandidateVisibilityPolicy
import com.yagay.ListCleaner.ui.UiFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateVisibilityPolicyTest {
    private fun candidate(
        unavailable: Boolean = false,
        restricted: Boolean = false,
    ) = ComponentCandidate(
        rule = ComponentRule(IntentKind.ASSISTANT, "com.example", "com.example.Assistant"),
        appLabel = "Example",
        activityLabel = "Assistant",
        unavailable = unavailable,
        restricted = restricted,
    )

    @Test fun normalCandidatesFollowOnlySelectionFilter() {
        val item = candidate()
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.SHOW_SELECTED))
    }

    @Test fun everyAuthorityUpgradeFallbackSurvivesPolicyAndRemainsVisibleInAll() {
        ENTRY_SURFACE_DEFINITIONS.values
            .filter { it.availabilityMode == EntryAvailabilityMode.AUTHORITY_UPGRADE }
            .forEach { definition ->
                val packageName = "com.example.${definition.kind.name.lowercase()}"
                val raw = ComponentCandidate(
                    rule = ComponentRule(definition.kind, packageName, "$packageName.Entry"),
                    appLabel = definition.kind.name,
                    activityLabel = "Entry",
                    evidence = listOf("ACTIVE_FALLBACK"),
                )
                val reconciled = AuthorityCandidatePolicy.normalize(listOf(raw), nowMillis = 1_000L).single()
                assertFalse("${definition.kind} must not become unavailable", reconciled.unavailable)
                assertTrue(
                    "${definition.kind} provisional candidate must stay visible in ALL",
                    CandidateVisibilityPolicy.visible(reconciled, selected = false, uiFilter = UiFilter.ALL),
                )
            }
    }

    @Test fun selectedUnavailableCandidateSurvivesRefreshInAll() {
        val item = candidate(unavailable = true)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = true))
        assertFalse(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = false))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.LOCKED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.ALL))
    }

    @Test fun restrictedCandidatesStayIsolatedFromAll() {
        val item = candidate(restricted = true)
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.HIDE_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.LOCKED))
    }

    @Test fun restrictedUnavailableNeverLeaksIntoAll() {
        val item = candidate(unavailable = true, restricted = true)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = true))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.SHOW_SELECTED))
    }
}
