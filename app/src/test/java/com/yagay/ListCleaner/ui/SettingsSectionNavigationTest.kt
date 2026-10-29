package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The original six categories keep their order; About is a separate final entry. */
class SettingsSectionNavigationTest {
    @Test fun originalSettingsGroupsRemainStableWithAboutAtEnd() {
        assertEquals(
            listOf(
                SettingsSection.APPEARANCE, SettingsSection.LISTS,
                SettingsSection.SCANNING, SettingsSection.COMPATIBILITY,
                SettingsSection.BACKUP, SettingsSection.ADVANCED,
                SettingsSection.SURFACES, SettingsSection.ABOUT
            ),
            SettingsSection.entries
        )
        assertEquals(4, SettingsSection.entries.filter { it != SettingsSection.ABOUT }.chunked(2).size)
        assertEquals(SettingsSection.ABOUT, SettingsSection.entries.last())
        assertTrue(SettingsSection.entries.all { it.titleRes != 0 && it.descriptionRes != 0 })
        assertEquals(8, SettingsSection.entries.map { it.titleRes }.distinct().size)
    }
}
