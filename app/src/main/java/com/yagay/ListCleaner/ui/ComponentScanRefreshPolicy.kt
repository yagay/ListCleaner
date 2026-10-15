package com.yagay.ListCleaner.ui

/**
 * Automatic component-list refresh policy. Only the component editor asks for an automatic
 * scan; the Cleanup homepage displays any already-loaded snapshot without triggering work.
 *
 * Uses monotonic elapsed time, not wall-clock timestamps (which can change).
 * Explicit user refresh bypasses this policy.
 */
internal object ComponentScanRefreshPolicy {
    const val FRESHNESS_MS: Long = 10 * 60 * 1000L
    const val RETRY_AFTER_FAILURE_MS: Long = 30 * 1000L

    fun completedFullScanAt(
        nowElapsedMs: Long,
        warning: String,
        packageRevisionChanged: Boolean
    ): Long = if (warning.isBlank() && !packageRevisionChanged) nowElapsedMs else -1L

    fun shouldRefresh(
        nowElapsedMs: Long,
        lastFullScanElapsedMs: Long,
        lastAttemptElapsedMs: Long,
        busy: Boolean,
        packageRevisionChanged: Boolean = false,
        scanMode: UiScanMode = UiScanMode.SMART,
        freshnessMs: Long = FRESHNESS_MS
    ): Boolean {
        if (busy || scanMode == UiScanMode.MANUAL) return false
        val freshFor = if (scanMode == UiScanMode.EVERY_ENTRY) 0L else freshnessMs.coerceAtLeast(0L)
        // Elapsed clocks may reset on reboot or test clocks; treat such readings as stale.
        if (!packageRevisionChanged && lastFullScanElapsedMs >= 0 &&
            nowElapsedMs >= lastFullScanElapsedMs &&
            nowElapsedMs - lastFullScanElapsedMs < freshFor
        ) return false
        // After an unsuccessful scan, avoid hammering the PackageManager.
        if (lastAttemptElapsedMs >= 0 &&
            nowElapsedMs >= lastAttemptElapsedMs &&
            nowElapsedMs - lastAttemptElapsedMs < RETRY_AFTER_FAILURE_MS
        ) return false
        return true
    }
}
