package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreferencesTest {
    @Test fun geometryIsBoundedBeforePersistence() {
        val preferences = UiPreferences(
            cardRadius = -4, buttonHeight = 4, appIconSize = 999,
            listRowHeight = 12, scanCacheMinutes = 1000
        ).normalized()
        assertEquals(0, preferences.cardRadius)
        assertEquals(36, preferences.buttonHeight)
        assertEquals(56, preferences.appIconSize)
        assertEquals(48, preferences.listRowHeight)
        assertEquals(60, preferences.scanCacheMinutes)
    }

    @Test fun openingPageMapsToExistingDestinations() {
        assertEquals(Destination.RULES, UiStartPage.CLEANUP.destination())
        assertEquals(Destination.PRIORITY, UiStartPage.ORDERING.destination())
        assertEquals(Destination.SETTINGS, UiStartPage.SETTINGS.destination())
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
