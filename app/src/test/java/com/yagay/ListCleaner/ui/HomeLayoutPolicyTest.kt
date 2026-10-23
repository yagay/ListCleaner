package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test fun categoryCardsKeepTheGroupedLandingPage() {
        assertTrue(HomeLayoutPolicy.showCategoryHome(
            UiHomeLayout.CATEGORY_CARDS, null, "", false))
        assertFalse(HomeLayoutPolicy.showCategoryHome(
            UiHomeLayout.CATEGORY_CARDS, IntentKind.SHARE, "", false))
        assertFalse(HomeLayoutPolicy.showCategoryHome(
            UiHomeLayout.CATEGORY_CARDS, null, "chat", false))
        assertFalse(HomeLayoutPolicy.showCategoryHome(
            UiHomeLayout.CATEGORY_CARDS, null, "", true))
        assertFalse(HomeLayoutPolicy.showCategoryFilterBar(UiHomeLayout.CATEGORY_CARDS))
    }

    @Test fun classicAlwaysStartsFromTheExistingListEditor() {
        assertFalse(HomeLayoutPolicy.showCategoryHome(
            UiHomeLayout.CLASSIC_LIST, null, "", false))
        assertTrue(HomeLayoutPolicy.showCategoryFilterBar(UiHomeLayout.CLASSIC_LIST))
        assertTrue(HomeLayoutPolicy.startWithComponentList(
            UiHomeLayout.CLASSIC_LIST, UiComponentStart.CATEGORIES))
    }

    @Test fun sortingUsesTheSameVerticalClassicListChoiceAsCleanup() {
        assertTrue(HomeLayoutPolicy.showPriorityCategoryHome(UiHomeLayout.CATEGORY_CARDS, null, ""))
        assertFalse(HomeLayoutPolicy.showPriorityCategoryHome(UiHomeLayout.CLASSIC_LIST, null, ""))
        assertFalse(HomeLayoutPolicy.showPriorityCategoryHome(UiHomeLayout.CATEGORY_CARDS, IntentKind.SHARE, ""))
        assertFalse(HomeLayoutPolicy.showPriorityCategoryHome(UiHomeLayout.CATEGORY_CARDS, null, "search"))
        assertEquals(IntentKind.SHARE, HomeLayoutPolicy.priorityKind(null))
    }

    @Test fun sortingCategoryFiltersAreRestrictedToEditableKinds() {
        assertTrue(IntentKind.SHARE in HomeLayoutPolicy.sortableKinds)
        assertTrue(IntentKind.OPEN in HomeLayoutPolicy.sortableKinds)
        assertFalse(IntentKind.PHOTO_SOURCE in HomeLayoutPolicy.sortableKinds)
        assertFalse(IntentKind.NOTIFICATION_LISTENER in HomeLayoutPolicy.sortableKinds)
        assertEquals(IntentKind.SHARE, HomeLayoutPolicy.priorityKind(IntentKind.PHOTO_SOURCE))
        assertEquals(IntentKind.OPEN, HomeLayoutPolicy.priorityKind(IntentKind.OPEN))
    }

    @Test fun currentModeDoesNotChangeExistingComponentPreference() {
        assertFalse(HomeLayoutPolicy.startWithComponentList(
            UiHomeLayout.CATEGORY_CARDS, UiComponentStart.CATEGORIES))
        assertTrue(HomeLayoutPolicy.startWithComponentList(
            UiHomeLayout.CATEGORY_CARDS, UiComponentStart.ALL_COMPONENTS))
    }

    @Test fun layoutDefaultsPreservedAndCopyDoesNotResetAnyOtherSettings() {
        val current = UiPreferences()
        assertEquals(UiHomeLayout.CATEGORY_CARDS, current.homeLayout)
        val changed = current.copy(homeLayout = UiHomeLayout.CLASSIC_LIST).normalized()
        assertEquals(UiHomeLayout.CLASSIC_LIST, changed.homeLayout)
        assertEquals(current.accent, changed.accent)
        assertEquals(current.appSort, changed.appSort)
        assertEquals(current.proactiveScanMode, changed.proactiveScanMode)
        assertEquals(current.defaultFilter, changed.defaultFilter)
    }
}
