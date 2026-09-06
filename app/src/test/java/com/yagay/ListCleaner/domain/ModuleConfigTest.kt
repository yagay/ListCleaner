package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModuleConfigTest {
    @Test fun hiddenFromAppsRoundTrip() {
        val config = ModuleConfig(emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715, TileConfig(), setOf("com.estrongs.android.pop"))
        val encoded = Json.encodeToString(ModuleConfig.serializer(), config)
        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(), encoded).validated())
    }

    @Test fun atomicConfigurationRoundTrip() {
        val config = ModuleConfig(setOf(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open")),
            DisplayMode.SHOW_SELECTED, PriorityConfig(mapOf(IntentKind.OPEN to listOf("com.example"))), true, 10715)
        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(),
            Json.encodeToString(ModuleConfig.serializer(), config)).validated())
    }

    @Test fun relativeNamesMatchExpandedRuleIds() {
        assertEquals(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open").id,
            ComponentRule(IntentKind.OPEN, "com.example", ".Open").id)
    }

    @Test fun controlCharactersAreRejected() {
        assertFalse(ComponentRule(IntentKind.OPEN, "com.example", "bad\nname").isValid())
    }

    @Test fun emptyTextMenuIsAllowedButFileDestinationIsProtected() {
        assertFalse(FilterPolicy.restoreEmpty("PROCESS_TEXT", 4, 0))
        assertTrue(FilterPolicy.restoreEmpty("OPEN", 4, 0))
    }

    @Test fun identicalAppInOtherProfileIsNotSameCaller() {
        assertFalse(FilterPolicy.sameCaller(10001, 1010001))
        assertTrue(FilterPolicy.sameCaller(10001, 10001))
    }
}
