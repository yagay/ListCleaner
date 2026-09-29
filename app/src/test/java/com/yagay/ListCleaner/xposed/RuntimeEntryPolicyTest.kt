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

class RuntimeEntryPolicyTest {
    @Test
    fun runtimeSnapshotPublishesAllSelectableRulesAndPrioritiesAtomically() {
        val shortcut = ComponentRule(
            IntentKind.SHORTCUT_ITEM,
            "com.example.one",
            "com.example.one.MainActivity#shortcut#scan",
        )
        val legacyShortcutSurface = ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            "com.example.one",
            "com.example.one.MainActivity",
        )
        val provider = ComponentRule(
            IntentKind.DOCUMENT_PROVIDER,
            "com.example.drive",
            "com.example.drive.DocumentsProvider",
        )
        val ordinary = ComponentRule(
            IntentKind.SHARE,
            "com.example.share",
            "com.example.share.ShareActivity",
        )
        val assistant = ComponentRule(
            IntentKind.ASSISTANT,
            "com.example.assistant",
            "com.example.assistant.AssistActivity",
        )

        RuntimeRuleSnapshot(
            configured = setOf(
                shortcut.id,
                legacyShortcutSurface.id,
                provider.id,
                ordinary.id,
                assistant.id,
            ),
            displayMode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(
                apps = mapOf(
                    IntentKind.SHORTCUT_ITEM to listOf("com.example.one"),
                    IntentKind.LAUNCHER_SHORTCUT to listOf("com.example.legacy"),
                    IntentKind.DOCUMENT_PROVIDER to listOf("com.example.drive"),
                    IntentKind.SHARE to listOf("com.example.share"),
                    IntentKind.ASSISTANT to listOf("com.example.assistant"),
                )
            ),
            openTypes = OpenTypeConfig(),
            browserLinks = BrowserLinkConfig(),
            diagnostic = false,
        )

        RuntimeComponentPolicy.publish(
            managerAppId = 12345,
            protectedComponents = setOf("0|com.example|com.example.Protected"),
            digest = "digest-a",
        )

        val policy = RuntimeComponentPolicy.snapshot()
        assertTrue(policy.authoritative)
        assertEquals(12345, policy.managerAppId)
        assertEquals("digest-a", policy.digest)
        assertEquals(DisplayMode.HIDE_SELECTED, policy.displayMode)
        assertEquals(setOf(shortcut.id, provider.id, ordinary.id, assistant.id), policy.entryRules)
        assertEquals(setOf(assistant.id), policy.selected(IntentKind.ASSISTANT))
        assertEquals(listOf("com.example.one"), policy.entryPriorities[IntentKind.SHORTCUT_ITEM])
        assertEquals(listOf("com.example.drive"), policy.entryPriorities[IntentKind.DOCUMENT_PROVIDER])
        assertEquals(listOf("com.example.share"), policy.entryPriorities[IntentKind.SHARE])
        assertEquals(listOf("com.example.assistant"), policy.entryPriorities[IntentKind.ASSISTANT])
        assertTrue(IntentKind.LAUNCHER_SHORTCUT !in policy.entryPriorities)
    }

    @Test
    fun stagedEntryRulesDoNotPartiallyReplaceAuthoritativeSnapshot() {
        RuntimeComponentPolicy.publishEntryRules(
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = emptySet(),
            entryPriorities = emptyMap(),
        )
        RuntimeComponentPolicy.publish(10001, emptySet(), "baseline")
        val baseline = RuntimeComponentPolicy.snapshot()

        RuntimeComponentPolicy.publishEntryRules(
            displayMode = DisplayMode.SHOW_SELECTED,
            entryRules = setOf("DIRECT_SHARE|com.example|com.example.Target"),
            entryPriorities = mapOf(IntentKind.DIRECT_SHARE to listOf("com.example")),
        )

        val staged = RuntimeComponentPolicy.snapshot()
        assertEquals(baseline, staged)
        assertFalse(staged.displayMode == DisplayMode.SHOW_SELECTED)

        RuntimeComponentPolicy.publish(10002, setOf("0|pkg|pkg.Component"), "next")
        val committed = RuntimeComponentPolicy.snapshot()
        assertEquals(DisplayMode.SHOW_SELECTED, committed.displayMode)
        assertEquals(10002, committed.managerAppId)
        assertEquals("next", committed.digest)
        assertEquals(listOf("com.example"), committed.entryPriorities[IntentKind.DIRECT_SHARE])
    }
}
