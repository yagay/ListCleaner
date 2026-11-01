package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookFailureCircuitTest {
    @Test fun hookDisablesAfterConsecutiveAdapterFailures() {
        val circuit = HookFailureCircuit()
        assertFalse(circuit.isDisabled())
        assertFalse(circuit.recordFailure())
        assertFalse(circuit.recordFailure())
        assertTrue(circuit.recordFailure())
        assertTrue(circuit.isDisabled())
        assertFalse(circuit.recordFailure())
        circuit.recordSuccess()
        assertTrue(circuit.isDisabled())
    }

    @Test fun successfulInterceptionClearsTransientFailures() {
        val circuit = HookFailureCircuit(2)
        assertFalse(circuit.recordFailure())
        circuit.recordSuccess()
        assertFalse(circuit.recordFailure())
        assertTrue(circuit.recordFailure())
    }
}
