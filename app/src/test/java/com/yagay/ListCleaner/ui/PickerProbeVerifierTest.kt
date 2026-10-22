package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PickerRuntimeRoot
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerProbeVerifierTest {
    private val now = 100_000L
    private val session = PickerProbeSession(
        IntentKind.GET_CONTENT, SystemProbeScenario.IMAGE, 0, 95_000L,
    )

    private fun snapshot(time: Long = 96_000L, user: Int = 0) = PickerRuntimeSnapshot(
        capturedAt = time, userId = user, requestKind = "GET_CONTENT",
        roots = listOf(PickerRuntimeRoot("com.example.files", "Files", "id", false)),
    )

    private fun verify(
        tested: PickerProbeSession? = session,
        result: PickerRuntimeSnapshot? = snapshot(),
        kind: IntentKind = IntentKind.GET_CONTENT,
        scenario: SystemProbeScenario = SystemProbeScenario.IMAGE,
        userId: Int = 0,
        nowMillis: Long = now,
    ) = PickerProbeVerifier.evaluate(tested, result, kind, scenario, userId, nowMillis)

    @Test fun onlyObservationsAfterTheSameTestAreCorrelated() {
        assertEquals(PickerProbeStatus.OBSERVED_AFTER_TEST, verify())
        assertEquals(PickerProbeStatus.AWAITING_HOOK, verify(result = null))
        assertEquals(PickerProbeStatus.AWAITING_HOOK, verify(result = snapshot(94_999L)))
        assertEquals(PickerProbeStatus.AWAITING_HOOK, verify(result = snapshot(user = 10)))
    }

    @Test fun aDifferentScenarioOrAndroidUserNeverInheritsAnotherTestsResult() {
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(scenario = SystemProbeScenario.PDF))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(kind = IntentKind.OPEN_DOCUMENT))
        assertEquals(PickerProbeStatus.NEVER_CAPTURED, verify(userId = 10))
        assertEquals(PickerProbeStatus.NEVER_CAPTURED,
            verify(tested = null, result = snapshot(user = 10)))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = null))
    }

    @Test fun expiredOrImpossibleSessionIsNotCurrent() {
        val expired = session.copy(startedAtMillis = now - PickerProbeVerifier.SESSION_WINDOW_MS - 1L)
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = expired))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = session.copy(startedAtMillis = now + 1)))
        assertEquals(PickerProbeStatus.AWAITING_HOOK, verify(result = snapshot(now + 5000L)))
        assertEquals(PickerProbeStatus.NEVER_CAPTURED, verify(tested = null, result = null))
    }

    @Test fun mismatchedUserObservationCannotBecomeHistory() {
        assertEquals(PickerProbeStatus.AWAITING_HOOK, verify(result = snapshot(user = 10)))
        assertEquals(PickerProbeStatus.NEVER_CAPTURED,
            verify(tested = null, result = snapshot(user = 10)))
    }

    @Test fun openAndSaveRejectUnknownOrOtherActionSnapshots() {
        val openSession = session.copy(kind = IntentKind.OPEN_DOCUMENT)
        val openRoot = snapshot().copy(requestKind = "OPEN_DOCUMENT")
        assertEquals(PickerProbeStatus.OBSERVED_AFTER_TEST,
            verify(tested = openSession, kind = IntentKind.OPEN_DOCUMENT, result = openRoot))
        assertEquals(PickerProbeStatus.AWAITING_HOOK,
            verify(tested = openSession, kind = IntentKind.OPEN_DOCUMENT,
                result = openRoot.copy(requestKind = "UNKNOWN")))
        assertEquals(PickerProbeStatus.AWAITING_HOOK,
            verify(tested = openSession, kind = IntentKind.OPEN_DOCUMENT,
                result = openRoot.copy(requestKind = "CREATE_DOCUMENT")))
    }

    @Test fun getContentAndOpenTreeRejectDifferentRequestActions() {
        assertEquals(PickerProbeStatus.AWAITING_HOOK,
            verify(result = snapshot().copy(requestKind = "UNKNOWN")))
        assertEquals(PickerProbeStatus.AWAITING_HOOK,
            verify(result = snapshot().copy(requestKind = "OPEN_DOCUMENT")))
        val tree = session.copy(kind = IntentKind.DOCUMENT_ROOT)
        assertEquals(PickerProbeStatus.OBSERVED_AFTER_TEST,
            verify(tested = tree, kind = IntentKind.DOCUMENT_ROOT,
                result = snapshot().copy(requestKind = "DOCUMENT_ROOT")))
    }

    @Test fun anEmptyMatchingRootsSnapshotStillCountsAsAnObservedResult() {
        assertEquals(PickerProbeStatus.OBSERVED_AFTER_TEST, verify(result = snapshot().copy(roots = emptyList())))
    }
}
