package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.SurfaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every operational cleanup has one dedicated leaf; no cleanup remains in Settings. */
class SurfaceManagementNavigationTest {
    @Test fun fourGroupsContainAllManagementPages() {
        assertEquals(
            listOf(SurfaceManagerGroup.NOTIFICATIONS, SurfaceManagerGroup.LAUNCHER,
                SurfaceManagerGroup.SYSTEM, SurfaceManagerGroup.MAINTENANCE),
            SurfaceManagerGroup.entries
        )
        assertEquals(1, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.NOTIFICATIONS
        })
        assertEquals(3, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.LAUNCHER
        })
        assertEquals(4, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.SYSTEM
        })
        assertEquals(1, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.MAINTENANCE
        })
        assertTrue(SurfaceManagerPage.entries.all { page ->
            page.group in SurfaceManagerGroup.entries && page.title != 0 &&
                page.description != 0
        })
    }

    @Test fun onlyNonConsolidatedAospFiltersHaveDedicatedNavigationEditors() {
        val mapped = SurfaceManagerPage.entries.mapNotNull { it.kind }
        assertEquals(7, mapped.size)
        // NOTIFICATION_SHADE is now edited alongside notification channels,
        // rather than exposed as a second notification page.
        assertEquals(SurfaceKind.entries.toSet() - SurfaceKind.NOTIFICATION_SHADE, mapped.toSet())
        assertEquals(mapped.size, mapped.distinct().size)
    }

    @Test fun destructiveOperationsAreNotPresentedAsDisplayOnlyFilters() {
        assertEquals(null, SurfaceManagerPage.NOTIFICATIONS_CHANNELS.kind)
        assertEquals(null, SurfaceManagerPage.INVALID_RULES.kind)
        assertEquals(listOf(SurfaceManagerPage.NOTIFICATIONS_CHANNELS),
            SurfaceManagerPage.entries.filter { it.group == SurfaceManagerGroup.NOTIFICATIONS })
        assertFalse(SettingsSection.entries.any { it.name == "SURFACES" })
        assertEquals(com.yagay.ListCleaner.ui.Destination.SURFACES,
            UiStartPage.SURFACES.destination())
    }
}
