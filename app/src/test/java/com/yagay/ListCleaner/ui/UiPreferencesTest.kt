package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreferencesTest {
    @Test fun oldNavigationModesBothResolveToListFirst() {
        assertEquals(UiHomeLayout.CLASSIC_LIST, UiPreferences().homeLayout)
        assertEquals(UiHomeLayout.CLASSIC_LIST,
            UiPreferencesStore.upgradedListLayout(UiHomeLayout.CATEGORY_CARDS, false))
        assertEquals(UiHomeLayout.CLASSIC_LIST,
            UiPreferencesStore.upgradedListLayout(UiHomeLayout.CATEGORY_CARDS, true))
        assertEquals(UiHomeLayout.CLASSIC_LIST,
            UiPreferencesStore.upgradedListLayout(UiHomeLayout.CLASSIC_LIST, false))
    }

    @Test fun oldGroupedLayoutMigratesToSingleContinuousList() {
        val preferences = UiPreferences(listStyle = UiListStyle.GROUPED).normalized()
        assertEquals(UiListStyle.ORIGINAL, preferences.listStyle)
        assertEquals(UiListStyle.ORIGINAL, UiPreferences().normalized().listStyle)
    }

    @Test fun geometryIsBoundedBeforePersistence() {
        val preferences = UiPreferences(
            cardRadius = -4, buttonHeight = 4, appIconSize = 999,
            listRowHeight = 12, scanCacheMinutes = 1000, homeColumns = 100
        ).normalized()
        assertEquals(0, preferences.cardRadius)
        assertEquals(36, preferences.buttonHeight)
        assertEquals(56, preferences.appIconSize)
        assertEquals(48, preferences.listRowHeight)
        assertEquals(60, preferences.scanCacheMinutes)
        assertEquals(2, preferences.homeColumns)
    }

    @Test fun extendedDisplayAndRetrySettingsAreClamped() {
        val preferences = UiPreferences(
            fontScalePercent = 300,
            appTitleLines = -1,
            appSubtitleLines = 20,
            scanRetrySeconds = 500,
            layoutSpacingPercent = 10
        ).normalized()
        assertEquals(140, preferences.fontScalePercent)
        assertEquals(1, preferences.appTitleLines)
        assertEquals(3, preferences.appSubtitleLines)
        assertEquals(180, preferences.scanRetrySeconds)
        assertEquals(70, preferences.layoutSpacingPercent)
        assertEquals(150, UiPreferences(layoutSpacingPercent = 500).normalized().layoutSpacingPercent)
    }

    @Test fun configurableFailureCooldownDoesNotChangeManualScanSafety() {
        assertFalse(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 35_000L,
            lastFullScanElapsedMs = -1L,
            lastAttemptElapsedMs = 20_000L,
            busy = false,
            retryAfterFailureMs = 20_000L
        ))
        assertTrue(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 41_000L,
            lastFullScanElapsedMs = -1L,
            lastAttemptElapsedMs = 20_000L,
            busy = false,
            retryAfterFailureMs = 20_000L
        ))
    }

    @Test fun openingPageMapsToExistingDestinations() {
        assertEquals(Destination.RULES, UiStartPage.CLEANUP.destination())
        assertEquals(Destination.PRIORITY, UiStartPage.ORDERING.destination())
        assertEquals(Destination.SETTINGS, UiStartPage.SETTINGS.destination())
    }

    @Test fun proactiveScanSettingsHaveSafeDefaultsAndBoundedCache() {
        val defaults = UiPreferences()
        assertEquals(UiProactiveScanMode.ON_SCAN, defaults.proactiveScanMode)
        assertTrue(defaults.proactiveDirectShare)
        assertFalse(defaults.proactiveShortcutItems)
        assertEquals(1, UiPreferences(proactiveCacheMinutes = -5).normalized().proactiveCacheMinutes)
        assertEquals(60, UiPreferences(proactiveCacheMinutes = 1000).normalized().proactiveCacheMinutes)
    }

    @Test fun newPreferencesDefaultToNonDestructiveAndPrivacyPreserving() {
        val value = UiPreferences()
        assertEquals(UiCandidateSource.ALL, value.candidateSource)
        assertTrue(value.showShortcutHistory)
        assertEquals(0, value.historyVisibilityDays)
        assertEquals(UiPreviewDefault.CURRENT, value.previewDefault)
        assertEquals(UiProbeDefaultScenario.SYSTEM, value.probeDefaultScenario)
        assertEquals(UiDiagnosticsLevel.SUMMARY, value.diagnosticsLevel)
        assertEquals(UiComponentRefresh.CHANGED_ONLY, value.componentRefresh)
        assertEquals(0, UiPreferences(historyVisibilityDays = -1).normalized().historyVisibilityDays)
        assertEquals(90, UiPreferences(historyVisibilityDays = 90).normalized().historyVisibilityDays)
        assertEquals(0, UiPreferences(historyVisibilityDays = 3).normalized().historyVisibilityDays)
    }

    @Test fun manualScanningDoesNotStartEvenWhenCacheMissing() {
        assertFalse(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 100_000L, lastFullScanElapsedMs = -1L,
            lastAttemptElapsedMs = -1L, busy = false, scanMode = UiScanMode.MANUAL
        ))
    }

    @Test fun customFreshnessAndEveryEntryRespectRetryCooldown() {
        assertFalse(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 200_000L, lastFullScanElapsedMs = 170_000L,
            lastAttemptElapsedMs = 170_000L, busy = false,
            freshnessMs = 60_000L
        ))
        assertTrue(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 220_000L, lastFullScanElapsedMs = 170_000L,
            lastAttemptElapsedMs = 170_000L, busy = false,
            scanMode = UiScanMode.EVERY_ENTRY
        ))
        assertFalse(ComponentScanRefreshPolicy.shouldRefresh(
            nowElapsedMs = 190_000L, lastFullScanElapsedMs = 170_000L,
            lastAttemptElapsedMs = 170_000L, busy = false,
            scanMode = UiScanMode.EVERY_ENTRY
        ))
    }
}
