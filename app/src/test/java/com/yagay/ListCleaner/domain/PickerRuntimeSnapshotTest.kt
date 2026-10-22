package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerRuntimeSnapshotTest {
    @Test fun roundTripPreservesReturnedRootsAndUser() {
        val sample = PickerRuntimeSnapshot(
            capturedAt = 1792598765432L,
            userId = 0,
            requestKind = "OPEN_DOCUMENT",
            actionCode = 2,
            stateClassName = "com.android.documentsui.base.State",
            knownActionConstants = "ACTION_OPEN=2",
            roots = listOf(
                PickerRuntimeRoot("com.android.documentsui", "Downloads", "@docroot_a", true),
                PickerRuntimeRoot("com.example.cloud", "Cloud drive", "@docroot_b", false),
            )
        )
        assertEquals(sample, PickerRuntimeSnapshotCodec.decode(
            PickerRuntimeSnapshotCodec.encode(sample)
        ))
    }

    @Test fun duplicateAndUnboundedRootsCannotOverloadDialog() {
        val root = PickerRuntimeRoot("com.example", "A".repeat(300), "@docroot_1", false)
        val sample = PickerRuntimeSnapshot(capturedAt = 1792598765432L, userId = 0, roots =
            List(PickerRuntimeSnapshotCodec.MAX_ROOTS + 50) { index ->
                if (index == 0) root else root.copy(syntheticId = "@docroot_$index")
            })
        val restored = PickerRuntimeSnapshotCodec.decode(
            PickerRuntimeSnapshotCodec.encode(sample)
        )
        assertEquals(PickerRuntimeSnapshotCodec.MAX_ROOTS, restored?.roots?.size)
        assertTrue(restored!!.roots.all { it.label.length <= 160 })
        assertTrue(restored.truncated)
        assertEquals(PickerRuntimeSnapshotCodec.MAX_ROOTS + 50, restored.totalRoots)
    }

    @Test fun malformedOrFutureDataCannotBeMisrepresentedAsLive() {
        assertNull(PickerRuntimeSnapshotCodec.decode(null))
        assertNull(PickerRuntimeSnapshotCodec.decode("invalid"))
        assertNull(PickerRuntimeSnapshotCodec.decode("x".repeat(
            PickerRuntimeSnapshotCodec.MAX_CHARS + 1
        )))
        assertNull(PickerRuntimeSnapshotCodec.decode(
            PickerRuntimeSnapshotCodec.encode(
                PickerRuntimeSnapshot(version = 2, capturedAt = 5, userId = 0, roots = emptyList())
            )
        ))
    }

    @Test fun actionDiagnosticsAreSanitizedWithoutLeakingPaths() {
        val sample = PickerRuntimeSnapshot(
            capturedAt = 123L, userId = 0, roots = emptyList(),
            stateClassName = "S".repeat(500),
            knownActionConstants = "A".repeat(500),
            actionCode = 5,
        )
        val actual = PickerRuntimeSnapshotCodec.decode(
            PickerRuntimeSnapshotCodec.encode(sample)
        )!!
        assertEquals(160, actual.stateClassName.length)
        assertEquals(240, actual.knownActionConstants.length)
        assertEquals(5, actual.actionCode)
    }

    @Test fun samplingNeverContainsRawDocumentIds() {
        val sample = PickerRuntimeSnapshot(capturedAt = 1792598765432L, userId = 0, roots =
            listOf(PickerRuntimeRoot("com.example", "Drive", "@docroot_token", false)))
        val encoded = PickerRuntimeSnapshotCodec.encode(sample)
        assertFalse(encoded.contains("content://"))
        assertFalse(encoded.contains("some-user-document-path"))
    }
}
