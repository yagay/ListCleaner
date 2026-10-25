package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInterfaceSnapshotTest {
    private fun snapshot(packages: List<String>) = RuntimeInterfaceSnapshot(
        kind = IntentKind.SHARE.name,
        source = "PACKAGE_MANAGER_FILTERED_RETURN",
        callerUserId = 0,
        callerUid = 12345,
        capturedAtMillis = 123456L,
        packages = packages,
        totalPackages = packages.size,
    )

    @Test fun largeCaptureHasExplicitOriginalCount() {
        val count = RuntimeInterfaceSnapshotCodec.MAX_PACKAGES + 40
        val original = snapshot((0 until count).map { "com.example.package$it" })
        val decoded = RuntimeInterfaceSnapshotCodec.decode(
            RuntimeInterfaceSnapshotCodec.encode(original), IntentKind.SHARE
        )!!
        assertTrue(decoded.truncated)
        assertEquals(count, decoded.totalPackages)
        assertEquals(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES, decoded.packages.size)
    }

    @Test fun olderSnapshotWithoutCountMetadataStillDecodes() {
        val decoded = RuntimeInterfaceSnapshotCodec.decode(
            RuntimeInterfaceSnapshotCodec.encode(snapshot(listOf("com.example.first"))),
            IntentKind.SHARE
        )!!
        assertEquals(listOf("com.example.first"), decoded.packages)
    }

    @Test fun veryLongComponentsFitCaptureBudgetWithoutDroppingWholeSnapshot() {
        val components = (0 until RuntimeInterfaceSnapshotCodec.MAX_PACKAGES).map { i ->
            "com.example.app$i/" + "A".repeat(215)
        }
        val original = snapshot(emptyList()).copy(
            components = components, totalComponents = components.size,
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(original)
        assertTrue(encoded.isNotBlank())
        val decoded = RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.SHARE)!!
        assertTrue(decoded.truncated)
        assertTrue(decoded.components.isNotEmpty())
        assertEquals(components.size, decoded.totalComponents)
    }
}
