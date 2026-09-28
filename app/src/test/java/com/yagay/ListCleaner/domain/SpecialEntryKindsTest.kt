package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialEntryKindsTest {
    @Test
    fun specialEntryRulesRoundTripWithoutPackageVisibilityScope() {
        listOf(IntentKind.LAUNCHER_SHORTCUT, IntentKind.DOCUMENT_PROVIDER).forEach { kind ->
            val rule = ComponentRule(kind, "com.example.app", "com.example.app.Entry")
            assertTrue(rule.isValid())
            assertEquals(rule, ComponentRule.fromId(rule.id))
            assertNull(VisibilityScope.forKind(kind))
        }
    }

    @Test
    fun specialEntryPrioritiesRemainStablePromotions() {
        val candidates = listOf("a", "b", "c", "d")
        val packages = mapOf("a" to "pkg.a", "b" to "pkg.b", "c" to "pkg.c", "d" to "pkg.d")
        val profiles = mapOf("a" to 0, "b" to 0, "c" to 10, "d" to 10)
        val result = prioritizeApps(
            candidates,
            listOf("pkg.b", "pkg.d"),
            { packages.getValue(it) },
            { profiles.getValue(it) },
        )
        assertEquals(listOf("b", "a", "d", "c"), result)
    }

    @Test
    fun displayModeSemanticsApplyToSpecialEntries() {
        assertTrue(DisplayMode.HIDE_SELECTED.includes(selected = false, hasSelection = true))
        assertTrue(DisplayMode.SHOW_SELECTED.includes(selected = true, hasSelection = true))
        assertTrue(DisplayMode.SHOW_ALL.includes(selected = true, hasSelection = true))
    }
}
