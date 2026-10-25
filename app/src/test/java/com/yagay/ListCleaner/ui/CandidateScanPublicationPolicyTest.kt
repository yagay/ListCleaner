package com.yagay.ListCleaner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateScanPublicationPolicyTest {
    @Test fun acceptsCurrentScan() {
        assertTrue(CandidateScanPublicationPolicy.isCurrent(5L, 5L, 10, 10, 10))
    }

    @Test fun rejectsPreviousRefreshAfterSuspension() {
        assertFalse(CandidateScanPublicationPolicy.isCurrent(4L, 5L, 10, 10, 10))
    }

    @Test fun rejectsResultsAfterSpaceSwitch() {
        assertFalse(CandidateScanPublicationPolicy.isCurrent(5L, 5L, 0, 10, 10))
        assertFalse(CandidateScanPublicationPolicy.isCurrent(5L, 5L, 10, 10, null))
        assertFalse(CandidateScanPublicationPolicy.isCurrent(5L, 5L, 10, 10, 0))
    }
}
