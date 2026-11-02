package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageCaptureProtocolTest {
    private fun page(
        packageName: String = "com.android.settings",
        activity: String = "com.android.settings.SubSettings",
        fragment: String = "com.android.settings.applications.defaultapps.DefaultBrowserPicker",
        role: String = "android.app.role.BROWSER",
    ) = CapturedSettingsPage(
        packageName = packageName,
        activityClass = activity,
        fragmentClass = fragment,
        roleName = role,
        action = "android.intent.action.MAIN",
        userId = 0,
        capturedAtMillis = 1000L,
    )

    @Test fun acceptsFrameworkActivityAndFragment() {
        assertTrue(SettingsPageCaptureProtocol.valid(page()))
    }

    @Test fun acceptsAospClassInsideGooglePermissionController() {
        assertTrue(SettingsPageCaptureProtocol.valid(page(
            packageName = "com.google.android.permissioncontroller",
            activity = "com.android.permissioncontroller.role.ui.DefaultAppActivity",
            fragment = ""
        )))
    }

    @Test fun rejectsOtherPackagesAndUntrustedFragmentData() {
        assertFalse(SettingsPageCaptureProtocol.valid(page(packageName = "com.example.app")))
        assertFalse(SettingsPageCaptureProtocol.valid(page(fragment = "com.example.SecretFragment")))
        assertFalse(SettingsPageCaptureProtocol.valid(page(activity = "com.example.FakeSettings")))
    }

    @Test fun rejectsArbitraryActionsAndPersonalRoleData() {
        assertFalse(SettingsPageCaptureProtocol.valid(page().copy(action = "custom.action.READ")))
        assertFalse(SettingsPageCaptureProtocol.valid(page(role = "arbitrary-private-extra")))
        assertFalse(SettingsPageCaptureProtocol.valid(page().copy(userId = -1)))
    }
}
