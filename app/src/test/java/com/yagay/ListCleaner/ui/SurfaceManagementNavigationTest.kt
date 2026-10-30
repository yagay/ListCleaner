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
        assertEquals(4, SurfaceManagerPage.entries.count {
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

    @Test fun allEightAospFiltersHaveExactlyOneDetailEditor() {
        val mapped = SurfaceManagerPage.entries.mapNotNull { it.kind }
        assertEquals(8, mapped.size)
        assertEquals(SurfaceKind.entries.toSet(), mapped.toSet())
        assertEquals(mapped.size, mapped.distinct().size)
    }

    @Test fun destructiveOperationsAreNotPresentedAsDisplayOnlyFilters() {
        assertEquals(null, SurfaceManagerPage.NOTIFICATIONS_CURRENT.kind)
        assertEquals(null, SurfaceManagerPage.NOTIFICATIONS_CHANNELS.kind)
        assertEquals(null, SurfaceManagerPage.INVALID_RULES.kind)
        assertFalse(SettingsSection.entries.any { it.name == "SURFACES" })
        assertEquals(com.yagay.ListCleaner.ui.Destination.SURFACES,
            UiStartPage.SURFACES.destination())
    }
}
