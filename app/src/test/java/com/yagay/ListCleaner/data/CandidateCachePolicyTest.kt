package com.yagay.ListCleaner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateCachePolicyTest {
    private fun reuse(
        ageMs: Long,
        force: Boolean = false,
        invalidated: Boolean = false,
        current: String = "same",
        cached: String? = "same",
    ) = CandidateCachePolicy.canReuse(
        force = force, invalidated = invalidated,
        fingerprint = current, cachedFingerprint = cached,
        nowElapsedMs = 100_000L, lastCompletedElapsedMs = 100_000L - ageMs,
    )

    @Test fun usesFreshUnchangedCatalogWithoutExtraManagerScan() {
        assertTrue(reuse(0))
        assertTrue(reuse(CandidateCachePolicy.MAX_AGE_MS - 1))
    }

    @Test fun refreshesExpiredInvalidatedOrChangedSystemCatalog() {
        assertFalse(reuse(CandidateCachePolicy.MAX_AGE_MS))
        assertFalse(reuse(30_001L))
        assertFalse(reuse(5L, force = true))
        assertFalse(reuse(5L, invalidated = true))
        assertFalse(reuse(5L, current = "new"))
        assertFalse(reuse(5L, cached = null))
        assertFalse(reuse(-1L))
    }
}
