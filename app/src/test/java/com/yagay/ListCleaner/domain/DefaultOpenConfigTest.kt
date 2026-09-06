package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class DefaultOpenConfigTest {
    @Test fun mimePresetsAreClassified() {
        assertEquals(OpenPreset.PDF, matchOpenPreset(IntentKind.OPEN, "application/pdf", "content"))
        assertEquals(OpenPreset.WORD, matchOpenPreset(IntentKind.OPEN, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "content"))
        assertEquals(OpenPreset.EXCEL, matchOpenPreset(IntentKind.OPEN, "application/vnd.ms-excel", "content"))
        assertEquals(OpenPreset.POWERPOINT, matchOpenPreset(IntentKind.OPEN, "application/vnd.ms-powerpoint", "content"))
        assertEquals(OpenPreset.EPUB, matchOpenPreset(IntentKind.OPEN, "application/epub+zip", "content"))
        assertEquals(OpenPreset.APK, matchOpenPreset(IntentKind.OPEN, "application/vnd.android.package-archive", "content"))
        assertEquals(OpenPreset.TORRENT, matchOpenPreset(IntentKind.OPEN, "application/x-bittorrent", "content"))
        assertEquals(OpenPreset.MARKDOWN, matchOpenPreset(IntentKind.OPEN, "text/markdown", "content"))
        assertEquals(OpenPreset.CSV, matchOpenPreset(IntentKind.OPEN, "text/csv", "content"))
        assertEquals(OpenPreset.JSON, matchOpenPreset(IntentKind.OPEN, "application/problem+json", "content"))
        assertEquals(OpenPreset.XML, matchOpenPreset(IntentKind.OPEN, "application/atom+xml", "content"))
        assertEquals(OpenPreset.SVG, matchOpenPreset(IntentKind.OPEN, "image/svg+xml", "content"))
        assertEquals(OpenPreset.GIF, matchOpenPreset(IntentKind.OPEN, "image/gif", "content"))
        assertEquals(OpenPreset.IMAGE, matchOpenPreset(IntentKind.OPEN, "image/png", "content"))
        assertEquals(OpenPreset.ARCHIVE, matchOpenPreset(IntentKind.OPEN, "application/zip", "content"))
        assertEquals(OpenPreset.BROWSER, matchOpenPreset(IntentKind.BROWSER, null, "https"))
        assertNull(matchOpenPreset(IntentKind.OPEN, "application/octet-stream", "content"))
    }

    @Test fun schemePresetsAreClassified() {
        assertEquals(OpenPreset.MAGNET, matchOpenPreset(IntentKind.OPEN, null, "magnet"))
        assertEquals(OpenPreset.GEO, matchOpenPreset(IntentKind.OPEN, null, "geo"))
        assertEquals(OpenPreset.MAILTO, matchOpenPreset(IntentKind.OPEN, null, "mailto"))
        assertEquals(OpenPreset.TEL, matchOpenPreset(IntentKind.OPEN, null, "tel"))
        assertEquals(OpenPreset.SMS, matchOpenPreset(IntentKind.OPEN, null, "sms"))
        assertEquals(OpenPreset.SMS, matchOpenPreset(IntentKind.OPEN, null, "smsto"))
    }

    @Test fun specificImageAndTextTypesBeatBroadCategories() {
        assertEquals(OpenPreset.SVG, matchOpenPreset(IntentKind.OPEN, "image/svg+xml", "content"))
        assertEquals(OpenPreset.GIF, matchOpenPreset(IntentKind.OPEN, "image/gif", "content"))
        assertEquals(OpenPreset.MARKDOWN, matchOpenPreset(IntentKind.OPEN, "text/x-markdown", "content"))
        assertEquals(OpenPreset.CSV, matchOpenPreset(IntentKind.OPEN, "application/csv", "content"))
    }

    @Test fun configRequiresMatchingKindAndCanonicalId() {
        val open = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Reader")
        DefaultOpenConfig(mapOf(OpenPreset.PDF to open.id, OpenPreset.MAGNET to open.id)).validated()
        assertThrows(IllegalArgumentException::class.java) {
            DefaultOpenConfig(mapOf(OpenPreset.BROWSER to open.id)).validated()
        }
    }
}
