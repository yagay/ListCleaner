package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectSharePolicyTest {
    @Test
    fun removesTargetsWhoseOwnerAppIsNotVisibleInShareResolver() {
        assertEquals(
            listOf(0, 2),
            directShareVisibleIndices(
                listOf("com.chat", "com.hidden", "com.chat"),
                setOf("com.chat")
            )
        )
    }

    @Test
    fun emptyVisibleAppSetRemovesAllKnownDirectShareTargets() {
        assertEquals(
            emptyList<Int>(),
            directShareVisibleIndices(
                listOf("com.chat", "com.mail"),
                emptySet()
            )
        )
    }

    @Test
    fun unknownTargetPackageFailsOpen() {
        assertEquals(
            listOf(0),
            directShareVisibleIndices(
                listOf(null, "com.hidden"),
                emptySet()
            )
        )
    }
}
