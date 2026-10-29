package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test fun legacyNavigationModesBothMigrateToOneList() {
        val old = UiPreferences(homeLayout = UiHomeLayout.CATEGORY_CARDS)
        val direct = UiPreferences(homeLayout = UiHomeLayout.CLASSIC_LIST)
        assertEquals(UiHomeLayout.CLASSIC_LIST, old.normalized().homeLayout)
        assertEquals(direct.normalized().homeLayout, old.normalized().homeLayout)
        assertEquals(UiListStyle.ORIGINAL, UiPreferences(listStyle = UiListStyle.GROUPED).normalized().listStyle)
    }

    @Test fun sortingOffersOnlyEditableCategories() {
        assertTrue(IntentKind.SHARE in HomeLayoutPolicy.sortableKinds)
        assertTrue(IntentKind.OPEN in HomeLayoutPolicy.sortableKinds)
        assertFalse(IntentKind.PHOTO_SOURCE in HomeLayoutPolicy.sortableKinds)
        assertFalse(IntentKind.NOTIFICATION_LISTENER in HomeLayoutPolicy.sortableKinds)
        assertEquals(IntentKind.SHARE, HomeLayoutPolicy.priorityKind(null))
        assertEquals(null, HomeLayoutPolicy.priorityKind(IntentKind.PHOTO_SOURCE))
        assertFalse(IntentKind.DOCUMENT_ROOT in HomeLayoutPolicy.sortableKinds)
        assertTrue(IntentKind.DIRECT_SHARE in HomeLayoutPolicy.sortableKinds)
        assertEquals(IntentKind.OPEN, HomeLayoutPolicy.priorityKind(IntentKind.OPEN))
    }

    @Test fun mergingDisplayModesPreservesOtherPreferences() {
        val old = UiPreferences(homeLayout = UiHomeLayout.CATEGORY_CARDS)
        val migrated = old.normalized()
        assertEquals(old.accent, migrated.accent)
        assertEquals(old.appSort, migrated.appSort)
        assertEquals(old.proactiveScanMode, migrated.proactiveScanMode)
        assertEquals(old.defaultFilter, migrated.defaultFilter)
    }
}
