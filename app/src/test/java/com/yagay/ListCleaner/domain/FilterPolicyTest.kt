package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPolicyTest {
    @Test fun privilegedCallersAreAlwaysPreservedByResolverFilter() {
        assertTrue(FilterPolicy.sameCaller(1000, 10123))
        assertTrue(FilterPolicy.sameCaller(0, 10123))
    }

    @Test fun packageManagerFilteringOnlyAllowsOrdinaryApplicationCallers() {
        assertFalse(FilterPolicy.ordinaryAppCaller(0))
        assertFalse(FilterPolicy.ordinaryAppCaller(1000))
        assertFalse(FilterPolicy.ordinaryAppCaller(9999))
        assertTrue(FilterPolicy.ordinaryAppCaller(10000))
        assertTrue(FilterPolicy.ordinaryAppCaller(10123))
    }

    @Test fun ordinaryAppsOnlyPreserveTheirOwnUid() {
        assertTrue(FilterPolicy.sameCaller(10123, 10123))
        assertFalse(FilterPolicy.sameCaller(10123, 10124))
    }

    @Test fun catalogPrivacyStillUsesExactUidEquality() {
        assertTrue(FilterPolicy.catalogRestricted(false, 10123, 1000))
        assertFalse(FilterPolicy.catalogRestricted(false, 10123, 10123))
        assertFalse(FilterPolicy.catalogRestricted(true, 10123, 1000))
    }
}
