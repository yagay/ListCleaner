package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RootGlobalModePolicyTest {
    private val first = "0|com.example.alpha|com.example.alpha.Tile"
    private val second = "10|com.example.beta|com.example.beta.Widget"
    private val third = "11|com.example.gamma|com.example.gamma.Launcher"

    @Test fun pausedProfileIsExcludedWithoutErasingSavedPolicy() {
        val saved = setOf(first, second, third)
        val before = saved.toSet()
        assertEquals(setOf(first, third), RootGlobalModePolicy.effectiveKeys(saved, setOf(10)))
        assertEquals(before, saved) // The original desired rules remain intact.
    }

    @Test fun resumeReappliesOnlyMatchingUserKeys() {
        val saved = setOf(first, second, third)
        assertEquals(setOf(second), RootGlobalModePolicy.userKeys(saved, 10))
        assertEquals(saved, RootGlobalModePolicy.effectiveKeys(saved, emptySet()))
    }

    @Test fun multiplePausedProfilesRemainIndependent() {
        val saved = setOf(first, second, third)
        assertEquals(setOf(first), RootGlobalModePolicy.effectiveKeys(saved, setOf(10, 11)))
        assertEquals(emptySet<String>(), RootGlobalModePolicy.effectiveKeys(saved, setOf(0, 10, 11)))
    }
}
