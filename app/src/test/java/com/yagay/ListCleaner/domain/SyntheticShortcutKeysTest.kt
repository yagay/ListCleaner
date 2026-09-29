package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticShortcutKeysTest {
    @Test
    fun app_level_shortcut_key_is_stable_and_package_scoped_by_rule() {
        val first = SyntheticEntryKeys.launcherShortcutAppClass()
        val second = SyntheticEntryKeys.launcherShortcutAppClass()

        assertEquals(first, second)
        assertTrue(first.endsWith("#shortcut-app"))
    }

    @Test
    fun app_level_and_item_level_keys_are_distinct() {
        val app = SyntheticEntryKeys.launcherShortcutAppClass()
        val item = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")

        assertNotEquals(app, item)
    }

    @Test
    fun component_rule_package_keeps_app_level_keys_separate() {
        val first = ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            "com.example.one",
            SyntheticEntryKeys.launcherShortcutAppClass(),
        )
        val second = ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            "com.example.two",
            SyntheticEntryKeys.launcherShortcutAppClass(),
        )

        assertNotEquals(first.id, second.id)
    }
}
