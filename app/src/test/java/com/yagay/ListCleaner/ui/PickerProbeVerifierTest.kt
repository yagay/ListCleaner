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
        capturedAt = time, userId = user,
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
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(userId = 10))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = null))
    }

    @Test fun expiredOrImpossibleSessionIsNotCurrent() {
        val expired = session.copy(startedAtMillis = now - PickerProbeVerifier.SESSION_WINDOW_MS - 1L)
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = expired))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(tested = session.copy(startedAtMillis = now + 1)))
        assertEquals(PickerProbeStatus.HISTORY_ONLY, verify(result = snapshot(now + 5000L)))
        assertEquals(PickerProbeStatus.NEVER_CAPTURED, verify(tested = null, result = null))
    }

    @Test fun anEmptyMatchingRootsSnapshotStillCountsAsAnObservedResult() {
        assertEquals(PickerProbeStatus.OBSERVED_AFTER_TEST, verify(result = snapshot().copy(roots = emptyList())))
    }
}
