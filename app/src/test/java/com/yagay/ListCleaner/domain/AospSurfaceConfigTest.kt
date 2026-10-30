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

    @Test fun channelRulesRemainIndependentFromWholeAppHiding() {
        val config = SurfaceFilterConfig()
            .withChannelHidden("com.example.mail", "promotions", true)
        assertTrue(config.isChannelHidden("com.example.mail", "promotions"))
        assertFalse(config.isChannelHidden("com.example.mail", "messages"))
        assertTrue(config.selected(SurfaceKind.NOTIFICATION_SHADE).isEmpty())
        assertFalse(config.withChannelHidden("com.example.mail", "promotions", false)
            .isChannelHidden("com.example.mail", "promotions"))
    }

    @Test fun lockedItemsSurviveBulkClearAndPriorityOrderRoundTrips() {
        val config = SurfaceFilterConfig()
            .withSelected(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.alpha", "com.example.beta"))
            .withLocked(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.alpha"))
            .withSelected(SurfaceKind.LAUNCHER_DRAWER, emptySet())
            .withPriorities(SurfaceKind.LAUNCHER_DRAWER,
                listOf("com.example.beta", "com.example.alpha"))
        assertEquals(setOf("com.example.alpha"), config.selected(SurfaceKind.LAUNCHER_DRAWER))
        assertEquals(setOf("com.example.alpha"), config.locked(SurfaceKind.LAUNCHER_DRAWER))
        assertEquals(listOf("com.example.alpha", "com.example.beta"),
            config.movePriority(SurfaceKind.LAUNCHER_DRAWER, "com.example.alpha", -1)
                .priorities(SurfaceKind.LAUNCHER_DRAWER))
        val decoded = json.decodeFromString(SurfaceFilterConfig.serializer(),
            json.encodeToString(SurfaceFilterConfig.serializer(), config))
        assertEquals(config, decoded)
    }

    @Test fun oldConfigurationWithoutSurfaceRulesStillLoadsSafely() {
        val old = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        val config = json.decodeFromString(ModuleConfig.serializer(), old).validated()
        assertTrue(config.surfaceFilters.packages.isEmpty())
        assertTrue(config.userSpaces.isEmpty())
    }
}
