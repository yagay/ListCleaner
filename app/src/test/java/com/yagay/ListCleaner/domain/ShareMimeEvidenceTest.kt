package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareMimeEvidenceTest {
    private fun candidate(kind: IntentKind, vararg evidence: String, unavailable: Boolean = false) =
        ComponentCandidate(
            rule = ComponentRule(kind, "com.example.receiver", "com.example.receiver.ReceiveActivity"),
            appLabel = "Receiver",
            activityLabel = "ReceiveActivity",
            evidence = evidence.toList(),
            unavailable = unavailable,
        )

    @Test fun sendAndSendMultipleUseSeparateObservedMimeInventories() {
        val single = candidate(IntentKind.SHARE,
            "SEND mime=text/plain flags=0x10000",
            "SEND mime=application/pdf flags=0x10000",
            "BROAD SEND mime=image/* flags=0x10000")
        val multi = candidate(IntentKind.SHARE_MULTIPLE,
            "SEND_MULTIPLE mime=image/png flags=0x10000",
            "SEND_MULTIPLE mime=application/zip flags=0x10000")
        assertEquals(listOf("text/plain", "image/*", "application/pdf"),
            observedShareMimeTypes(listOf(single, multi), IntentKind.SHARE))
        assertEquals(listOf("image/png", "application/zip"),
            observedShareMimeTypes(listOf(single, multi), IntentKind.SHARE_MULTIPLE))
        assertFalse(single.matchesShareMimeType("application/zip"))
        assertFalse(multi.matchesShareMimeType("text/plain"))
    }

    @Test fun wildcardReceiversMatchConcreteTypesWithoutInventingSupport() {
        val images = candidate(IntentKind.SHARE, "BROAD SEND mime=image/* flags=0x10000")
        val universal = candidate(IntentKind.SHARE, "BROAD SEND mime=*/* flags=0x10000")
        assertTrue(images.matchesShareMimeType("image/jpeg"))
        assertFalse(images.matchesShareMimeType("video/mp4"))
        assertTrue(universal.matchesShareMimeType("application/pdf"))
        assertTrue(universal.matchesShareMimeType("*/*"))
        assertFalse(images.matchesShareMimeType("*/*"))
    }

    @Test fun sameOpenPresetsGroupShareMimesWithoutMixingMultipleAction() {
        val share = candidate(IntentKind.SHARE,
            "SEND mime=application/pdf",
            "SEND mime=image/jpeg",
            "SEND mime=image/png",
            "SEND mime=application/zip",
            "SEND mime=application/x-unknown-file")
        val multiple = candidate(IntentKind.SHARE_MULTIPLE, "SEND_MULTIPLE mime=video/mp4")
        val all = listOf(share, multiple)
        val options = observedShareTypeOptions(all, IntentKind.SHARE)
        assertTrue(options.any { it.preset == OpenPreset.PDF })
        assertTrue(options.any { it.preset == OpenPreset.IMAGE &&
            it.mimeTypes == listOf("image/jpeg", "image/png") })
        assertTrue(options.any { it.preset == OpenPreset.ARCHIVE })
        assertTrue(options.any { it.key == "mime:application/x-unknown-file" })
        assertFalse(options.any { it.preset == OpenPreset.VIDEO })
        assertEquals(listOf(OpenPreset.VIDEO),
            observedShareTypeOptions(all, IntentKind.SHARE_MULTIPLE).mapNotNull { it.preset })
        assertTrue(share.matchesShareTypeOption(options.first { it.preset == OpenPreset.IMAGE }))
        assertFalse(multiple.matchesShareTypeOption(options.first { it.preset == OpenPreset.IMAGE }))
    }

    @Test fun unknownAndUnavailableCandidatesDoNotAdvertiseTypes() {
        val unavailable = candidate(IntentKind.SHARE, "SEND mime=text/plain", unavailable = true)
        val unknown = candidate(IntentKind.SHARE, "OTHER_SOURCE mime=image/png",
            "SEND mime=not-a-mime", "SEND mime=bad/value/extra")
        assertTrue(observedShareMimeTypes(listOf(unavailable, unknown), IntentKind.SHARE).isEmpty())
        assertFalse(unknown.matchesShareMimeType("image/png"))
    }
}

