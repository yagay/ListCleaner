package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerTextQueryPolicyTest {
    @Test fun realTextActionModeCanBeFiltered() {
        assertFalse(ManagerTextQueryPolicy.bypassManager(true, false, false, true))
    }

    @Test fun managerInventoryRemainsUnfiltered() {
        assertTrue(ManagerTextQueryPolicy.bypassManager(true, false, true, true))
        assertTrue(ManagerTextQueryPolicy.bypassManager(true, false, false, false))
    }

    @Test fun authenticatedPreviewAndOrdinaryAppsAreNotBypassed() {
        assertFalse(ManagerTextQueryPolicy.bypassManager(true, true, false, true))
        assertFalse(ManagerTextQueryPolicy.bypassManager(false, false, true, true))
    }
}
