package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class OpenPresetTest {
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

    @Test fun opaqueMimeFallsBackToFileExtension() {
        assertEquals(OpenPreset.PDF, matchOpenPreset(IntentKind.OPEN, "*/*", "content", "/storage/emulated/0/Download/book.PDF"))
        assertEquals(OpenPreset.APK, matchOpenPreset(IntentKind.OPEN, "application/octet-stream", "content", "release.apk"))
        assertEquals(OpenPreset.ARCHIVE, matchOpenPreset(IntentKind.OPEN, "binary/octet-stream", "file", "backup.7z"))
        assertEquals(OpenPreset.WORD, matchOpenPreset(IntentKind.OPEN, null, "content", "document.docx"))
        assertEquals(OpenPreset.IMAGE, matchOpenPreset(IntentKind.OPEN, "application/x-download", "content", "photo.webp?token=1"))
    }

    @Test fun encodedDocumentIdsFallBackToDecodedExtension() {
        assertEquals(OpenPreset.PDF,
            matchOpenPreset(IntentKind.OPEN, null, "content", "primary%3ADownload%2Fbook.pdf"))
        assertEquals(OpenPreset.WORD,
            matchOpenPreset(IntentKind.OPEN, "application/octet-stream", "content", "home%3ADocuments%2Freport.DOCX"))
        assertEquals(OpenPreset.ARCHIVE,
            matchOpenPreset(IntentKind.OPEN, "*/*", "content", "downloads%2Fbackup.7z"))
        assertEquals(OpenPreset.PDF,
            matchOpenPreset(IntentKind.OPEN, null, "content", "folder%2Fmy+book.pdf"))
    }

    @Test fun specificUnknownMimeDoesNotGetOverriddenByExtension() {
        assertNull(matchOpenPreset(IntentKind.OPEN, "application/vnd.example.custom", "content", "looks-like.pdf"))
        assertEquals(OpenPreset.TEXT, matchOpenPreset(IntentKind.OPEN, "text/plain", "content", "looks-like.pdf"))
    }

    @Test fun customMimeAndExtensionAreClassified() {
        val definitions = mapOf(
            OpenPreset.CUSTOM_1 to CustomOpenDefinition(
                title = "Kindle",
                mimeTypes = setOf("application/vnd.amazon.ebook"),
                extensions = setOf("azw3", "mobi")
            ).validated(),
            OpenPreset.CUSTOM_2 to CustomOpenDefinition(
                title = "Playlist",
                mimeTypes = setOf("application/vnd.apple.mpegurl"),
                extensions = setOf("m3u8")
            ).validated()
        )
        assertEquals(OpenPreset.CUSTOM_1,
            matchOpenPreset(IntentKind.OPEN, "application/vnd.amazon.ebook", "content", "book.bin", definitions))
        assertEquals(OpenPreset.CUSTOM_1,
            matchOpenPreset(IntentKind.OPEN, "application/octet-stream", "content", "book.AZW3", definitions))
        assertEquals(OpenPreset.CUSTOM_2,
            matchOpenPreset(IntentKind.OPEN, null, "content", "stream.m3u8?token=1", definitions))
    }

    @Test fun customWildcardMimeCanMatchSubtypeFamily() {
        val definitions = mapOf(
            OpenPreset.CUSTOM_1 to CustomOpenDefinition("Custom image", setOf("image/*"), emptySet()).validated()
        )
        assertEquals(OpenPreset.CUSTOM_1,
            matchOpenPreset(IntentKind.OPEN, "image/heic", "content", null, definitions))
    }

    private fun scannedOpen(vararg evidence: String, active: Boolean = true) = ComponentCandidate(
        rule = ComponentRule(IntentKind.OPEN, "com.example.reader", "com.example.reader.OpenActivity"),
        appLabel = "Reader",
        activityLabel = "OpenActivity",
        evidence = evidence.toList(),
        unavailable = !active,
    )

    @Test fun openWithTypeSummaryMergesUniqueFormatsAcrossProbes() {
        val candidate = scannedOpen(
            "VIEW scheme=content mime=application/pdf sample=report.pdf flags=0x40",
            "VIEW scheme=file mime=application/pdf sample=report.pdf flags=0x40",
            "VIEW scheme=content mime=application/msword sample=report.doc flags=0x40",
            "BROAD VIEW scheme=content mime=image/* flags=0x40",
            "BROAD VIEW scheme=file mime=*/* flags=0x40",
        )
        val summary = summarizeOpenWithTypes(listOf(candidate))
        assertTrue(summary.universal)
        assertEquals(listOf(OpenPreset.PDF, OpenPreset.WORD, OpenPreset.IMAGE), summary.presets)
    }

    @Test fun universalTagRequiresExplicitWildcardResolverMatch() {
        val narrow = scannedOpen("VIEW scheme=content mime=application/pdf sample=doc.pdf flags=0x40")
        val fallback = scannedOpen("VIEW scheme=content mime=application/octet-stream sample=item flags=0x40")
        val historical = scannedOpen("VIEW scheme=content mime=*/*", active = false)
        val summary = summarizeOpenWithTypes(listOf(narrow, fallback, historical))
        assertFalse(summary.universal)
        assertEquals(listOf(OpenPreset.PDF), summary.presets)
    }

    @Test fun openWithTypeSummaryIncludesConfiguredCustomLabelsButNotOtherKinds() {
        val definitions = mapOf(
            OpenPreset.CUSTOM_1 to CustomOpenDefinition(
                title = "Kindle", mimeTypes = setOf("application/vnd.amazon.ebook"),
            ).validated()
        )
        val open = scannedOpen(
            "CUSTOM preset=CUSTOM_1 VIEW scheme=content mime=application/vnd.amazon.ebook sample=custom-0.pdf",
        )
        val share = ComponentCandidate(
            rule = ComponentRule(IntentKind.SHARE, "com.example.reader", "com.example.reader.Share"),
            appLabel = "Reader", activityLabel = "Share",
            evidence = listOf("BROAD SEND mime=*/*"),
        )
        val summary = summarizeOpenWithTypes(listOf(share, open), definitions)
        assertFalse(summary.universal)
        assertEquals(listOf(OpenPreset.CUSTOM_1), summary.presets)
    }

}
