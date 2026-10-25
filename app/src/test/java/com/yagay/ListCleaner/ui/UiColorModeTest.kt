package com.yagay.ListCleaner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiColorModeTest {
    @Test fun missingOrUnknownPreferenceDefaultsToSystem() {
        assertSame(UiColorMode.SYSTEM, UiColorMode.fromStored(null))
        assertSame(UiColorMode.SYSTEM, UiColorMode.fromStored("UNKNOWN"))
    }

    @Test fun selectedModeOverridesSystemOnlyWhenForced() {
        assertFalse(UiColorMode.SYSTEM.usesDarkTheme(false))
        assertTrue(UiColorMode.SYSTEM.usesDarkTheme(true))
        assertFalse(UiColorMode.LIGHT.usesDarkTheme(true))
        assertTrue(UiColorMode.DARK.usesDarkTheme(false))
    }

    @Test fun savedModesRoundTrip() {
        UiColorMode.entries.forEach {
            assertSame(it, UiColorMode.fromStored(it.name))
        }
    }
}
