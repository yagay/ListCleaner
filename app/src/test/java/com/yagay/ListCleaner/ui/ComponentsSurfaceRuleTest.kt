package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
import org.junit.Assert.*
import org.junit.Test

class ComponentsSurfaceRuleTest {
    private val app = ManagedComponentRule(
        ManagedComponentRuleType.NOTIFICATION_APP, "com.example.chat", "com.example.chat",
        "Entire app", "com.example.chat")
    private val channel = ManagedComponentRule(
        ManagedComponentRuleType.CHANNEL, "com.example.chat", "important",
        "Important", "important")
    private val settings = ManagedComponentRule(
        ManagedComponentRuleType.SETTINGS_APP, "com.example.chat", "com.example.chat",
        "Settings app", "com.example.chat")
    private val legacy = ManagedComponentRule(
        ManagedComponentRuleType.LEGACY_CATEGORY, "__legacy__", "promo",
        "Promotion", "promo")

    @Test fun appAndChannelSelectionsRemainIndependent() {
        val initial = SurfaceFilterConfig()
        val hidden = applyManagedComponentDraft(initial, listOf(app, channel, settings, legacy),
            mapOf(app.id to true, channel.id to true, settings.id to true, legacy.id to true))
        assertTrue(managedRuleHidden(hidden, app))
        assertTrue(managedRuleHidden(hidden, channel))
        assertTrue(managedRuleHidden(hidden, settings))
        assertTrue(managedRuleHidden(hidden, legacy))
        val restoredApp = applyManagedComponentDraft(hidden, listOf(app), mapOf(app.id to false))
        assertFalse(managedRuleHidden(restoredApp, app))
        assertTrue(managedRuleHidden(restoredApp, channel))
        assertTrue(managedRuleHidden(restoredApp, settings))
        assertTrue(managedRuleHidden(restoredApp, legacy))
    }

    @Test fun lockedRowsCannotBeChangedByBulkOrApply() {
        val locked = withManagedRuleLocked(
            withManagedRuleLocked(SurfaceFilterConfig(), app, true), channel, true)
        val result = applyManagedComponentDraft(locked, listOf(app, channel),
            mapOf(app.id to true, channel.id to true))
        assertEquals(locked, result)
        assertFalse(managedRuleHidden(result, app))
        assertFalse(managedRuleHidden(result, channel))
    }

    @Test fun switchingOffAnOldCategoryDoesNotTouchChannelsOrSettings() {
        val before = SurfaceFilterConfig()
            .withNotificationCategoryHidden("promo", true)
            .withChannelHidden("com.example.chat", "important", true)
            .withSelected(SurfaceKind.SETTINGS_APPS, setOf("com.example.chat"))
        val after = applyManagedComponentDraft(before, listOf(legacy), mapOf(legacy.id to false))
        assertFalse("promo" in after.hiddenNotificationCategories)
        assertTrue(managedRuleHidden(after, channel))
        assertTrue(managedRuleHidden(after, settings))
    }

    @Test fun ruleIdentityIsUniqueAcrossAppChannelAndSettingsScopes() {
        assertEquals(4, listOf(app, channel, settings, legacy).map { it.id }.toSet().size)
    }
}
