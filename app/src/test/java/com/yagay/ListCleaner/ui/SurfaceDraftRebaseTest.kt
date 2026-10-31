package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
import org.junit.Assert.*
import org.junit.Test

class SurfaceDraftRebaseTest {
    @Test fun editsInAnotherSurfaceRemainAfterRebase() {
        val base = SurfaceFilterConfig()
        val local = base.withSelected(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.launcher"))
        val latest = base.withSelected(SurfaceKind.SETTINGS_APPS, setOf("com.example.settings"))
        val result = rebaseSurfaceDraft(base, local, latest)
        assertEquals(setOf("com.example.launcher"), result.selected(SurfaceKind.LAUNCHER_DRAWER))
        assertEquals(setOf("com.example.settings"), result.selected(SurfaceKind.SETTINGS_APPS))
    }
    @Test fun removalsAndConcurrentAddsAreBothPreserved() {
        val base = SurfaceFilterConfig().withSelected(SurfaceKind.RECENTS,
            setOf("com.example.old", "com.example.keep"))
        val local = base.withSelected(SurfaceKind.RECENTS, setOf("com.example.keep"))
        val latest = base.withSelected(SurfaceKind.RECENTS,
            setOf("com.example.old", "com.example.keep", "com.example.new"))
        assertEquals(setOf("com.example.keep", "com.example.new"),
            rebaseSurfaceDraft(base, local, latest).selected(SurfaceKind.RECENTS))
    }
    @Test fun independentCategoryAndChannelChangesSurvive() {
        val base = SurfaceFilterConfig()
        val local = base.withNotificationCategoryHidden("msg", true)
        val latest = base.withChannelHidden("com.example.chat", "inbox", true)
        val merged = rebaseSurfaceDraft(base, local, latest)
        assertTrue("msg" in merged.hiddenNotificationCategories)
        assertTrue(merged.isChannelHidden("com.example.chat", "inbox"))
    }
    @Test fun concurrentTileOrdersUseLatestUnlessLocallyChanged() {
        val a = "com.example.tiles/com.example.tiles.One"
        val b = "com.example.tiles/com.example.tiles.Two"
        val base = SurfaceFilterConfig()
        val latest = base.withTileOrder(listOf(a, b))
        assertEquals(listOf(a, b), rebaseSurfaceDraft(base, base, latest).tileOrder)
        val local = base.withTileOrder(listOf(b, a))
        assertEquals(listOf(b, a), rebaseSurfaceDraft(base, local, latest).tileOrder)
    }

    @Test fun noChangesFollowLatest() {
        val base = SurfaceFilterConfig()
        val latest = base.withSelected(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.updated"))
        assertEquals(latest, rebaseSurfaceDraft(base, base, latest))
    }
}