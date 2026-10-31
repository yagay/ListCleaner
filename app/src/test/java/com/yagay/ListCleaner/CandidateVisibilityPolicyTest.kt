package com.yagay.ListCleaner

import com.yagay.ListCleaner.domain.AuthorityCandidatePolicy
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.ENTRY_SURFACE_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryAvailabilityMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.ui.UiPreferences
import com.yagay.ListCleaner.ui.UiCandidateSource
import com.yagay.ListCleaner.ui.CandidateVisibilityPolicy
import com.yagay.ListCleaner.ui.mayBeDynamicEntry
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
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
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

    @Test fun historicalDirectShareIsHiddenFromNormalListButPreserved() {
        val historical = ComponentCandidate(
            rule = ComponentRule(IntentKind.DIRECT_SHARE, "com.example.chat", "@direct#a"),
            appLabel = "Chat", activityLabel = "Previous conversation",
            unavailable = true,
            evidence = listOf("AUTHORITY_OBSERVED kind=DIRECT_SHARE source=cache_history"),
        )
        assertTrue(historical.unavailable)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(historical, selected = false))
        assertFalse(CandidateVisibilityPolicy.visible(historical, selected = false, uiFilter = UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(historical, selected = false, uiFilter = UiFilter.HIDE_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(historical, selected = false, uiFilter = UiFilter.ALL,
            UiPreferences(candidateSource = UiCandidateSource.HISTORY)))
        assertFalse(CandidateVisibilityPolicy.visible(historical, selected = false, uiFilter = UiFilter.SHOW_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(historical, selected = false, uiFilter = UiFilter.LOCKED))
    }

    @Test fun historicalShareSettingsFilterWithoutDeletingSelectedRules() {
        val historical = ComponentCandidate(
            rule = ComponentRule(IntentKind.DIRECT_SHARE, "com.example.chat", "com.example.chat.Past"),
            appLabel = "Chat", activityLabel = "Past",
            evidence = listOf("AUTHORITY_OBSERVED kind=DIRECT_SHARE source=cache_history", "observedAt=1000000"),
            unavailable = true,
        )
        val oldTime = 1_000_000L + 40L * 86_400_000L
        assertFalse(CandidateVisibilityPolicy.visible(historical, false, UiFilter.ALL,
            UiPreferences(historyVisibilityDays = 0), oldTime))
        assertFalse(CandidateVisibilityPolicy.visible(historical, false, UiFilter.ALL,
            UiPreferences(historyVisibilityDays = 30), oldTime))
        assertFalse(CandidateVisibilityPolicy.visible(historical, true, UiFilter.ALL,
            UiPreferences(showShortcutHistory = false), oldTime))
        assertTrue(CandidateVisibilityPolicy.visible(historical, true, UiFilter.SHOW_SELECTED,
            UiPreferences(showShortcutHistory = false), oldTime))
        assertFalse(CandidateVisibilityPolicy.visible(historical, false, UiFilter.ALL,
            UiPreferences(showShortcutHistory = false), oldTime))
        assertTrue(CandidateVisibilityPolicy.visible(historical, false, UiFilter.ALL,
            UiPreferences(candidateSource = UiCandidateSource.HISTORY), oldTime))
        assertFalse(CandidateVisibilityPolicy.visible(historical, false, UiFilter.ALL,
            UiPreferences(candidateSource = UiCandidateSource.PROACTIVE), oldTime))
    }

    @Test fun proactiveAndLiveSourceFilteringKeepsSignalsSeparate() {
        val proactive = ComponentCandidate(
            rule = ComponentRule(IntentKind.DIRECT_SHARE, "com.example.chat", "com.example.chat.New"),
            appLabel = "Chat", activityLabel = "New",
            evidence = listOf("ACTIVE_SHORTCUT_CANDIDATE source=ROOT_SHELL userId=0"),
        )
        assertTrue(CandidateVisibilityPolicy.visible(proactive, false, UiFilter.ALL,
            UiPreferences(candidateSource = UiCandidateSource.PROACTIVE)))
        assertFalse(CandidateVisibilityPolicy.visible(proactive, false, UiFilter.ALL,
            UiPreferences(candidateSource = UiCandidateSource.OBSERVED)))
    }

    @Test fun selectedUnavailableCandidateKeepsRuleButIsAbsentFromNormalList() {
        val item = candidate(unavailable = true)
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = true))
        assertFalse(CandidateVisibilityPolicy.retainAfterRefresh(item, selected = false))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.ALL))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.SHOW_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.LOCKED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = true, uiFilter = UiFilter.HIDE_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(item, selected = false, uiFilter = UiFilter.ALL))
    }

    @Test fun unavailableDirectShareKeepsSavedRuleAndShowsOnlyInSelectedMaintenance() {
        val stale = ComponentCandidate(
            rule = ComponentRule(IntentKind.DIRECT_SHARE, "com.example.chat",
                "com.example.chat.Entry#direct#123456789012345678901234"),
            appLabel = "Chat", activityLabel = "Old conversation",
            unavailable = true,
            evidence = listOf("Configured but not observed during this scan")
        )
        val rules = setOf(stale.rule)
        val retained = com.yagay.ListCleaner.ui.retainConfiguredCandidates(
            listOf(stale), rules
        )
        assertTrue(retained.any { it.rule in rules && it.unavailable })
        assertFalse(CandidateVisibilityPolicy.visible(stale, true, UiFilter.ALL))
        assertFalse(CandidateVisibilityPolicy.visible(stale, true, UiFilter.HIDE_SELECTED))
        assertTrue(CandidateVisibilityPolicy.visible(stale, true, UiFilter.SHOW_SELECTED))
        assertFalse(CandidateVisibilityPolicy.visible(stale, false, UiFilter.SHOW_SELECTED))
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
    @Test fun unobservedShortcutsAndDirectShareArePotentiallyDynamic() {
        val shortcut = ComponentCandidate(
            ComponentRule(IntentKind.SHORTCUT_ITEM, "com.example.app", "@entry#shortcut#123456789012345678901234"),
            "App", "Quick action", unavailable = true
        )
        val directShare = ComponentCandidate(
            ComponentRule(IntentKind.DIRECT_SHARE, "com.example.app", "com.example.app.ShareTarget"),
            "App", "Recent conversation", unavailable = true
        )
        val ordinaryOpen = ComponentCandidate(
            ComponentRule(IntentKind.OPEN, "com.example.app", "com.example.app.OpenActivity"),
            "App", "Open PDF", unavailable = true
        )
        assertTrue(shortcut.mayBeDynamicEntry())
        assertTrue(directShare.mayBeDynamicEntry())
        assertFalse(ordinaryOpen.mayBeDynamicEntry())
        // Existing cleanup selections are preserved independently of the UI status wording.
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(shortcut, selected = true))
        assertTrue(CandidateVisibilityPolicy.retainAfterRefresh(ordinaryOpen, selected = true))
    }

}
