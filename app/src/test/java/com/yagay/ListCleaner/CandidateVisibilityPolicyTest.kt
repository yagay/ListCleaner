package com.yagay.ListCleaner

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
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
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = false, UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = false, UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.SHOW_SELECTED))
    }

    @Test fun selectedUnavailableCandidateSurvivesRefreshInAll() {
        val item = candidate(unavailable = true)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = true))
        assertFalse(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = false))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.LOCKED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, UiFilter.ALL))
    }

    @Test fun restrictedCandidatesStayIsolatedFromAll() {
        val item = candidate(restricted = true)
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.HIDE_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.LOCKED))
    }

    @Test fun restrictedUnavailableNeverLeaksIntoAll() {
        val item = candidate(unavailable = true, restricted = true)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = true))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, UiFilter.SHOW_SELECTED))
    }
}
