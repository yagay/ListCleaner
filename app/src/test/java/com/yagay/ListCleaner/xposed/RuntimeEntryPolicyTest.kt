package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEntryPolicyTest {
    @Test
    fun runtimeSnapshotPublishesOnlySpecialEntryRulesAndPriorities() {
        val shortcut = ComponentRule(
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

        RuntimeRuleSnapshot(
            configured = setOf(shortcut.id, provider.id, ordinary.id),
            displayMode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(
                apps = mapOf(
                    IntentKind.LAUNCHER_SHORTCUT to listOf("com.example.one"),
                    IntentKind.DOCUMENT_PROVIDER to listOf("com.example.drive"),
                    IntentKind.SHARE to listOf("com.example.share"),
                )
            ),
            openTypes = OpenTypeConfig(),
            browserLinks = BrowserLinkConfig(),
            diagnostic = false,
        )

        val policy = RuntimeComponentPolicy.snapshot()
        assertEquals(DisplayMode.HIDE_SELECTED, policy.displayMode)
        assertEquals(setOf(shortcut.id, provider.id), policy.entryRules)
        assertEquals(listOf("com.example.one"), policy.entryPriorities[IntentKind.LAUNCHER_SHORTCUT])
        assertEquals(listOf("com.example.drive"), policy.entryPriorities[IntentKind.DOCUMENT_PROVIDER])
        assertTrue(IntentKind.SHARE !in policy.entryPriorities)
    }
}
