package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticShortcutKeysTest {
    @Test
    fun app_level_shortcut_key_is_stable_for_activity() {
        val first = SyntheticEntryKeys.launcherShortcutAppClass("com.example.MainActivity")
        val second = SyntheticEntryKeys.launcherShortcutAppClass("com.example.MainActivity")

        assertEquals(first, second)
        assertTrue(first.endsWith("#shortcut-app"))
    }

    @Test
    fun app_level_and_item_level_keys_are_distinct() {
        val app = SyntheticEntryKeys.launcherShortcutAppClass("com.example.MainActivity")
        val item = SyntheticEntryKeys.shortcutItemClass("com.example.MainActivity", "scan")

        assertNotEquals(app, item)
    }

    @Test
    fun different_launcher_activities_remain_separate_app_surfaces() {
        val first = SyntheticEntryKeys.launcherShortcutAppClass("com.example.MainActivity")
        val second = SyntheticEntryKeys.launcherShortcutAppClass("com.example.SecondActivity")

        assertNotEquals(first, second)
    }
}
