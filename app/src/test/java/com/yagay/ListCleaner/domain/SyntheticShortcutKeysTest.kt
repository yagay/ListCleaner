package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyntheticShortcutKeysTest {
    @Test
    fun shortcut_item_key_is_stable() {
        val first = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")
        val second = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")

        assertEquals(first, second)
    }

    @Test
    fun different_shortcut_ids_are_distinct() {
        val scan = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")
        val chat = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "new_chat")

        assertNotEquals(scan, chat)
    }

    @Test
    fun same_shortcut_id_on_different_activities_is_distinct() {
        val first = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")
        val second = SyntheticEntryKeys.shortcutItemClass("com.example.SecondActivity", "scan")

        assertNotEquals(first, second)
    }

    @Test
    fun assistant_identity_is_stable_per_package() {
        val expected = ComponentRule(IntentKind.ASSISTANT, "com.example.assistant", "@assistant")
        assertEquals(expected, SyntheticEntryKeys.assistantPackageRule("com.example.assistant"))
        assertEquals("ASSISTANT|com.example.assistant|@assistant", expected.id)
    }

    @Test
    fun historical_assistant_components_collapse_to_package_identity() {
        val activity = ComponentRule(IntentKind.ASSISTANT, "com.example.assistant", "com.example.AssistActivity")
        val service = ComponentRule(IntentKind.ASSISTANT, "com.example.assistant", "com.example.VoiceService")

        assertEquals(
            SyntheticEntryKeys.normalizePackageScopedRule(activity),
            SyntheticEntryKeys.normalizePackageScopedRule(service),
        )
    }

    @Test
    fun different_assistant_packages_remain_distinct() {
        assertNotEquals(
            SyntheticEntryKeys.assistantPackageRule("com.example.one"),
            SyntheticEntryKeys.assistantPackageRule("com.example.two"),
        )
    }
}
