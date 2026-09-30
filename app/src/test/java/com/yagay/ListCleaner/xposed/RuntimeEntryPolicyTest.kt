package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PriorityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEntryPolicyTest {
    @Test
    fun runtimeCompilerPublishesAllSelectableRulesAndPrioritiesAtomically() {
        val shortcut = ComponentRule(IntentKind.SHORTCUT_ITEM, "com.example.one", "Shortcut#scan")
        val provider = ComponentRule(IntentKind.DOCUMENT_PROVIDER, "com.example.drive", "DocumentsProvider")
        val ordinary = ComponentRule(IntentKind.SHARE, "com.example.share", "ShareActivity")
        val assistant = ComponentRule(IntentKind.ASSISTANT, "com.example.assistant", "AssistActivity")

        val compiled = RuntimePolicyCompiler.compile(
            managerAppId = 12345,
            protectedComponents = setOf("0|com.example|com.example.Protected"),
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = setOf(shortcut.id, provider.id, ordinary.id, assistant.id),
            entryPriorities = PriorityConfig(
                apps = mapOf(
                    IntentKind.SHORTCUT_ITEM to listOf("com.example.one"),
                    IntentKind.DOCUMENT_PROVIDER to listOf("com.example.drive"),
                    IntentKind.SHARE to listOf("com.example.share"),
                    IntentKind.ASSISTANT to listOf("com.example.assistant"),
                )
            ).apps,
            digest = "digest-a",
        )
        RuntimeComponentPolicy.publish(compiled)

        val policy = RuntimeComponentPolicy.snapshot()
        assertTrue(policy.authoritative)
        assertEquals(12345, policy.managerAppId)
        assertEquals("digest-a", policy.digest)
        assertEquals(DisplayMode.HIDE_SELECTED, policy.displayMode)
        assertEquals(setOf(shortcut.id, provider.id, ordinary.id, assistant.id), policy.entryRules)
        assertEquals(setOf(assistant.id), policy.selected(IntentKind.ASSISTANT))
        assertEquals(setOf("com.example.assistant"), policy.selectedPackages(IntentKind.ASSISTANT))
        assertEquals(listOf("com.example.one"), policy.entryPriorities[IntentKind.SHORTCUT_ITEM])
        assertEquals(listOf("com.example.drive"), policy.entryPriorities[IntentKind.DOCUMENT_PROVIDER])
        assertEquals(listOf("com.example.share"), policy.entryPriorities[IntentKind.SHARE])
        assertEquals(listOf("com.example.assistant"), policy.entryPriorities[IntentKind.ASSISTANT])
    }

    @Test
    fun compilingNextPolicyDoesNotPartiallyReplaceCurrentSnapshot() {
        val baseline = RuntimePolicyCompiler.compile(
            managerAppId = 10001,
            protectedComponents = emptySet(),
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = emptySet(),
            entryPriorities = emptyMap(),
            digest = "baseline",
        )
        RuntimeComponentPolicy.publish(baseline)

        val next = RuntimePolicyCompiler.compile(
            managerAppId = 10002,
            protectedComponents = setOf("0|pkg|pkg.Component"),
            displayMode = DisplayMode.SHOW_SELECTED,
            entryRules = setOf("DIRECT_SHARE|com.example|com.example.Target"),
            entryPriorities = mapOf(IntentKind.DIRECT_SHARE to listOf("com.example")),
            digest = "next",
        )

        assertEquals(baseline, RuntimeComponentPolicy.snapshot())
        assertFalse(RuntimeComponentPolicy.snapshot().displayMode == DisplayMode.SHOW_SELECTED)

        RuntimeComponentPolicy.publish(next)
        val committed = RuntimeComponentPolicy.snapshot()
        assertEquals(DisplayMode.SHOW_SELECTED, committed.displayMode)
        assertEquals(10002, committed.managerAppId)
        assertEquals("next", committed.digest)
        assertEquals(listOf("com.example"), committed.entryPriorities[IntentKind.DIRECT_SHARE])
    }
}
