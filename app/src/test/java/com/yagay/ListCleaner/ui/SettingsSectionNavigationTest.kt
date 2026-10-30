package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cleanup actions live under the separate Lists destination, not in Settings. */
class SettingsSectionNavigationTest {
    @Test fun settingsRemainConfigurationOnlyWithAboutAtEnd() {
        assertEquals(
            listOf(
                SettingsSection.APPEARANCE, SettingsSection.LISTS,
                SettingsSection.SCANNING, SettingsSection.COMPATIBILITY,
                SettingsSection.BACKUP, SettingsSection.ADVANCED,
                SettingsSection.ABOUT
            ),
            SettingsSection.entries
        )
        assertEquals(3, SettingsSection.entries.filter { it != SettingsSection.ABOUT }.chunked(2).size)
        assertEquals(SettingsSection.ABOUT, SettingsSection.entries.last())
        assertTrue(SettingsSection.entries.all { it.titleRes != 0 && it.descriptionRes != 0 })
        assertEquals(7, SettingsSection.entries.map { it.titleRes }.distinct().size)
        assertTrue(SettingsSection.entries.none { it.name == "SURFACES" })
    }
}
