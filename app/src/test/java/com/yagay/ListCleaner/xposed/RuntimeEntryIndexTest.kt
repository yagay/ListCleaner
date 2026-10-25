package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEntryIndexTest {
    @Test
    fun `compiled entry rules are indexed once by kind and package`() {
        val shortcut = ComponentRule(IntentKind.SHORTCUT_ITEM, "com.example.one", "Shortcut#1")
        val provider = ComponentRule(IntentKind.DOCUMENT_PROVIDER, "com.example.two", "Provider")
        val policy = RuntimePolicyCompiler.compile(
            managerAppId = 12345,
            protectedComponents = emptySet(),
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = setOf(shortcut.id, provider.id),
            entryPriorities = mapOf(IntentKind.SHORTCUT_ITEM to listOf("com.example.one")),
            digest = "digest-index-test",
        )

        assertEquals(setOf(shortcut.id), policy.selected(IntentKind.SHORTCUT_ITEM))
        assertEquals(setOf(provider.id), policy.selected(IntentKind.DOCUMENT_PROVIDER))
        assertEquals(setOf("com.example.one"), policy.selectedPackages(IntentKind.SHORTCUT_ITEM))
        assertEquals(setOf("com.example.two"), policy.selectedPackages(IntentKind.DOCUMENT_PROVIDER))
        assertEquals(listOf("com.example.one"), policy.priorities(IntentKind.SHORTCUT_ITEM))
        assertTrue(policy.selected(IntentKind.VPN).isEmpty())
    }

    @Test
    fun `fallback index silently drops retired or invalid ids`() {
        val valid = ComponentRule(IntentKind.DIRECT_SHARE, "com.example.share", "Direct#1")
        val policy = fallbackRuntimePolicy(
            managerAppId = 10001,
            displayMode = DisplayMode.SHOW_SELECTED,
            entryRules = setOf(valid.id, "LAUNCHER_SHORTCUT|com.example.old|Old", "broken"),
            entryPriorities = mapOf(IntentKind.DIRECT_SHARE to listOf("com.example.share")),
        )

        assertEquals(setOf(valid.id), policy.selected(IntentKind.DIRECT_SHARE))
        assertFalse("LAUNCHER_SHORTCUT|com.example.old|Old" in policy.entryRules)
        assertEquals(10001, policy.managerAppId)
    }

    @Test
    fun `compiler deduplicates priorities without mixing entry kinds`() {
        val policy = fallbackRuntimePolicy(
            managerAppId = 10002,
            displayMode = DisplayMode.HIDE_SELECTED,
            entryRules = emptySet(),
            entryPriorities = mapOf(
                IntentKind.SHORTCUT_ITEM to listOf("com.example.shortcut", "com.example.shortcut"),
                IntentKind.DIRECT_SHARE to listOf("com.example.direct"),
            ),
        )

        assertEquals(listOf("com.example.shortcut"), policy.priorities(IntentKind.SHORTCUT_ITEM))
        assertEquals(listOf("com.example.direct"), policy.priorities(IntentKind.DIRECT_SHARE))
        assertTrue(policy.priorities(IntentKind.DOCUMENT_PROVIDER).isEmpty())
    }
}
