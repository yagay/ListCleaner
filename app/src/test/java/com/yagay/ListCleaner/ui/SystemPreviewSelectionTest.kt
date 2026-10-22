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

    @Test fun systemSettingsObservationIsSeparatedFromOwnHookResult() {
        val observedFromSettings = observed(IntentKind.ACCESSIBILITY, uid = 1000)
        val payload = result(raw).copy(hostReturn = observedFromSettings)
        val actual = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.HOST_OBSERVATION, actual.source)
        assertEquals("com.example.filtered", actual.items.single().packageName)
        assertEquals(PreviewListSource.UNAVAILABLE,
            SystemPreviewSelection.select(request(IntentKind.ACCESSIBILITY),
                payload.copy(hostReturn = observedFromSettings.copy(callerUserId = 10)),
                false, now, null, 12345).source)
        assertEquals(PreviewListSource.UNAVAILABLE,
            SystemPreviewSelection.select(request(IntentKind.ACCESSIBILITY),
                payload.copy(hostReturn = observedFromSettings.copy(
                    capturedAtMillis = 1L)),
                false, now + 120_001L, null, 12345).source)
    }

    @Test fun oldOrUnattributedManagerObservationRemainsVisibleOnlyInHistory() {
        val old = observed(IntentKind.ACCESSIBILITY, uid = 1000).copy(
            capturedAtMillis = 1L
        )
        val payload = result(raw).copy(unattributedReturn = old)
        val live = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.UNAVAILABLE, live.source)
        val past = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345,
            viewMode = PreviewListMode.HISTORY,
        )
        assertEquals(PreviewListSource.HISTORY, past.source)
        assertEquals("com.example.filtered", past.items.single().packageName)
    }

    @Test fun untrustedHostNeverInheritsOwnObservation() {
        val payload = result(raw).copy(
            runtimeReturn = observed(IntentKind.ACCESSIBILITY, uid = 1000)
        )
        assertEquals(PreviewListSource.UNAVAILABLE,
            SystemPreviewSelection.select(request(IntentKind.ACCESSIBILITY),
                payload, false, now, null, 12345).source)
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

    @Test fun documentRootsFromDifferentProfilesHaveUniquePreviewKeys() {
        val roots = listOf(
            PickerRuntimeRoot("com.example.drive", "Personal", "@same-root", false, 0),
            PickerRuntimeRoot("com.example.drive", "Work", "@same-root", false, 10),
        )
        val snapshot = PickerRuntimeSnapshot(
            capturedAt = now - 100L, userId = 0, roots = roots,
            requestKind = "OPEN_DOCUMENT", captureSource = "ROOTS_LOADER",
        )
        val payload = result().copy(
            matchingRootsSnapshot = snapshot,
            pickerProbeStatus = PickerProbeStatus.OBSERVED_AFTER_TEST,
        )
        for (mode in listOf(PreviewListMode.CURRENT, PreviewListMode.HISTORY)) {
            val displayed = SystemPreviewSelection.select(
                request(IntentKind.OPEN_DOCUMENT), payload, false, now,
                now - 1000L, 12345, viewMode = mode,
            )
            assertEquals(2, displayed.items.size)
            assertEquals(2, displayed.items.map { it.component }.distinct().size)
        }
    }

    @Test fun enabledAndInstalledServicesRemainSeparate() {
        val enabled = SystemProbeItem("Enabled keyboard", "com.example.ime", ".Keyboard")
        val installed = SystemProbeItem("Installed keyboard", "com.example.other", ".Keyboard")
        val payload = result(enabled).copy(
            evidence = ProbeEvidence.ENABLED,
            enabledItems = listOf(enabled),
            diagnosticCandidates = listOf(installed),
        )
        val current = SystemPreviewSelection.select(
            request(IntentKind.INPUT_METHOD), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.AUTHORITY_ENABLED, current.source)
        assertEquals(enabled, current.items.single())
        val candidates = SystemPreviewSelection.select(
            request(IntentKind.INPUT_METHOD), payload, false, now, null, 12345,
            viewMode = PreviewListMode.CANDIDATES,
        )
        assertEquals(PreviewListSource.DIAGNOSTIC, candidates.source)
        assertEquals(installed, candidates.items.single())
    }

    @Test fun accessibilityCandidatesAreNotPretendedToBeTheSettingsVisibleList() {
        val enabled = SystemProbeItem("Active service", "com.example.active", ".Service")
        val payload = result(raw).copy(
            enabledItems = listOf(enabled), diagnosticCandidates = listOf(raw),
        )
        val current = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.UNAVAILABLE, current.source)
        assertTrue(current.items.isEmpty())
        val enabledTab = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345,
            viewMode = PreviewListMode.ENABLED,
        )
        assertEquals(PreviewListSource.AUTHORITY_ENABLED, enabledTab.source)
        assertEquals(enabled, enabledTab.items.single())
        val catalog = SystemPreviewSelection.select(
            request(IntentKind.ACCESSIBILITY), payload, false, now, null, 12345,
            viewMode = PreviewListMode.CANDIDATES,
        )
        assertEquals(raw, catalog.items.single())
    }

    @Test fun roleHoldersAndEligibleAppCandidatesHaveDifferentProvenance() {
        val holder = SystemProbeItem("Current browser", "com.example.default", "@role/BROWSER")
        val candidate = SystemProbeItem("Available browser", "com.example.other", ".Browser")
        val payload = result(holder).copy(
            evidence = ProbeEvidence.ENABLED,
            enabledItems = listOf(holder),
            diagnosticCandidates = listOf(candidate),
        )
        assertEquals(PreviewListSource.AUTHORITY_ENABLED,
            SystemPreviewSelection.select(
                request(IntentKind.BROWSER), payload, false, now, null, 12345,
            ).source)
        val catalog = SystemPreviewSelection.select(
            request(IntentKind.BROWSER), payload, false, now, null, 12345,
            viewMode = PreviewListMode.CANDIDATES,
        )
        assertEquals(candidate, catalog.items.single())
    }

    @Test fun historicalDirectShareNeverBecomesLiveVisibility() {
        val previous = SystemProbeItem("Old target", "com.example.share", "@direct/abc")
        val payload = result().copy(historicalItems = listOf(previous))
        val current = SystemPreviewSelection.select(
            request(IntentKind.DIRECT_SHARE), payload, false, now, null, 12345
        )
        assertEquals(PreviewListSource.HISTORY, current.source)
        assertEquals(previous, current.items.single())
    }

    @Test fun getContentAndDirectoryPickerRequireRequestMatchedRoots() {
        val root = PickerRuntimeRoot("com.example.drive", "Drive", "@root-id", false)
        val snapshot = PickerRuntimeSnapshot(capturedAt = now - 20L, userId = 0,
            roots = listOf(root), requestKind = "GET_CONTENT")
        val payload = result().copy(
            pickerProbeStatus = PickerProbeStatus.OBSERVED_AFTER_TEST,
            matchingRootsSnapshot = snapshot,
        )
        assertEquals(PreviewListSource.DOCUMENT_ROOTS,
            SystemPreviewSelection.select(request(IntentKind.GET_CONTENT),
                payload, false, now, now - 1000L, 12345).source)
        assertEquals(PreviewListSource.UNAVAILABLE,
            SystemPreviewSelection.select(request(IntentKind.DOCUMENT_ROOT),
                payload, false, now, now - 1000L, 12345).source)
        val prior = SystemPreviewSelection.select(request(IntentKind.DOCUMENT_ROOT),
            payload, false, now, null, 12345,
            viewMode = PreviewListMode.HISTORY)
        assertEquals(PreviewListSource.HISTORY, prior.source)
        assertEquals("com.example.drive", prior.items.single().packageName)
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
