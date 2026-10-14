package com.yagay.ListCleaner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentScanRefreshPolicyTest {
    private fun needsRefresh(now: Long, success: Long, attempt: Long, busy: Boolean = false) =
        ComponentScanRefreshPolicy.shouldRefresh(now, success, attempt, busy)

    @Test fun firstComponentViewScansOnDemand() {
        assertTrue(needsRefresh(now = 120_000L, success = -1L, attempt = -1L))
    }

    @Test fun repeatedViewsUseTheLastSnapshot() {
        assertFalse(needsRefresh(now = 300_000L, success = 240_000L, attempt = 240_000L))
        assertFalse(needsRefresh(now = 240_000L + ComponentScanRefreshPolicy.FRESHNESS_MS - 1L,
            success = 240_000L, attempt = 240_000L))
    }

    @Test fun staleSnapshotRefreshesOnNextVisit() {
        val last = 120_000L
        assertTrue(needsRefresh(
            now = last + ComponentScanRefreshPolicy.FRESHNESS_MS,
            success = last,
            attempt = last
        ))
    }

    @Test fun concurrentAttemptIsNotEnqueuedAgain() {
        assertFalse(needsRefresh(now = 120_000L, success = -1L, attempt = -1L, busy = true))
    }

    @Test fun unsuccessfulAttemptWaitsBeforeRetrying() {
        val lastAttempt = 100_000L
        assertFalse(needsRefresh(now = lastAttempt + 1000L, success = -1L, attempt = lastAttempt))
        assertTrue(needsRefresh(
            now = lastAttempt + ComponentScanRefreshPolicy.RETRY_AFTER_FAILURE_MS,
            success = -1L, attempt = lastAttempt
        ))
    }

    @Test fun elapsedClockResetDoesNotUseOldCache() {
        assertTrue(needsRefresh(now = 500L, success = 100_000L, attempt = 100_000L))
    }
}
