package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeComponentPolicyTest {
    @Test fun verifiedSnapshotCanRepresentAnAuthoritativeEmptyPolicy() {
        RuntimeComponentPolicy.publish(10715, emptySet(), "a".repeat(64))
        val snapshot = RuntimeComponentPolicy.snapshot()
        assertTrue(snapshot.authoritative)
        assertEquals(10715, snapshot.managerAppId)
        assertTrue(snapshot.protectedComponents.isEmpty())
    }

    @Test fun emptyDigestIsNotRuntimeAuthoritative() {
        RuntimeComponentPolicy.publish(10715, setOf("0|com.example|com.example.Tile"), "")
        assertFalse(RuntimeComponentPolicy.snapshot().authoritative)
    }
}
