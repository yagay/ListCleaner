package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshot

/**
 * A test session records that ListCleaner *requested* a real system interface.
 * It does not assert ownership of the result: another caller can trigger DocumentsUI
 * between the request and the read. Returned-root observations aren't rendered UI pixels.
 */
internal data class PickerProbeSession(
    val kind: IntentKind,
    val scenario: SystemProbeScenario,
    val userId: Int,
    val startedAtMillis: Long,
)

internal enum class PickerProbeStatus {
    /** A matching-roots Hook snapshot was captured after this test was launched. */
    OBSERVED_AFTER_TEST,
    /** The app launched the panel but no matching-roots snapshot has arrived yet. */
    AWAITING_HOOK,
    /** A snapshot exists, but it is not attributable to the current test window. */
    HISTORY_ONLY,
    /** Nothing is available from DocumentsUI. */
    NEVER_CAPTURED,
}

/** Core verification logic, unit-testable without the Android framework. */
internal object PickerProbeVerifier {
    const val SESSION_WINDOW_MS = 5L * 60L * 1000L
    const val CLOCK_SKEW_MS = 2_000L

    fun evaluate(
        session: PickerProbeSession?,
        snapshot: PickerRuntimeSnapshot?,
        kind: IntentKind,
        scenario: SystemProbeScenario,
        userId: Int,
        nowMillis: Long,
    ): PickerProbeStatus {
        val matched = session != null &&
            session.kind == kind &&
            session.scenario == scenario &&
            session.userId == userId &&
            session.startedAtMillis in 1..nowMillis &&
            nowMillis - session.startedAtMillis <= SESSION_WINDOW_MS
        if (!matched) return if (snapshot != null) PickerProbeStatus.HISTORY_ONLY
            else PickerProbeStatus.NEVER_CAPTURED
        val capturedAfter = snapshot != null &&
            snapshot.userId == userId &&
            snapshot.capturedAt >= session!!.startedAtMillis &&
            snapshot.capturedAt <= nowMillis + CLOCK_SKEW_MS
        return when {
            capturedAfter -> PickerProbeStatus.OBSERVED_AFTER_TEST
            else -> PickerProbeStatus.AWAITING_HOOK
        }
    }
}
