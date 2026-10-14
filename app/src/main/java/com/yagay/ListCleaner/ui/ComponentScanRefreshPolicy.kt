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

    fun shouldRefresh(
        nowElapsedMs: Long,
        lastFullScanElapsedMs: Long,
        lastAttemptElapsedMs: Long,
        busy: Boolean
    ): Boolean {
        if (busy) return false
        // Elapsed clocks may reset on reboot or test clocks; treat such readings as stale.
        if (lastFullScanElapsedMs >= 0 &&
            nowElapsedMs >= lastFullScanElapsedMs &&
            nowElapsedMs - lastFullScanElapsedMs < FRESHNESS_MS
        ) return false
        // After an unsuccessful scan, avoid hammering the PackageManager.
        if (lastAttemptElapsedMs >= 0 &&
            nowElapsedMs >= lastAttemptElapsedMs &&
            nowElapsedMs - lastAttemptElapsedMs < RETRY_AFTER_FAILURE_MS
        ) return false
        return true
    }
}
