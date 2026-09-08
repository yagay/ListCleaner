package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class OpenTypeConfigTest {
    @Test fun typedConfigRequiresOpenComponents() {
        val open = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Reader")
        OpenTypeConfig(
            rules = mapOf(OpenPreset.PDF to setOf(open.id)),
            priorities = mapOf(OpenPreset.PDF to listOf("com.example"))
        ).validated()
        assertThrows(IllegalArgumentException::class.java) {
            OpenTypeConfig(
                rules = mapOf(OpenPreset.PDF to setOf(ComponentRule(IntentKind.SHARE, "com.example", "com.example.Share").id))
            ).validated()
        }
    }

    @Test fun customRuleRequiresDefinition() {
        val open = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Reader")
        assertThrows(IllegalArgumentException::class.java) {
            OpenTypeConfig(rules = mapOf(OpenPreset.CUSTOM_1 to setOf(open.id))).validated()
        }
        val config = OpenTypeConfig(
            rules = mapOf(OpenPreset.CUSTOM_1 to setOf(open.id)),
            customDefinitions = mapOf(
                OpenPreset.CUSTOM_1 to CustomOpenDefinition("Kindle", extensions = setOf("azw3"))
            )
        ).validated()
        assertEquals("Kindle", config.customDefinitions[OpenPreset.CUSTOM_1]?.title)
        assertTrue(OpenPreset.CUSTOM_1 in config.configuredPresets())
        assertFalse(OpenPreset.CUSTOM_2 in config.configuredPresets())
    }

    @Test fun customDefinitionIsNormalized() {
        val config = OpenTypeConfig(
            customDefinitions = mapOf(
                OpenPreset.CUSTOM_1 to CustomOpenDefinition(
                    "  Playlist  ",
                    mimeTypes = setOf("APPLICATION/VND.APPLE.MPEGURL"),
                    extensions = setOf(".M3U8")
                )
            )
        ).validated()
        val definition = requireNotNull(config.customDefinitions[OpenPreset.CUSTOM_1])
        assertEquals("Playlist", definition.title)
        assertEquals(setOf("application/vnd.apple.mpegurl"), definition.mimeTypes)
        assertEquals(setOf("m3u8"), definition.extensions)
    }
}
