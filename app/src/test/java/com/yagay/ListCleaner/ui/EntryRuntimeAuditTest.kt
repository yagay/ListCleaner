package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRuntimeAuditTest {
    private fun candidate(kind: IntentKind): ComponentCandidate = ComponentCandidate(
        rule = ComponentRule(kind, "com.example", "com.example.Target"),
        appLabel = "Example",
        activityLabel = "Target",
    )

    @Test fun roleControllerFilterEvidenceIsRecognized() {
        val item = candidate(IntentKind.ASSISTANT)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = """
            09-29 I/ListCleaner.RoleController: pid=123 process=com.android.permissioncontroller HOOK_INSTALLED package=com.android.permissioncontroller method=x
            09-29 I/ListCleaner.RoleController: pid=123 process=com.android.permissioncontroller FILTER role=android.app.role.ASSISTANT kind=ASSISTANT before=4 after=2 selectedPackages=2 mode=HIDE_SELECTED
        """.trimIndent()
        val report = EntryRuntimeAudit.report(state, logs)
        assertTrue(report.contains("kind=ASSISTANT"))
        assertTrue(report.contains("status=FILTER_OBSERVED"))
        assertTrue(report.contains("filterObserved=1"))
    }

    @Test fun resolverArrowFilterEvidenceIsRecognized() {
        val item = candidate(IntentKind.ASSISTANT)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = "I/ListCleaner.Diagnostic: pid=44 process=system SYSTEM ASSISTANT preset=null: 12 -> 2"
        val report = EntryRuntimeAudit.report(state, logs)
        val line = report.lineSequence().first { it.startsWith("kind=ASSISTANT ") }
        assertTrue(line.contains("status=FILTER_OBSERVED"))
        assertTrue(line.contains("filterObserved=1"))
    }

    @Test fun settingsVpnAuthorityEvidenceIsRecognized() {
        val item = candidate(IntentKind.VPN)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = """
            I/ListCleaner.SettingsAuthority: pid=55 process=com.android.settings HOOK_INSTALLED id=lc-settings-vpn-authority method=x
            I/ListCleaner.SettingsAuthority: pid=55 process=com.android.settings VPN_FILTER before=3 after=1 selectedPackages=2
        """.trimIndent()
        val line = EntryRuntimeAudit.report(state, logs)
            .lineSequence().first { it.startsWith("kind=VPN ") }
        assertTrue(line.contains("status=FILTER_OBSERVED"))
        assertTrue(line.contains("filterObserved=1"))
    }

    @Test fun moduleLoadedAloneIsNotTreatedAsHookReady() {
        val item = candidate(IntentKind.VPN)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = "I/ListCleaner.SettingsAuthority: pid=55 process=com.android.settings MODULE_LOADED"
        val line = EntryRuntimeAudit.report(state, logs)
            .lineSequence().first { it.startsWith("kind=VPN ") }
        assertTrue(line.contains("hookReady=0"))
        assertTrue(line.contains("NO_RUNTIME_EVIDENCE"))
    }

    @Test fun knownRolePathsNoLongerReportArchitectureGaps() {
        val item = candidate(IntentKind.HOME)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val report = EntryRuntimeAudit.report(state, "")
        val line = report.lineSequence().first { it.startsWith("kind=HOME ") }
        assertFalse(line.contains("COVERAGE_GAP_ROLE_CONTROLLER"))
    }

    @Test fun shortcutNoLongerReportsEmptyGuardRisk() {
        val item = candidate(IntentKind.SHORTCUT_ITEM)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val report = EntryRuntimeAudit.report(state, "")
        val line = report.lineSequence().first { it.startsWith("kind=SHORTCUT_ITEM ") }
        assertFalse(line.contains("EMPTY_RESULT_GUARD_MAY_RESTORE"))
    }

    @Test fun oldRestoreHistoryDoesNotOverrideNewFilterEvidence() {
        val item = candidate(IntentKind.SHORTCUT_ITEM)
        val state = MainState(candidates = listOf(item), selected = setOf(item.rule))
        val logs = """
            I/ListCleaner.ShortcutSurface: pid=44 process=system RESTORE_ALL_SHORTCUTS callerUid=10001 before=1
            I/ListCleaner.ShortcutSurface: pid=44 process=system SHORTCUT_FILTER callerUid=10001 before=1 after=0 selected=1
        """.trimIndent()
        val report = EntryRuntimeAudit.report(state, logs)
        val line = report.lineSequence().first { it.startsWith("kind=SHORTCUT_ITEM ") }
        assertTrue(line.contains("status=FILTER_OBSERVED_RESTORE_HISTORY"))
        assertTrue(line.contains("RESTORE_ALL_SEEN_IN_LOG"))
    }
}
