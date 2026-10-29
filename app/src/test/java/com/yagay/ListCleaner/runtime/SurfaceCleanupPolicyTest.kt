package com.yagay.ListCleaner.runtime

import android.app.Notification
import com.yagay.ListCleaner.domain.ControlsProviderEligibility
import org.junit.Assert.*
import org.junit.Test

class SurfaceCleanupPolicyTest {
    @Test fun deviceControlProvidersRequireTheExactPlatformPermissionAndExport() {
        assertTrue(ControlsProviderEligibility.accepts(ControlsProviderEligibility.PERMISSION, true))
        assertFalse(ControlsProviderEligibility.accepts(ControlsProviderEligibility.PERMISSION, false))
        assertFalse(ControlsProviderEligibility.accepts(null, true))
        assertFalse(ControlsProviderEligibility.accepts("android.permission.BIND_QUICK_SETTINGS_TILE", true))
    }

    @Test fun emergencyAndOngoingNotificationsAreNotDismissed() {
        assertTrue(NotificationCleanupPolicy.protected(0, Notification.CATEGORY_CALL))
        assertTrue(NotificationCleanupPolicy.protected(0, Notification.CATEGORY_ALARM))
        assertTrue(NotificationCleanupPolicy.protected(Notification.FLAG_NO_CLEAR, null))
        assertTrue(NotificationCleanupPolicy.protected(Notification.FLAG_FOREGROUND_SERVICE, null))
        assertTrue(NotificationCleanupPolicy.protected(Notification.FLAG_ONGOING_EVENT, null))
        assertFalse(NotificationCleanupPolicy.protected(0, Notification.CATEGORY_MESSAGE))
    }
}
