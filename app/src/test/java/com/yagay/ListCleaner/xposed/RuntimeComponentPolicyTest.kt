package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeComponentPolicyTest {
    @Test fun verifiedSnapshotCanRepresentAnAuthoritativeEmptyPolicy() {
        RuntimeComponentPolicy.publish(10715, emptySet(), "a".repeat(64))
        val snapshot = RuntimeComponentPolicy.snapshot()
        assertTrue(snapshot.authoritative)
        assertEquals(10715, snapshot.managerAppId)
        assertTrue(snapshot.protectedComponents.isEmpty())
    }

    @Test fun emptyDigestIsNotRuntimeAuthoritative() {
        RuntimeComponentPolicy.publish(10715, setOf("0|com.example|com.example.Tile"), "")
        assertFalse(RuntimeComponentPolicy.snapshot().authoritative)
    }

    @Test fun authoritativePolicyKeepsAssistantRulesForRoleHook() {
        val assistant = ComponentRule(
            IntentKind.ASSISTANT,
            "com.example.assistant",
            "com.example.assistant.AssistActivity"
        ).id
        RuntimeRuleSnapshot(
            configured = setOf(assistant),
            displayMode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(),
            openTypes = OpenTypeConfig(),
            browserLinks = BrowserLinkConfig(),
            diagnostic = false,
        )
        RuntimeComponentPolicy.publish(10715, emptySet(), "b".repeat(64))

        val snapshot = RuntimeComponentPolicy.snapshot()
        assertTrue(snapshot.authoritative)
        assertEquals(setOf(assistant), snapshot.selected(IntentKind.ASSISTANT))
    }
}
