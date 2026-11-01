package com.yagay.ListCleaner.xposed

/**
 * Process-local fail-open circuit for one installed hook method.
 *
 * Repeated adapter failures disable only this hook until its process is restarted.
 * An exception thrown by the original framework method is not a hook failure.
 */
internal class HookFailureCircuit(private val threshold: Int = 3) {
    init { require(threshold > 0) }
    private var consecutiveFailures = 0
    private var disabled = false

    @Synchronized fun isDisabled(): Boolean = disabled

    /** Returns true only when this failure trips the circuit. */
    @Synchronized fun recordFailure(): Boolean {
        if (disabled) return false
        consecutiveFailures++
        if (consecutiveFailures < threshold) return false
        disabled = true
        return true
    }

    @Synchronized fun recordSuccess() {
        if (!disabled) consecutiveFailures = 0
    }
}
