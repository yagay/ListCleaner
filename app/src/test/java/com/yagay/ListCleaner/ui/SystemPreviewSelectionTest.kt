package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PickerRuntimeRoot
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPreviewSelectionTest {
    private val now = 100_000L
    private fun request(kind: IntentKind) = SystemProbeRequest(entryKind = kind, userId = 0)
    private fun result(vararg items: SystemProbeItem) = SystemProbeResult(
        0, 0, items = items.toList()
    )
    private val raw = SystemProbeItem("Unfiltered", "com.example.raw", ".Service")

    private fun observed(kind: IntentKind, uid: Int = 12345, source: String = "SYSTEM_MANAGER_RETURN") =
        RuntimeInterfaceSnapshot(
            kind = kind.name, source = source, callerUserId = 0, callerUid = uid,
            capturedAtMillis = 99_000L, packages = listOf("com.example.filtered"),
        )

    @Test fun managerCandidatesNeverPretendToBeFiltered() {
        val actual = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), result(raw),
            false, now, null, 12345
        )
        assertEquals(PreviewListSource.UNAVAILABLE, actual.source)
        assertTrue(actual.items.isEmpty())
    }

    @Test fun managerReturnMustBeFromThisCallerAndRightSource() {
        val payload = result(raw).copy(runtimeReturn = observed(IntentKind.ACCESSIBILITY))
        val actual = SystemPreviewSelection.select(request(IntentKind.ACCESSIBILITY),
            payload, false, now, null, 12345)
        assertEquals(PreviewListSource.HOOK_OBSERVATION, actual.source)
        assertEquals("com.example.filtered", actual.items.single().packageName)
        assertEquals(PreviewListSource.UNAVAILABLE,
            SystemPreviewSelection.select(request(IntentKind.ACCESSIBILITY),
                payload, false, now, null, 1000).source)
    }

    @Test fun trueEmptyHookReturnIsNotConfusedWithMissingCapture() {
        val payload = result(raw).copy(runtimeReturn = observed(IntentKind.ACCESSIBILITY).copy(
            packages = emptyList(), components = emptyList()
        ))
        val selected = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.HOOK_OBSERVATION, selected.source)
        assertTrue(selected.items.isEmpty())
    }

    @Test fun documentRootsRequireOperationAndSessionEvidence() {
        val root = PickerRuntimeRoot("com.example.documents", "Documents", "@root", false)
        val snapshot = PickerRuntimeSnapshot(
            capturedAt = 99_000L, userId = 0, roots = listOf(root), requestKind = "CREATE_DOCUMENT",
        )
        val payload = result().copy(
            pickerProbeStatus = PickerProbeStatus.OBSERVED_AFTER_TEST,
            matchingRootsSnapshot = snapshot,
        )
        assertEquals(PreviewListSource.UNAVAILABLE, SystemPreviewSelection.select(
            request(IntentKind.OPEN_DOCUMENT), payload, false, now, 98_000, 12345
        ).source)
        assertEquals(PreviewListSource.DOCUMENT_ROOTS, SystemPreviewSelection.select(
            request(IntentKind.CREATE_DOCUMENT), payload, false, now, 98_000, 12345
        ).source)
        assertEquals(PreviewListSource.UNAVAILABLE, SystemPreviewSelection.select(
            request(IntentKind.CREATE_DOCUMENT), payload.copy(
                matchingRootsSnapshot = snapshot.copy(requestKind = "UNKNOWN")),
            false, now, 98_000, 12345
        ).source)
    }

    @Test fun showApiRemainsSeparateFromFilteredResults() {
        val payload = result(raw).copy(runtimeReturn = observed(IntentKind.ACCESSIBILITY))
        val selected = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, true, now, null, 12345
        )
        assertEquals(PreviewListSource.DIAGNOSTIC, selected.source)
        assertEquals(raw, selected.items.single())
    }
}
