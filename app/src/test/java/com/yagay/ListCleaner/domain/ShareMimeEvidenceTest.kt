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

    @Test fun shareTypePolicyIsIndependentAcrossActionsAndFormats() {
        val single = ComponentRule(IntentKind.SHARE, "com.example.receiver", "com.example.receiver.Receive")
        val multiple = single.copy(kind = IntentKind.SHARE_MULTIPLE)
        val config = OpenTypeConfig(
            shareRules = mapOf(
                IntentKind.SHARE to mapOf(
                    "preset:IMAGE" to setOf(single.id),
                    "preset:PDF" to setOf(single.id),
                ),
                IntentKind.SHARE_MULTIPLE to mapOf(
                    "preset:IMAGE" to setOf(multiple.id),
                ),
            ),
            sharePriorities = mapOf(
                IntentKind.SHARE to mapOf("preset:IMAGE" to listOf("com.example.receiver")),
            ),
        ).validated()
        assertEquals(setOf(single), config.selectedShareRules(IntentKind.SHARE, "preset:IMAGE"))
        assertEquals(setOf(multiple), config.selectedShareRules(IntentKind.SHARE_MULTIPLE, "preset:IMAGE"))
        assertTrue(config.selectedShareRules(IntentKind.SHARE_MULTIPLE, "preset:PDF").isEmpty())
        assertTrue(config.selectedRules(OpenPreset.IMAGE).isEmpty())
        assertEquals(listOf("com.example.receiver"), config.sharePriority(IntentKind.SHARE, "preset:IMAGE"))
        assertTrue(config.sharePriority(IntentKind.SHARE_MULTIPLE, "preset:IMAGE").isEmpty())
        assertEquals("preset:IMAGE", shareTypeKeyForMime("image/png"))
        assertEquals("preset:IMAGE", shareTypeKeyForMime("image/jpeg"))
        assertEquals("preset:PDF", shareTypeKeyForMime("application/pdf"))
        assertEquals("mime:application/x-unknown-file", shareTypeKeyForMime("application/x-unknown-file"))
        assertEquals("universal", shareTypeKeyForMime("*/*"))
        assertEquals(null, shareTypeKeyForMime(null))
    }

    @Test fun shareTypeRulesRejectMixedKindAndInvalidScope() {
        val share = ComponentRule(IntentKind.SHARE, "com.example.receiver", "com.example.receiver.Receive")
        val invalid = OpenTypeConfig(
            shareRules = mapOf(IntentKind.SHARE_MULTIPLE to mapOf("preset:IMAGE" to setOf(share.id)))
        )
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            invalid.validated()
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            OpenTypeConfig(shareRules = mapOf(
                IntentKind.SHARE to mapOf("preset:BROWSER" to setOf(share.id))
            )).validated()
        }
    }

    @Test fun unknownAndUnavailableCandidatesDoNotAdvertiseTypes() {
        val unavailable = candidate(IntentKind.SHARE, "SEND mime=text/plain", unavailable = true)
        val unknown = candidate(IntentKind.SHARE, "OTHER_SOURCE mime=image/png",
            "SEND mime=not-a-mime", "SEND mime=bad/value/extra")
        assertTrue(observedShareMimeTypes(listOf(unavailable, unknown), IntentKind.SHARE).isEmpty())
        assertFalse(unknown.matchesShareMimeType("image/png"))
    }
}

