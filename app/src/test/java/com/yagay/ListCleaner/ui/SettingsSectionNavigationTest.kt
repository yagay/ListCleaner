package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings categories preserve their old stable identifiers across the UI refactor. */
class SettingsSectionNavigationTest {
    @Test fun sixSettingsSectionsRemainInThreeStableGroups() {
        assertEquals(
            listOf(
                SettingsSection.APPEARANCE, SettingsSection.LISTS,
                SettingsSection.SCANNING, SettingsSection.COMPATIBILITY,
                SettingsSection.BACKUP, SettingsSection.ADVANCED
            ),
            SettingsSection.entries
        )
        assertEquals(3, SettingsSection.entries.chunked(2).size)
        assertTrue(SettingsSection.entries.all { it.titleRes != 0 && it.descriptionRes != 0 })
        assertEquals(6, SettingsSection.entries.map { it.titleRes }.distinct().size)
    }
}
