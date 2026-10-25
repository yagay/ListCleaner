package com.yagay.ListCleaner.xposed

/**
 * Bounds chatty per-query logs in hot system_server hooks without suppressing hook
 * installation, failures, or other diagnostic events. One limiter belongs to one module.
 */
internal class HookDiagnosticRateLimiter(
    private val maxEventsPerWindow: Int = 48,
    private val windowMillis: Long = 5_000L,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var window: Long = Long.MIN_VALUE
    private var count: Int = 0

    @Synchronized
    fun allow(message: String): Boolean {
        if (!message.startsWith("QUERY ") &&
            !message.startsWith("RESULT ") &&
            !message.startsWith("SYSTEM_CALLER_BYPASS ") &&
            !message.startsWith("MANAGER_HIT ") &&
            !message.startsWith("AUTHORITY_OBSERVED ")
        ) return true
        val bucket = elapsedMillis() / windowMillis
        if (window != bucket) {
            window = bucket
            count = 0
        }
        count++
        return count <= maxEventsPerWindow
    }
}
