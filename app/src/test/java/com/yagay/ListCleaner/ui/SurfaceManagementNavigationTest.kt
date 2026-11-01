package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.kinds
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom Lists destination now manages Entry Global Mode, not AOSP surfaces. */
class SurfaceManagementNavigationTest {
    @Test fun listsDestinationKeepsItsNavigationIdentity() {
        assertEquals(Destination.SURFACES, UiStartPage.SURFACES.destination())
        assertFalse(SettingsSection.entries.any { it.name == "SURFACES" })
        assertEquals(7, EntryGroup.entries.size)
    }

    @Test fun listsGlobalModePresentsEverySelectableEntryKindExactlyOnce() {
        val kinds = EntryGroup.entries.flatMap { it.kinds() }
        assertEquals(kinds.size, kinds.distinct().size)
        assertTrue(IntentKind.OPEN in kinds)
        assertTrue(IntentKind.SHARE in kinds)
        assertTrue(IntentKind.DIRECT_SHARE in kinds)
        assertFalse(IntentKind.CREDENTIAL_PROVIDER in kinds)
        assertFalse(IntentKind.LAUNCHER_SHORTCUT in kinds)
    }

    @Test fun onlyTwoVisibleExecutionGroupsWithCorrectMembership() {
        val root = CleanupCategoryRegistry.rootCategories()
        val lsposed = CleanupCategoryRegistry.lsposedCategories()
        assertEquals(CleanupKind.entries.size, root.size)
        assertTrue(root.all { it.executionGroup() == CleanupExecutionGroup.ROOT })
        assertTrue(lsposed.all { it.executionGroup() == CleanupExecutionGroup.LSPOSED })
        assertTrue(lsposed.contains(CleanupCategoryId.Managed(ManagedComponentSurface.SETTINGS_APPS)))
        assertTrue(lsposed.contains(CleanupCategoryId.Managed(ManagedComponentSurface.NOTIFICATIONS)))
        assertFalse(root.any { it is CleanupCategoryId.Managed })
        assertEquals(IntentKind.entries.count { it.isSelectableEntryKind() } + 2, lsposed.size)
    }

    @Test fun settingsStillOwnIndependentListPresentationPreferences() {
        assertTrue(SettingsSection.LISTS in SettingsSection.entries)
        assertTrue(SettingsSection.COMPATIBILITY in SettingsSection.entries)
    }
}
