package com.yagay.ListCleaner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateScanEvidenceTest {
    @Test fun neverTreatUnscannedOrPartialZeroAsVerifiedEmpty() {
        assertFalse(CandidateScanEvidence(userId = 0).isVerifiedEmpty)
        assertFalse(CandidateScanEvidence(0, 100L, 0, failedSources = 1).isVerifiedEmpty)
        assertFalse(CandidateScanEvidence(10, 100L, 0, unavailableSources = 1).isVerifiedEmpty)
    }

    @Test fun completeEmptyAndNonEmptyHaveDifferentMeanings() {
        assertTrue(CandidateScanEvidence(0, 100L).isVerifiedEmpty)
        assertTrue(CandidateScanEvidence(0, 100L, candidateCount = 2).isComplete)
        assertFalse(CandidateScanEvidence(0, 100L, candidateCount = 2).isVerifiedEmpty)
    }

    @Test fun partialResultsRemainUsefulWithoutClaimingCompleteness() {
        val evidence = CandidateScanEvidence(10, 100L, candidateCount = 4, failedSources = 1)
        assertFalse(evidence.isComplete)
        assertFalse(evidence.isVerifiedEmpty)
    }
}
