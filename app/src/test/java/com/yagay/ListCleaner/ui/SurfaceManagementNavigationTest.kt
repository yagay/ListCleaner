package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.SurfaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every operational cleanup has one dedicated leaf; no cleanup remains in Settings. */
class SurfaceManagementNavigationTest {
    @Test fun onlyThreeGroupsRemainAfterHomeRecentsRemoval() {
        assertEquals(
            listOf(SurfaceManagerGroup.MAINTENANCE),
            SurfaceManagerGroup.entries
        )
        assertEquals(1, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.MAINTENANCE
        })
        assertTrue(SurfaceManagerPage.entries.all { page ->
            page.group in SurfaceManagerGroup.entries && page.title != 0 &&
                page.description != 0
        })
    }

    @Test fun movedSystemListsAndNotificationsAreNotInListsNavigation() {
        assertTrue(SurfaceManagerPage.entries.mapNotNull { it.kind }.isEmpty())
        assertEquals(listOf(SurfaceManagerPage.INVALID_RULES), SurfaceManagerPage.entries)
    }

    @Test fun destructiveOperationsAreNotPresentedAsDisplayOnlyFilters() {
        assertEquals(null, SurfaceManagerPage.INVALID_RULES.kind)
        assertFalse(SettingsSection.entries.any { it.name == "SURFACES" })
        assertEquals(com.yagay.ListCleaner.ui.Destination.SURFACES,
            UiStartPage.SURFACES.destination())
    }
}
