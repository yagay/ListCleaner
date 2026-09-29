package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRuntimeAuditTest {
    private fun candidate(kind: IntentKind): ComponentCandidate = ComponentCandidate(
        rule = ComponentRule(kind, "com.example", "com.example.Target"),
        appLabel = "Example",
        activityLabel = "Target",
    )

    @Test fun assistantFilterEvidenceIsRecognized() {
        val item = candidate(IntentKind.ASSISTANT)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = """
            09-29 I/ListCleaner.AssistantRole: pid=123 process=com.android.permissioncontroller HOOK_INSTALLED package=com.android.permissioncontroller method=x
            09-29 I/ListCleaner.AssistantRole: pid=123 process=com.android.permissioncontroller FILTER role=android.app.role.ASSISTANT before=4 after=2 selectedPackages=2 mode=HIDE_SELECTED
        """.trimIndent()
        val report = EntryRuntimeAudit.report(state, logs)
        assertTrue(report.contains("kind=ASSISTANT"))
        assertTrue(report.contains("status=FILTER_OBSERVED"))
        assertTrue(report.contains("filterObserved=1"))
    }

    @Test fun knownHomeRoleGapIsReported() {
        val item = candidate(IntentKind.HOME)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val report = EntryRuntimeAudit.report(state, "")
        val line = report.lineSequence().first { it.startsWith("kind=HOME ") }
        assertTrue(line.contains("COVERAGE_GAP_ROLE_CONTROLLER"))
    }

    @Test fun selectingOnlyShortcutCandidateReportsEmptyGuardRisk() {
        val item = candidate(IntentKind.SHORTCUT_ITEM)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val report = EntryRuntimeAudit.report(state, "")
        val line = report.lineSequence().first { it.startsWith("kind=SHORTCUT_ITEM ") }
        assertTrue(line.contains("EMPTY_RESULT_GUARD_MAY_RESTORE"))
    }

    @Test fun restoreAllLogOverridesApparentlyHealthyHookState() {
        val item = candidate(IntentKind.SHORTCUT_ITEM)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = "I/ListCleaner.ShortcutSurface: pid=44 process=system RESTORE_ALL_SHORTCUTS callerUid=10001 before=1"
        val report = EntryRuntimeAudit.report(state, logs)
        val line = report.lineSequence().first { it.startsWith("kind=SHORTCUT_ITEM ") }
        assertTrue(line.contains("status=EMPTY_RESULT_RESTORED"))
        assertTrue(line.contains("RESTORE_ALL_SEEN_IN_LOG"))
    }
}
