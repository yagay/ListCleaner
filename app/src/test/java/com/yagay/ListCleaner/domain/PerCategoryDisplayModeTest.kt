package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerCategoryDisplayModeTest {
    @Test fun overridesInheritGlobalModeAndSurviveProfileConversions() {
        val config = ModuleConfig(
            rules = emptySet(), mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(), diagnostic = false,
            modeOverrides = mapOf(IntentKind.OPEN to DisplayMode.SHOW_SELECTED),
        ).validated()
        assertEquals(DisplayMode.SHOW_SELECTED, config.modeFor(IntentKind.OPEN))
        assertEquals(DisplayMode.HIDE_SELECTED, config.modeFor(IntentKind.SHARE))
        val profile = config.toSpaceRules()
        assertEquals(config.modeOverrides, profile.toModuleConfig().modeOverrides)
        assertEquals(config.modeOverrides, profile.validated().modeOverrides)
    }

    @Test fun legacyRulesHaveNoImplicitCategoryOverrides() {
        val legacy = UserSpaceRules()
        assertTrue(legacy.modeOverrides.isEmpty())
        assertEquals(DisplayMode.HIDE_SELECTED,
            legacy.toModuleConfig().modeFor(IntentKind.OPEN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun retiredEntryKindsCannotBeConfigured() {
        ModuleConfig(
            rules = emptySet(), mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(), diagnostic = false,
            modeOverrides = mapOf(IntentKind.LAUNCHER_SHORTCUT to DisplayMode.SHOW_SELECTED),
        ).validated()
    }
}
