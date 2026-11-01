package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.*
import org.junit.Assert.*
import org.junit.Test

class PreviewMimeOptionsTest {
    private fun item(kind: IntentKind, evidence: String) = ComponentCandidate(
        ComponentRule(kind, "com.example.receiver", "com.example.receiver.Receive"),
        "Receiver", "Receive", evidence = listOf(evidence)
    )

    @Test fun eachShareActionHasItsOwnPreviewTypesEvenWhenOneListManagesAll() {
        val candidates = listOf(
            item(IntentKind.SHARE, "SEND mime=application/vnd.ms-excel"),
            item(IntentKind.SHARE, "SEND mime=image/jpeg"),
            item(IntentKind.SHARE_MULTIPLE, "SEND_MULTIPLE mime=application/zip")
        )
        val single = previewMimeOptions(IntentKind.SHARE, candidates, OpenTypeConfig())
        val multiple = previewMimeOptions(IntentKind.SHARE_MULTIPLE, candidates, OpenTypeConfig())
        assertTrue(single.any { it.mimeType == "application/vnd.ms-excel" })
        assertTrue(single.any { it.mimeType == "image/jpeg" })
        assertFalse(multiple.any { it.mimeType == "application/vnd.ms-excel" })
        assertTrue(multiple.any { it.mimeType == "application/zip" })
        assertTrue(single.any { it.mimeType == "text/plain" })
        assertEquals(single.map { it.mimeType }.size, single.map { it.mimeType }.distinct().size)
    }

    @Test fun openPreviewIncludesConfiguredMimesAndSchemesWithoutCreatingRules() {
        val types = previewMimeOptions(IntentKind.OPEN, emptyList(), OpenTypeConfig())
        assertTrue(types.any { it.openPreset == OpenPreset.PDF && it.mimeType == "application/pdf" })
        assertTrue(types.any { it.openPreset == OpenPreset.WORD && it.mimeType?.contains("wordprocessingml") == true })
        assertTrue(types.any { it.openPreset == OpenPreset.MAGNET && it.mimeType == null })
        assertFalse(types.any { it.openPreset == OpenPreset.BROWSER })
        val chosen = types.first { it.openPreset == OpenPreset.PDF }
        val req = previewMimeRequest(IntentKind.OPEN, 0, chosen)
        assertTrue(req.directLaunch)
        assertEquals(IntentKind.OPEN, req.entryKind)
        assertEquals("application/pdf", req.customMimeType)
        assertNull(req.shareMimeType)
    }

    @Test fun sharePreviewUsesActualMimeButNotOpenRuleKind() {
        val type = PreviewMimeOption(SystemProbeScenario.IMAGE, "image/jpeg", OpenPreset.IMAGE)
        val req = previewMimeRequest(IntentKind.SHARE_MULTIPLE, 10, type, "com.example", "com.example.Receive")
        assertEquals(IntentKind.SHARE_MULTIPLE, req.entryKind)
        assertEquals("image/jpeg", req.shareMimeType)
        assertNull(req.customMimeType)
        assertNull(req.openPreset)
        assertEquals("com.example", req.focusPackage)
        assertTrue(req.directLaunch)
    }
}
