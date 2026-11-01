package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProbeScenarioTest {
    @Test fun shareAndOpenUseTypedIntentScenarios() {
        for (kind in listOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.OPEN)) {
            assertEquals(
                listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF,
                    SystemProbeScenario.VIDEO, SystemProbeScenario.AUDIO),
                availableProbeScenarios(kind)
            )
        }
        assertEquals("image/png", SystemProbeScenario.IMAGE.mimeType)
        assertEquals("application/pdf", SystemProbeScenario.PDF.mimeType)
    }

    @Test fun eyeMimeScenariosCoverSpecificAndWildcardFiles() {
        assertEquals(SystemProbeScenario.IMAGE, previewScenarioForMime("image/webp"))
        assertEquals(SystemProbeScenario.PDF, previewScenarioForMime("application/pdf"))
        assertEquals(SystemProbeScenario.AUDIO, previewScenarioForMime("audio/flac"))
        assertEquals(SystemProbeScenario.TEXT, previewScenarioForMime("application/zip"))
        assertEquals(SystemProbeScenario.ANY, previewScenarioForMime("*/*"))
    }

    @Test fun saveDocumentNameTracksSelectedMimeNotHardcodedTxt() {
        val selected = listOf(
            SystemProbeScenario.TEXT to "ListCleaner-preview.txt",
            SystemProbeScenario.IMAGE to "ListCleaner-preview.png",
            SystemProbeScenario.PDF to "ListCleaner-preview.pdf",
            SystemProbeScenario.VIDEO to "ListCleaner-preview.mp4",
            SystemProbeScenario.AUDIO to "ListCleaner-preview.mp3",
            SystemProbeScenario.ANY to "ListCleaner-preview.bin",
        )
        for ((scenario, filename) in selected) {
            val mime = previewMimeForScenario(IntentKind.CREATE_DOCUMENT, scenario, null)
            assertEquals(scenario.mimeType, mime)
            assertEquals(filename, previewDocumentName(mime, "ListCleaner-preview.txt"))
        }
    }

    @Test fun fileAndDocumentPickerKeepSelectedMimeAndMatchingExtension() {
        for (kind in listOf(IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT, IntentKind.OPEN, IntentKind.SHARE_MULTIPLE)) {
            val req = if (kind == IntentKind.OPEN)
                SystemProbeRequest(entryKind = kind, userId = 0, customMimeType = "image/jpeg")
            else if (kind == IntentKind.SHARE_MULTIPLE)
                SystemProbeRequest(entryKind = kind, userId = 0, shareMimeType = "video/webm")
            else null
            val scenario = SystemProbeScenario.IMAGE
            val expected = when (kind) {
                IntentKind.OPEN -> "image/jpeg"
                IntentKind.SHARE_MULTIPLE -> "video/webm"
                else -> "image/png"
            }
            assertEquals(expected, previewMimeForScenario(kind, scenario, req))
            assertEquals(
                when (kind) {
                    IntentKind.OPEN -> "jpg"
                    IntentKind.SHARE_MULTIPLE -> "webm"
                    else -> "png"
                },
                previewExtensionForMime(expected)
            )
        }
        assertEquals("ListCleaner-preview.docx", previewDocumentName(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "ListCleaner-preview.txt"))
        assertEquals("ListCleaner-preview.zip", previewDocumentName(
            "application/zip", "ListCleaner-preview.txt"))
        assertEquals("ListCleaner-preview.bin", previewDocumentName(
            "application/x-unknown-vendor-type", "ListCleaner-preview.txt"))
    }

    @Test fun fileAndSendToTestTypesAreNotAllTheSame() {
        val pickerTypes = availableProbeScenarios(IntentKind.GET_CONTENT)
        assertTrue(SystemProbeScenario.VIDEO in pickerTypes)
        assertTrue(SystemProbeScenario.AUDIO in pickerTypes)
        assertEquals(SystemProbeScenario.ANY, defaultProbeScenario(IntentKind.OPEN_DOCUMENT))
        assertEquals(
            listOf(SystemProbeScenario.MAIL, SystemProbeScenario.SMS),
            availableProbeScenarios(IntentKind.SEND_TO)
        )
        assertEquals(
            listOf(SystemProbeScenario.HTTPS, SystemProbeScenario.HTTP),
            availableProbeScenarios(IntentKind.DEEP_LINK)
        )
    }

    @Test fun frameworkServiceCandidatesMustMatchManifestBindPermission() {
        val nfc = SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == IntentKind.NFC_HCE }
        assertEquals(2, nfc.size)
        assertTrue(nfc.all { it.acceptsPermission("android.permission.BIND_NFC_SERVICE") })
        assertFalse(nfc.any { it.acceptsPermission("android.permission.BIND_VPN_SERVICE") })
        assertTrue(SYSTEM_SERVICE_ENTRY_DEFINITIONS.none {
            it.kind == IntentKind.CREDENTIAL_PROVIDER
        })
    }
}
