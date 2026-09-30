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
    @Test
    fun verifiedSnapshotCanRepresentAnAuthoritativeEmptyPolicy() {
        RuntimeComponentPolicy.publish(
            RuntimePolicyCompiler.compile(
                managerAppId = 10715,
                protectedComponents = emptySet(),
                displayMode = DisplayMode.HIDE_SELECTED,
                entryRules = emptySet(),
                entryPriorities = emptyMap(),
                digest = "a".repeat(64),
            )
        )
        val snapshot = RuntimeComponentPolicy.snapshot()
        assertTrue(snapshot.authoritative)
        assertEquals(10715, snapshot.managerAppId)
        assertTrue(snapshot.protectedComponents.isEmpty())
    }

    @Test
    fun emptyDigestIsNotRuntimeAuthoritative() {
        RuntimeComponentPolicy.publish(
            RuntimePolicyCompiler.compile(
                managerAppId = 10715,
                protectedComponents = setOf("0|com.example|com.example.Tile"),
                displayMode = DisplayMode.HIDE_SELECTED,
                entryRules = emptySet(),
                entryPriorities = emptyMap(),
                digest = "",
            )
        )
        assertFalse(RuntimeComponentPolicy.snapshot().authoritative)
    }

    @Test
    fun compilerIndexesAssistantRulesAndPackagesForRoleHook() {
        val assistant = ComponentRule(
            IntentKind.ASSISTANT,
            "com.example.assistant",
            "com.example.assistant.AssistActivity"
        )
        val compiled = RuntimePolicyCompiler.compile(
            managerAppId = 10715,
            protectedComponents = emptySet(),
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = setOf(assistant.id),
            entryPriorities = emptyMap(),
            digest = "b".repeat(64),
        )
        RuntimeComponentPolicy.publish(compiled)

        val snapshot = RuntimeComponentPolicy.snapshot()
        assertTrue(snapshot.authoritative)
        assertEquals(setOf(assistant.id), snapshot.selected(IntentKind.ASSISTANT))
        assertEquals(setOf("com.example.assistant"), snapshot.selectedPackages(IntentKind.ASSISTANT))
    }

    @Test
    fun constructingResolverSnapshotHasNoGlobalPolicySideEffect() {
        val baseline = RuntimePolicyCompiler.compile(
            managerAppId = 10001,
            protectedComponents = emptySet(),
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = emptySet(),
            entryPriorities = emptyMap(),
            digest = "baseline",
        )
        RuntimeComponentPolicy.publish(baseline)

        RuntimeRuleSnapshot(
            configured = setOf("SHARE|com.example|com.example.Share"),
            displayMode = DisplayMode.SHOW_SELECTED,
            priorities = PriorityConfig(),
            openTypes = OpenTypeConfig(),
            browserLinks = BrowserLinkConfig(),
            diagnostic = false,
        )

        assertEquals(baseline, RuntimeComponentPolicy.snapshot())
    }
}
