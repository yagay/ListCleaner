package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPolicyTest {
    @Test fun privilegedCallersAreAlwaysPreservedByResolverFilter() {
        assertTrue(FilterPolicy.sameCaller(1000, 10123))
        assertTrue(FilterPolicy.sameCaller(0, 10123))
        assertTrue(FilterPolicy.sameCaller(101000, 110123))
    }

    @Test fun packageManagerFilteringOnlyAllowsOrdinaryApplicationCallers() {
        assertFalse(FilterPolicy.ordinaryAppCaller(0))
        assertFalse(FilterPolicy.ordinaryAppCaller(1000))
        assertFalse(FilterPolicy.ordinaryAppCaller(9999))
        assertTrue(FilterPolicy.ordinaryAppCaller(10000))
        assertTrue(FilterPolicy.ordinaryAppCaller(10123))
    }

    @Test fun multiUserUidsAreClassifiedByAppId() {
        assertFalse(FilterPolicy.ordinaryAppCaller(101000))
        assertFalse(FilterPolicy.ordinaryAppCaller(201000))
        assertTrue(FilterPolicy.ordinaryAppCaller(110123))
        assertTrue(FilterPolicy.ordinaryAppCaller(210123))
    }

    @Test fun sharedUidHelpersHandleProfilesConsistently() {
        assertEquals(0, AndroidUid.userId(10123))
        assertEquals(1, AndroidUid.userId(110123))
        assertEquals(2, AndroidUid.userId(210123))
        assertEquals(10123, AndroidUid.appId(110123))
        assertEquals(1000, AndroidUid.appId(101000))
        assertEquals(-1, AndroidUid.userId(-1))
        assertEquals(-1, AndroidUid.appId(-1))
    }

    @Test fun ordinaryAppsOnlyPreserveTheirOwnUid() {
        assertTrue(FilterPolicy.sameCaller(10123, 10123))
        assertFalse(FilterPolicy.sameCaller(10123, 10124))
        assertTrue(FilterPolicy.sameCaller(110123, 110123))
        assertFalse(FilterPolicy.sameCaller(110123, 10123))
    }

    @Test fun catalogPrivacyStillUsesExactUidEquality() {
        assertTrue(FilterPolicy.catalogRestricted(false, 10123, 1000))
        assertFalse(FilterPolicy.catalogRestricted(false, 10123, 10123))
        assertFalse(FilterPolicy.catalogRestricted(true, 10123, 1000))
    }
}
