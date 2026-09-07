package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenEffectPreviewTest {
    private val a = ComponentCandidate(ComponentRule(IntentKind.OPEN, "a.app", "a.app.Open"), "A", "Open")
    private val b = ComponentCandidate(ComponentRule(IntentKind.OPEN, "b.app", "b.app.Open"), "B", "Open")
    private val c = ComponentCandidate(ComponentRule(IntentKind.OPEN, "c.app", "c.app.Open"), "C", "Open")

    @Test fun genericAndTypedRulesAreBothShownWithSources() {
        val config = OpenTypeConfig(rules = mapOf(OpenPreset.PDF to setOf(b.rule.id)))
        val preview = previewOpenEffect(
            listOf(a, b, c), setOf(a.rule), DisplayMode.HIDE_SELECTED, PriorityConfig(), config,
            "application/pdf", "content", "book.pdf"
        )
        assertEquals(OpenPreset.PDF, preview.preset)
        assertEquals(1, preview.finalCount)
        assertEquals("全部", preview.items.first { it.candidate.rule == a.rule }.selectedBy)
        assertEquals("PDF", preview.items.first { it.candidate.rule == b.rule }.selectedBy)
        assertFalse(preview.items.first { it.candidate.rule == a.rule }.included)
        assertTrue(preview.items.first { it.candidate.rule == c.rule }.included)
    }

    @Test fun typedPriorityOverridesGenericAndEmptyTypedInheritsGeneric() {
        val inherited = previewOpenEffect(
            listOf(a, b, c), emptySet(), DisplayMode.HIDE_SELECTED,
            PriorityConfig(apps = mapOf(IntentKind.OPEN to listOf("c.app", "a.app"))),
            OpenTypeConfig(), "application/pdf", "content", "book.pdf"
        )
        assertEquals(1, inherited.items.first { it.candidate.rule == c.rule }.rank)

        val explicit = previewOpenEffect(
            listOf(a, b, c), emptySet(), DisplayMode.HIDE_SELECTED,
            PriorityConfig(apps = mapOf(IntentKind.OPEN to listOf("c.app", "a.app"))),
            OpenTypeConfig(priorities = mapOf(OpenPreset.PDF to listOf("b.app"))),
            "application/pdf", "content", "book.pdf"
        )
        assertEquals(1, explicit.items.first { it.candidate.rule == b.rule }.rank)
        assertEquals(null, explicit.items.first { it.candidate.rule == c.rule }.rank)
    }
}
