package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileServiceEligibilityTest {
    @Test fun unavailableServicesStayManageableButDoNotAppearActive() {
        // A disabled component should remain discoverable so Root management can enable it.
        val bind = TileServiceEligibility.REQUIRED_PERMISSION
        assertTrue(TileServiceEligibility.declared(bind, true))
        assertFalse(TileServiceEligibility.active(bind, true, false, true))
        assertFalse(TileServiceEligibility.active(bind, true, true, false))
        assertTrue(TileServiceEligibility.active(bind, true, true, true))
    }

    @Test fun systemBindingPermissionAndExportAreMandatory() {
        val bind = TileServiceEligibility.REQUIRED_PERMISSION
        assertFalse(TileServiceEligibility.declared(null, true))
        assertFalse(TileServiceEligibility.declared("android.permission.BIND_NFC_SERVICE", true))
        assertFalse(TileServiceEligibility.declared(bind, false))
        assertFalse(TileServiceEligibility.active(bind, false, true, true))
    }
}
