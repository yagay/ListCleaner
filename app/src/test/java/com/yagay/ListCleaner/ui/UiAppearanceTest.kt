package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiAppearanceTest {
    @Test fun missingOrUnknownPreferenceUsesMaterial() {
        assertEquals(UiThemeStyle.MATERIAL, UiThemeStyle.fromStored(null))
        assertEquals(UiThemeStyle.MATERIAL, UiThemeStyle.fromStored(""))
        assertEquals(UiThemeStyle.MATERIAL, UiThemeStyle.fromStored("OUTDATED"))
    }

    @Test fun themePreferenceRoundTripsByStableKey() {
        UiThemeStyle.entries.forEach { style ->
            assertEquals(style, UiThemeStyle.fromStored(style.name))
        }
    }
}
