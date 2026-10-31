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

    @Test fun aLockedVisibleCandidateStaysVisibleOnBulkSelect() {
        val before = SurfaceFilterConfig()
            .withLocked(SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.safe"))
        val after = before.withSelected(
            SurfaceKind.LAUNCHER_DRAWER, setOf("com.example.safe", "com.example.other"))
        assertFalse("Locked visible app must remain unhidden",
            "com.example.safe" in after.selected(SurfaceKind.LAUNCHER_DRAWER))
        assertTrue("Unlocked app is still selectable",
            "com.example.other" in after.selected(SurfaceKind.LAUNCHER_DRAWER))
    }

    @Test fun systemTileNamesArePerComponentAndCanRestoreOriginal() {
        val one = "com.example.tiles/com.example.tiles.FirstTile"
        val two = "com.example.tiles/com.example.tiles.SecondTile"
        val policy = SurfaceFilterConfig()
            .withTileLabel(one, "Office VPN")
            .withTileLabel(two, "Workspace")
        assertEquals("Office VPN", policy.tileLabels[one])
        assertEquals("Workspace", policy.tileLabels[two])
        val roundTrip = json.decodeFromString(SurfaceFilterConfig.serializer(),
            json.encodeToString(SurfaceFilterConfig.serializer(), policy)).validated()
        assertEquals(policy, roundTrip)
        val restored = roundTrip.withTileLabel(one, null)
        assertFalse(one in restored.tileLabels)
        assertEquals("Workspace", restored.tileLabels[two])
    }

    @Test fun systemTileNamesAreKeptSeparateAcrossAndroidProfiles() {
        val tile = "com.example.tiles/com.example.tiles.VpnTile"
        val personal = SurfaceFilterConfig().withTileLabel(tile, "Personal VPN")
        val work = SurfaceFilterConfig().withTileLabel(tile, "Work VPN")
        val config = ModuleConfig(
            rules = emptySet(), mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(), diagnostic = false,
            surfaceFilters = personal,
            userSpaces = mapOf(10 to UserSpaceRules(surfaceFilters = work))
        ).validated()
        val decoded = json.decodeFromString(ModuleConfig.serializer(),
            json.encodeToString(ModuleConfig.serializer(), config)).validated()
        assertEquals("Personal VPN", decoded.surfaceFilters.tileLabels[tile])
        assertEquals("Work VPN", decoded.userSpaces.getValue(10).surfaceFilters.tileLabels[tile])
    }

    @Test fun tileNamesRejectInvalidComponentsAndControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            SurfaceFilterConfig().withTileLabel("not-a-component", "Test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SurfaceFilterConfig().withTileLabel(
                "com.example.tiles/com.example.tiles.One", "Hello\nworld")
        }
    }

    @Test fun tileCandidateOrderIsPerServiceAndRestorable() {
        val a = "com.example.tiles/com.example.tiles.One"
        val b = "com.example.tiles/com.example.tiles.Two"
        val policy = SurfaceFilterConfig().withTileOrder(listOf(b, a))
        assertEquals(listOf(b, a), policy.tileOrder)
        val decoded = json.decodeFromString(SurfaceFilterConfig.serializer(),
            json.encodeToString(SurfaceFilterConfig.serializer(), policy))
        assertEquals(listOf(b, a), decoded.validated().tileOrder)
        assertTrue(decoded.withTileOrder(emptyList()).tileOrder.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            SurfaceFilterConfig().withTileOrder(listOf("invalid"))
        }
    }

    @Test fun tileCandidateOrderDoesNotAffectAnotherAndroidUser() {
        val component = "com.example.tiles/com.example.tiles.One"
        val work = SurfaceFilterConfig().withTileOrder(listOf(component))
        val stored = ModuleConfig(
            rules = emptySet(), mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(), diagnostic = false,
            userSpaces = mapOf(10 to UserSpaceRules(surfaceFilters = work))
        ).validated()
        val decoded = json.decodeFromString(ModuleConfig.serializer(),
            json.encodeToString(ModuleConfig.serializer(), stored))
        assertTrue(decoded.surfaceFilters.tileOrder.isEmpty())
        assertEquals(listOf(component), decoded.userSpaces.getValue(10).surfaceFilters.tileOrder)
    }

    @Test fun oldConfigurationWithoutSurfaceRulesStillLoadsSafely() {
        val old = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        val config = json.decodeFromString(ModuleConfig.serializer(), old).validated()
        assertTrue(config.surfaceFilters.packages.isEmpty())
        assertTrue(config.userSpaces.isEmpty())
    }
}
