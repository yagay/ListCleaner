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
            listOf(SurfaceManagerGroup.NOTIFICATIONS,
                SurfaceManagerGroup.SYSTEM, SurfaceManagerGroup.MAINTENANCE),
            SurfaceManagerGroup.entries
        )
        assertEquals(1, SurfaceManagerPage.entries.count {
            it.group == SurfaceManagerGroup.NOTIFICATIONS
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

    @Test fun retiredLauncherSurfacesHaveNoNavigationEditors() {
        val mapped = SurfaceManagerPage.entries.mapNotNull { it.kind }
        assertEquals(4, mapped.size)
        val retired = setOf(SurfaceKind.LAUNCHER_DRAWER,
            SurfaceKind.LAUNCHER_PREDICTIONS, SurfaceKind.RECENTS)
        val combined = retired + SurfaceKind.NOTIFICATION_SHADE
        assertEquals(SurfaceKind.entries.toSet() - combined, mapped.toSet())
        assertEquals(mapped.size, mapped.distinct().size)
        assertFalse(SurfaceManagerPage.entries.any { it.name in setOf(
            "DRAWER", "PREDICTIONS", "RECENTS"
        ) })
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
