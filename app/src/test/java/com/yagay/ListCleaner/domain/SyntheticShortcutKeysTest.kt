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
    fun all_role_package_identities_are_stable() {
        assertEquals("@assistant", SyntheticEntryKeys.packageScopedClass(IntentKind.ASSISTANT))
        assertEquals("@home", SyntheticEntryKeys.packageScopedClass(IntentKind.HOME))
        assertEquals("@browser", SyntheticEntryKeys.packageScopedClass(IntentKind.BROWSER))
        assertEquals("@call_screening", SyntheticEntryKeys.packageScopedClass(IntentKind.CALL_SCREENING))
    }

    @Test
    fun historical_package_scoped_components_collapse_by_package() {
        listOf(
            IntentKind.ASSISTANT,
            IntentKind.HOME,
            IntentKind.BROWSER,
            IntentKind.CALL_SCREENING,
        ).forEach { kind ->
            val first = ComponentRule(kind, "com.example.target", "com.example.First")
            val second = ComponentRule(kind, "com.example.target", "com.example.Second")
            assertEquals(
                SyntheticEntryKeys.normalizePackageScopedRule(first),
                SyntheticEntryKeys.normalizePackageScopedRule(second),
            )
        }
    }

    @Test
    fun different_assistant_packages_remain_distinct() {
        assertNotEquals(
            SyntheticEntryKeys.assistantPackageRule("com.example.one"),
            SyntheticEntryKeys.assistantPackageRule("com.example.two"),
        )
    }
}
