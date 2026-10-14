package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookDiagnosticRateLimiterTest {
    @Test fun limitsHotMessagesButNeverHidesFailures() {
        var now = 1000L
        val limiter = HookDiagnosticRateLimiter(2, 5000L) { now }
        assertTrue(limiter.allow("QUERY kind=OPEN"))
        assertTrue(limiter.allow("RESULT kind=OPEN"))
        assertFalse(limiter.allow("QUERY kind=OPEN"))
        assertTrue(limiter.allow("HOOK_FAILED method=test"))
        now = 6000L
        assertTrue(limiter.allow("QUERY kind=OPEN"))
    }

    @Test fun limitsManagerObservationMessages() {
        val limiter = HookDiagnosticRateLimiter(1, 5000L) { 500L }
        assertTrue(limiter.allow("AUTHORITY_OBSERVED kind=HOME"))
        assertFalse(limiter.allow("MANAGER_HIT kind=HOME"))
    }
}
