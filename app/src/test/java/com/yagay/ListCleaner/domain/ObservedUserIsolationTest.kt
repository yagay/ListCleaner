package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class ObservedUserIsolationTest {
    private val personal = ObservedEntryRecord(IntentKind.ASSISTANT.name, "com.example.assist", "@assistant", observedAt = 1)
    private val work = personal.copy(userId = 10, observedAt = 2)

    @Test fun sameComponentInTwoUsersKeepsBothIdentities() {
        assertNotEquals(personal.key, work.key)
        val decoded = ObservedEntryCacheCodec.decode(ObservedEntryCacheCodec.encode(listOf(personal, work)))
        assertEquals(setOf(0, 10), decoded.map { it.userId }.toSet())
    }

    @Test fun emptyWorkAuthoritySnapshotDoesNotErasePersonalHistory() {
        val result = ObservedEntryCacheCodec.reconcileRemote(listOf(personal, work), emptyList(), userId = 10)
        assertEquals(listOf(personal), result)
    }

    @Test fun invalidUsersAreRejected() {
        assertNull(personal.copy(userId = -1).validatedOrNull())
        assertNull(personal.copy(userId = Int.MAX_VALUE).validatedOrNull())
    }
}
