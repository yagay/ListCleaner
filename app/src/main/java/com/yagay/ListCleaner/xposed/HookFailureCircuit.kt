package com.yagay.ListCleaner.xposed

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-local fail-open circuit for one installed hook method.
 *
 * Repeated adapter failures disable only that hook until its process is restarted.
 * Original framework exceptions must not count toward this threshold.
 * The success path is lock-free because resolver hooks run very frequently.
 */
internal class HookFailureCircuit(private val threshold: Int = 3) {
    init { require(threshold > 0) }
    private val consecutiveFailures = AtomicInteger()
    private val disabled = AtomicBoolean()

    fun isDisabled(): Boolean = disabled.get()

    /** Returns true only when this failure trips the circuit. */
    fun recordFailure(): Boolean {
        if (disabled.get()) return false
        val count = consecutiveFailures.incrementAndGet()
        return count >= threshold && disabled.compareAndSet(false, true)
    }

    fun recordSuccess() {
        if (!disabled.get() && consecutiveFailures.get() != 0) consecutiveFailures.set(0)
    }
}
