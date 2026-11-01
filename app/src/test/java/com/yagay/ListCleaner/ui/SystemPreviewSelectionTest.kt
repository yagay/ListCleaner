package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PickerRuntimeRoot
import com.yagay.ListCleaner.domain.PickerRuntimeApp
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

    @Test fun autofillCandidateInventoryDoesNotClaimToBeCurrentPicker() {
        val autofill = SystemProbeItem("Autofill service", "com.example.autofill", ".AutofillService")
        val candidates = SystemPreviewSelection.select(
            request(IntentKind.AUTOFILL), result(autofill), false,
            now, null, 12345, viewMode = PreviewListMode.CANDIDATES,
        )
        assertEquals(PreviewListSource.DIAGNOSTIC, candidates.source)
        assertEquals("com.example.autofill", candidates.items.single().packageName)
    }

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

    @Test fun getContentSidebarAppsAppearWithoutFakeStorageRoots() {
        val snapshot = PickerRuntimeSnapshot(
            capturedAt = now - 100L, userId = 0, roots = emptyList(),
            requestKind = "GET_CONTENT", captureSource = "SIDEBAR_ROOTS",
            apps = listOf(
                PickerRuntimeApp("com.example.viewer", "com.example.viewer.Picker", "Viewer", 0),
                PickerRuntimeApp("com.example.viewer", "com.example.viewer.Picker", "Work viewer", 10),
            ),
            totalVisibleItems = 2,
        )
        val payload = result().copy(matchingRootsSnapshot = snapshot,
            pickerProbeStatus = PickerProbeStatus.OBSERVED_AFTER_TEST)
        val current = SystemPreviewSelection.select(
            request(IntentKind.GET_CONTENT), payload, false, now, now - 1000L, 12345)
        assertEquals(PreviewListSource.DOCUMENT_ROOTS, current.source)
        assertEquals(2, current.items.size)
        assertEquals(2, current.items.map { it.component }.distinct().size)
        assertTrue(current.items.all { it.packageName == "com.example.viewer" })
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
    @Test fun pmCaptureMustMatchActionMimeAndHostOfThisPreview() {
        val captured = RuntimeInterfaceSnapshot(
            kind = IntentKind.SHARE.name, source = "PACKAGE_MANAGER_FILTERED_RETURN",
            callerUserId = 0, callerUid = 12345,
            requestAction = "android.intent.action.SEND", requestMimeType = "image/png",
            requestHost = "", capturedAtMillis = now - 100L,
            packages = listOf("com.example.filtered"),
        )
        val payload = result(raw).copy(runtimeReturn = captured)
        fun resultFor(action: String, mime: String, host: String? = null) =
            SystemPreviewSelection.select(
                request(IntentKind.SHARE), payload, false, now,
                now - 1000L, 12345,
                expectedAction = action, expectedMimeType = mime, expectedHost = host,
            ).source
        assertEquals(PreviewListSource.HOOK_OBSERVATION,
            resultFor("android.intent.action.SEND", "image/png"))
        assertEquals(PreviewListSource.API_RETURN,
            resultFor("android.intent.action.SEND", "text/plain"))
        assertEquals(PreviewListSource.API_RETURN,
            resultFor("android.intent.action.SEND_MULTIPLE", "image/png"))
        assertEquals(PreviewListSource.API_RETURN,
            resultFor("android.intent.action.SEND", "image/png", "example.com"))
    }

    @Test fun chooserAdapterOrderPreservesExactComponentOrderAfterMatchedLaunch() {
        val capture = RuntimeInterfaceSnapshot(
            kind = IntentKind.SHARE.name, source = "CHOOSER_ADAPTER_ORDER",
            callerUserId = 0, callerUid = 1000, callerPackage = "com.android.intentresolver",
            requestAction = "android.intent.action.SEND", requestMimeType = "text/plain",
            capturedAtMillis = now - 100L,
            packages = listOf("com.example.b", "com.example.a"),
            components = listOf("com.example.b/B", "com.example.a/A"),
        )
        val payload = result(raw).copy(orderedReturn = capture)
        val observed = SystemPreviewSelection.select(
            request(IntentKind.SHARE), payload, false, now, now - 1_000L, 12345,
            expectedAction = "android.intent.action.SEND", expectedMimeType = "text/plain",
        )
        assertEquals(PreviewListSource.ORDERED_ADAPTER, observed.source)
        assertEquals(listOf("com.example.b", "com.example.a"),
            observed.items.map { it.packageName })
        assertEquals(listOf("B", "A"), observed.items.map { it.className })
        // No session verification means the capture cannot become the current order.
        assertEquals(PreviewListSource.API_RETURN, SystemPreviewSelection.select(
            request(IntentKind.SHARE), payload, false, now, null, 12345,
            expectedAction = "android.intent.action.SEND", expectedMimeType = "text/plain",
        ).source)
    }

    @Test fun sortedSnapshotCannotCrossMimeHostProfileOrSession() {
        val capture = RuntimeInterfaceSnapshot(
            kind = IntentKind.OPEN.name, source = "RESOLVER_RANKED_ORDER",
            callerUserId = 0, callerUid = 1000, callerPackage = "android",
            requestAction = "android.intent.action.VIEW", requestMimeType = "application/pdf",
            requestHost = "",
            capturedAtMillis = now - 100L, packages = listOf("com.example.reader"),
            components = listOf("com.example.reader/Viewer"),
        )
        val payload = result(raw).copy(orderedReturn = capture)
        fun select(
            action: String = "android.intent.action.VIEW",
            mime: String = "application/pdf",
            host: String? = null,
            started: Long = now - 500L,
            candidate: RuntimeInterfaceSnapshot = capture,
        ) = SystemPreviewSelection.select(
            request(IntentKind.OPEN), payload.copy(orderedReturn = candidate),
            false, now, started, 12345,
            expectedAction = action, expectedMimeType = mime, expectedHost = host,
        ).source
        assertEquals(PreviewListSource.ORDERED_ADAPTER, select())
        assertEquals(PreviewListSource.API_RETURN, select(mime = "image/png"))
        assertEquals(PreviewListSource.API_RETURN, select(action = "android.intent.action.EDIT"))
        assertEquals(PreviewListSource.API_RETURN, select(host = "example.com"))
        assertEquals(PreviewListSource.API_RETURN, select(started = now))
        assertEquals(PreviewListSource.API_RETURN, select(candidate = capture.copy(callerUserId = 10)))
        assertEquals(PreviewListSource.API_RETURN, select(candidate = capture.copy(truncated = true)))
        assertEquals(PreviewListSource.API_RETURN,
            select(candidate = capture.copy(capturedAtMillis = now - 120_001L)))
    }

}
