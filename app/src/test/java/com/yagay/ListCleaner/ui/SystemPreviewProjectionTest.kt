package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
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
}
