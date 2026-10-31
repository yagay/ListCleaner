package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPreviewProjectionTest {
    private val kind = IntentKind.OPEN
    private val a = ComponentRule(kind, "com.test.a", "com.test.a.Main")
    private val b = ComponentRule(kind, "com.test.b", "com.test.b.Main")
    private val candidates = listOf(
        ComponentCandidate(a, "A", "Activity A"),
        ComponentCandidate(b, "B", "Activity B"),
    )
    private fun estimate(
        saved: Set<ComponentRule>,
        mode: DisplayMode = DisplayMode.HIDE_SELECTED,
        overrides: Map<IntentKind, DisplayMode> = emptyMap(),
    ) = SystemPreviewProjection.estimate(kind, candidates, saved, mode, overrides, null)!!

    @Test fun hidingOneSelectionShowsOnlyTheOther() {
        assertEquals(listOf("com.test.b"), estimate(setOf(a)).items.map { it.packageName })
        assertEquals(PreviewListSource.ESTIMATED, estimate(setOf(a)).source)
    }
    @Test fun showSelectedUsesConfirmedRules() {
        assertEquals(listOf("com.test.a"),
            estimate(setOf(a), DisplayMode.SHOW_SELECTED).items.map { it.packageName })
    }
    @Test fun noSelectedRulesFailOpen() {
        assertEquals(2, estimate(emptySet(), DisplayMode.SHOW_SELECTED).items.size)
    }
    @Test fun perCategoryOverrideWins() {
        assertEquals(2, estimate(setOf(a), DisplayMode.HIDE_SELECTED,
            mapOf(kind to DisplayMode.SHOW_ALL)).items.size)
    }
    @Test fun noCandidatesDoesNotInventAny() {
        assertNull(SystemPreviewProjection.estimate(kind, emptyList(), setOf(a),
            DisplayMode.HIDE_SELECTED, emptyMap(), null))
    }
    @Test fun otherKindsDoNotSelectOpen() {
        val other = ComponentRule(IntentKind.SHARE, "com.test.a", "com.test.a.Main")
        assertEquals(2, estimate(setOf(other)).items.size)
    }
    @Test fun estimatedUsesScenarioApiOrderInsteadOfAlphabetizedCatalog() {
        val queried = SystemProbeResult(
            sourceRes = 0, explanationRes = 0,
            items = listOf(
                SystemProbeItem("B", "com.test.b", "com.test.b.Main"),
                SystemProbeItem("A", "com.test.a", "com.test.a.Main"),
            ),
        )
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), queried,
        )!!
        assertEquals(listOf("com.test.b", "com.test.a"),
            result.items.map { it.packageName })
    }

    @Test fun estimatedGenericPriorityMatchesStableRuntimePromotion() {
        val queried = SystemProbeResult(
            0, 0, items = listOf(
                SystemProbeItem("B", "com.test.b", "com.test.b.Main"),
                SystemProbeItem("A", "com.test.a", "com.test.a.Main"),
            ),
        )
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), queried,
            priorities = PriorityConfig(apps = mapOf(kind to listOf("com.test.a"))),
        )!!
        assertEquals(listOf("com.test.a", "com.test.b"),
            result.items.map { it.packageName })
    }

    @Test fun typedPdfPriorityOverridesGenericAndScenarioTextUsesGeneric() {
        val scanned = listOf(candidates[1], candidates[0])
        val generic = PriorityConfig(apps = mapOf(kind to listOf("com.test.a")))
        val typed = OpenTypeConfig(priorities = mapOf(OpenPreset.PDF to listOf("com.test.b")))
        fun preview(scenario: SystemProbeScenario) = SystemPreviewProjection.estimate(
            kind, scanned, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), null,
            priorities = generic, openTypes = typed, scenario = scenario,
        )!!.items.map { it.packageName }
        assertEquals(listOf("com.test.b", "com.test.a"), preview(SystemProbeScenario.PDF))
        assertEquals(listOf("com.test.a", "com.test.b"), preview(SystemProbeScenario.TEXT))
    }

    @Test fun deepLinkHostPriorityOnlyAppliesForMatchingRequestHost() {
        val deep = IntentKind.DEEP_LINK
        val hostCandidates = listOf(
            ComponentCandidate(ComponentRule(deep, "com.test.a", "a.Main"), "A", "A"),
            ComponentCandidate(ComponentRule(deep, "com.test.b", "b.Main"), "B", "B"),
        )
        val config = BrowserLinkConfig(
            hosts = setOf("example.com"),
            priorities = mapOf("example.com" to listOf("com.test.b")),
        )
        fun preview(host: String?) = SystemPreviewProjection.estimate(
            deep, hostCandidates, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), null,
            browserLinks = config, requestHost = host,
        )!!.items.map { it.packageName }
        assertEquals(listOf("com.test.b", "com.test.a"), preview("example.com"))
        assertEquals(listOf("com.test.a", "com.test.b"), preview("unrelated.example"))
    }

    @Test fun regularPreviewPreservesVerifiedHookOrder() {
        val hooked = PreviewListResult(
            listOf(SystemProbeItem("B", "com.test.b", "b.Main"),
                SystemProbeItem("A", "com.test.a", "a.Main")),
            PreviewListSource.HOOK_OBSERVATION,
        )
        val projected = PreviewListResult(
            listOf(hooked.items[1], hooked.items[0]), PreviewListSource.ESTIMATED,
        )
        assertEquals(hooked, preferredPreviewResult(PreviewListMode.CURRENT, hooked, projected))
    }

    @Test fun regularPreviewAutomaticallyUsesEstimateWhenOnlyApiCandidatesAreAvailable() {
        val api = PreviewListResult(
            listOf(SystemProbeItem("A", "com.test.a", "a.Main")),
            PreviewListSource.API_RETURN,
        )
        val estimated = PreviewListResult(
            listOf(SystemProbeItem("B", "com.test.b", "b.Main")),
            PreviewListSource.ESTIMATED,
        )
        assertEquals(estimated, preferredPreviewResult(PreviewListMode.CURRENT, api, estimated))
        assertEquals(api, preferredPreviewResult(PreviewListMode.CANDIDATES, api, estimated))
        assertEquals(api, preferredPreviewResult(PreviewListMode.CURRENT, api, null))
    }

}
