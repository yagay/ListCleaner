package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AospSurfaceConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun allAospSurfaceKindsStartDisabled() {
        val config = SurfaceFilterConfig().validated()
        SurfaceKind.entries.forEach { assertTrue(config.selected(it).isEmpty()) }
    }

    @Test fun rulesDoNotBleedAcrossSurfaces() {
        val config = SurfaceFilterConfig()
            .withSelected(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.one"))
            .withSelected(SurfaceKind.RECENTS, setOf("com.example.two"))
        assertEquals(setOf("com.example.one"), config.selected(SurfaceKind.LAUNCHER_DRAWER))
        assertEquals(setOf("com.example.two"), config.selected(SurfaceKind.RECENTS))
        assertTrue(config.selected(SurfaceKind.NOTIFICATION_SHADE).isEmpty())
    }

    @Test fun mediaOutputUsesActualRouteIdentifiersNotAppPackageNames() {
        val config = SurfaceFilterConfig().withSelected(
            SurfaceKind.MEDIA_OUTPUT, setOf("route:bluetooth:42", "route@desk")
        )
        assertEquals(2, config.selected(SurfaceKind.MEDIA_OUTPUT).size)
        assertThrows(IllegalArgumentException::class.java) {
            SurfaceFilterConfig().withSelected(
                SurfaceKind.LAUNCHER_DRAWER, setOf("bad package name")
            )
        }
    }

    @Test fun moduleAndPerUserPoliciesRoundTripIndependently() {
        val work = SurfaceFilterConfig().withSelected(
            SurfaceKind.NOTIFICATION_SHADE, setOf("com.example.work")
        )
        val personal = SurfaceFilterConfig().withSelected(
            SurfaceKind.LAUNCHER_PREDICTIONS, setOf("com.example.home")
        )
        val config = ModuleConfig(
            rules = emptySet(), mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(), diagnostic = false,
            surfaceFilters = personal,
            userSpaces = mapOf(10 to UserSpaceRules(surfaceFilters = work))
        ).validated()
        val decoded = json.decodeFromString(ModuleConfig.serializer(),
            json.encodeToString(ModuleConfig.serializer(), config)).validated()
        assertEquals(personal, decoded.surfaceFilters)
        assertEquals(work, decoded.userSpaces.getValue(10).surfaceFilters)
    }

    @Test fun oldConfigurationWithoutSurfaceRulesStillLoadsSafely() {
        val old = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        val config = json.decodeFromString(ModuleConfig.serializer(), old).validated()
        assertTrue(config.surfaceFilters.packages.isEmpty())
        assertTrue(config.userSpaces.isEmpty())
    }
}
