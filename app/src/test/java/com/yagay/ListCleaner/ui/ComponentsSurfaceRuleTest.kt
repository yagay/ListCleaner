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

    @Test fun sortingUsesSavedPartialAndFullSelectionNotDraft() {
        val initial = SurfaceFilterConfig()
        val partial = initial.withChannelHidden("com.example.chat", "important", true)
        val full = partial.withSelected(SurfaceKind.NOTIFICATION_SHADE, setOf("com.example.chat"))
        assertEquals(2, managedGroupSelectionRank(listOf(app, channel), initial))
        assertEquals(1, managedGroupSelectionRank(listOf(app, channel), partial))
        assertEquals(0, managedGroupSelectionRank(listOf(app, channel), full))
        assertEquals(0, managedGroupSelectionRank(listOf(settings),
            initial.withSelected(SurfaceKind.SETTINGS_APPS, setOf("com.example.chat"))))
        // Merely staging a new checkbox would not change the saved config.
        assertEquals(2, managedGroupSelectionRank(listOf(app, channel), initial))
    }

    @Test fun settingsAppsCommitMovesSelectedFirstWithoutOverwritingPriorityOrder() {
        val a = ManagedComponentRule(ManagedComponentRuleType.SETTINGS_APP,
            "com.example.alpha", "com.example.alpha", "Alpha", "Alpha")
        val z = ManagedComponentRule(ManagedComponentRuleType.SETTINGS_APP,
            "com.example.zulu", "com.example.zulu", "Zulu", "Zulu")
        val rows = listOf(a.packageName to listOf(a), z.packageName to listOf(z))
        val labels = mapOf(a.packageName to "Alpha", z.packageName to "Zulu")
        // Priority belongs to the separate ordering page, and must never
        // suppress a confirmed cleanup selection on the Settings Apps page.
        val before = SurfaceFilterConfig().withPriorities(
            SurfaceKind.SETTINGS_APPS, listOf(a.packageName, z.packageName))
        assertEquals(listOf(a.packageName, z.packageName),
            sortManagedSurfaceAppGroups(rows, labels, before, UiAppSort.SELECTED_FIRST)
                .map { it.first })
        val committed = applyManagedComponentDraft(before, listOf(a, z), mapOf(z.id to true))
        assertEquals(listOf(z.packageName, a.packageName),
            sortManagedSurfaceAppGroups(rows, labels, committed, UiAppSort.SELECTED_FIRST)
                .map { it.first })
        assertEquals(before.priorities(SurfaceKind.SETTINGS_APPS),
            committed.priorities(SurfaceKind.SETTINGS_APPS))
        // No draft is passed to the sorter; checkbox staging alone cannot
        // change the list order before the Apply operation commits the rule.
        assertEquals(listOf(a.packageName, z.packageName),
            sortManagedSurfaceAppGroups(rows, labels, before, UiAppSort.SELECTED_FIRST)
                .map { it.first })
        assertEquals(listOf(a.packageName, z.packageName),
            sortManagedSurfaceAppGroups(rows, labels, committed, UiAppSort.NAME)
                .map { it.first })
    }

    @Test fun managedSurfaceSelectionSortsFullPartialAndUnselectedOnlyAfterCommit() {
        val pkgA = "com.example.alpha"
        val pkgB = "com.example.beta"
        val pkgC = "com.example.charlie"
        val appA = app.copy(packageName = pkgA, key = pkgA)
        val chA = channel.copy(packageName = pkgA)
        val appB = app.copy(packageName = pkgB, key = pkgB)
        val chB = channel.copy(packageName = pkgB)
        val appC = app.copy(packageName = pkgC, key = pkgC)
        val rows = listOf(pkgA to listOf(appA, chA),
            pkgB to listOf(appB, chB), pkgC to listOf(appC, chB.copy(packageName = pkgC)))
        val labels = mapOf(pkgA to "Alpha", pkgB to "Beta", pkgC to "Charlie")
        val before = SurfaceFilterConfig()
        val after = before.withChannelHidden(pkgB, chB.key, true)
            .withSelected(SurfaceKind.NOTIFICATION_SHADE, setOf(pkgC))
        assertEquals(listOf(pkgA, pkgB, pkgC),
            sortManagedSurfaceAppGroups(rows, labels, before, UiAppSort.SELECTED_FIRST).map { it.first })
        assertEquals(listOf(pkgC, pkgB, pkgA),
            sortManagedSurfaceAppGroups(rows, labels, after, UiAppSort.SELECTED_FIRST).map { it.first })
    }

    @Test fun ruleIdentityIsUniqueAcrossAppChannelAndSettingsScopes() {
        assertEquals(4, listOf(app, channel, settings, legacy).map { it.id }.toSet().size)
    }
}
