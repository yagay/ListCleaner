package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDirectShareRuleCleanupTest {
    private val share = ComponentRule(
        IntentKind.DIRECT_SHARE, "com.example.chat",
        "com.example.chat.Main#direct#0123456789abcdef01234567")
    private val open = ComponentRule(IntentKind.OPEN, "com.example.viewer", "com.example.viewer.View")
    private val otherShare = ComponentRule(IntentKind.SHARE, "com.example.chat", "com.example.chat.Send")

    private fun priorities() = PriorityConfig(
        apps = mapOf(
            IntentKind.DIRECT_SHARE to listOf("com.example.chat"),
            IntentKind.OPEN to listOf("com.example.viewer")
        ),
        titles = mapOf(share.id to "Old target", open.id to "Keep title")
    )

    @Test fun removesOnlyOldDirectShareSettingsAcrossAllSpaces() {
        val typed = DirectShareTypeConfig(mapOf(DirectShareType.IMAGE to setOf(share.id)))
        val work = UserSpaceRules(
            rules = setOf(share, otherShare), priorities = priorities(),
            directShareTypes = typed, mode = DisplayMode.SHOW_SELECTED
        )
        val config = ModuleConfig(
            rules = setOf(share, open, otherShare),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = priorities(),
            diagnostic = false,
            directShareTypes = typed,
            userSpaces = mapOf(10 to work)
        )
        val cleaned = LegacyDirectShareRuleCleanup.remove(config).validated()
        assertEquals(setOf(open, otherShare), cleaned.rules)
        assertEquals(setOf(otherShare), cleaned.userSpaces.getValue(10).rules)
        assertEquals(DisplayMode.SHOW_SELECTED, cleaned.userSpaces.getValue(10).mode)
        assertTrue(cleaned.directShareTypes.rules.isEmpty())
        assertTrue(cleaned.userSpaces.getValue(10).directShareTypes.rules.isEmpty())
        assertFalse(IntentKind.DIRECT_SHARE in cleaned.priorities.apps)
        assertFalse(IntentKind.DIRECT_SHARE in cleaned.userSpaces.getValue(10).priorities.apps)
        assertEquals(listOf("com.example.viewer"), cleaned.priorities.apps[IntentKind.OPEN])
        assertEquals("Keep title", cleaned.priorities.titles[open.id])
        assertFalse(share.id in cleaned.priorities.titles)
    }

    @Test fun migrationIsIdempotentButDoesNotBlockLaterNewRules() {
        val old = ModuleConfig(
            rules = setOf(share, open), mode = DisplayMode.HIDE_SELECTED,
            priorities = priorities(), diagnostic = false
        )
        val once = LegacyDirectShareRuleCleanup.remove(old)
        assertEquals(once, LegacyDirectShareRuleCleanup.remove(once))
        val laterSelection = once.copy(rules = once.rules + share).validated()
        assertTrue(share in laterSelection.rules)
        assertEquals(setOf(open, share), laterSelection.rules)
    }
}
