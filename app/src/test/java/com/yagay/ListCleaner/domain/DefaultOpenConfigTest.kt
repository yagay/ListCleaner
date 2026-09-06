package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class DefaultOpenConfigTest {
    @Test fun mimePresetsAreClassified() {
        assertEquals(OpenPreset.PDF, matchOpenPreset(IntentKind.OPEN, "application/pdf", "content"))
        assertEquals(OpenPreset.IMAGE, matchOpenPreset(IntentKind.OPEN, "image/png", "content"))
        assertEquals(OpenPreset.ARCHIVE, matchOpenPreset(IntentKind.OPEN, "application/zip", "content"))
        assertEquals(OpenPreset.BROWSER, matchOpenPreset(IntentKind.BROWSER, null, "https"))
        assertNull(matchOpenPreset(IntentKind.OPEN, "application/octet-stream", "content"))
    }

    @Test fun configRequiresMatchingKindAndCanonicalId() {
        val open = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Reader")
        DefaultOpenConfig(mapOf(OpenPreset.PDF to open.id)).validated()
        assertThrows(IllegalArgumentException::class.java) {
            DefaultOpenConfig(mapOf(OpenPreset.BROWSER to open.id)).validated()
        }
    }
}
