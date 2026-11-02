package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAccessibilityNavigationProbeTest {
    @Test fun onlySettingsSubpagesQualifyForReadOnlyCapture() {
        assertTrue(SettingsAccessibilityNavigationProbe.isSubSettings(
            "com.android.settings.SubSettings"))
        assertTrue(SettingsAccessibilityNavigationProbe.isSubSettings(
            "com.android.settings.Settings.SubSettings"))
        assertFalse(SettingsAccessibilityNavigationProbe.isSubSettings(
            "com.android.settings.Settings"))
        assertFalse(SettingsAccessibilityNavigationProbe.isSubSettings(
            "com.example.SubSettings"))
    }

    @Test fun classNamesAndExtraKeysAreConstrainedToNonSensitiveMetadata() {
        assertEquals("com.android.settings.accessibility.AccessibilitySettings",
            SettingsAccessibilityNavigationProbe.safeClassName(
                "com.android.settings.accessibility.AccessibilitySettings"))
        assertNull(SettingsAccessibilityNavigationProbe.safeClassName(
            "foo; rm -rf /"))
        assertEquals(
            ":settings:show_fragment,:settings:show_fragment_args,preference_key",
            SettingsAccessibilityNavigationProbe.safeExtraKeys(
                listOf(":settings:show_fragment_args", "preference_key",
                    ":settings:show_fragment", ":settings:show_fragment", "extra key=secret")
            )
        )
    }
}
