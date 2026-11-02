package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceDetailRouteTest {
    private val service = "org.example.accessibility/org.example.feature.AccessibilityService"

    @Test fun acceptsOnlyWellFormedServiceComponents() {
        assertEquals(service, AccessibilityServiceDetailRoute.flattenedService(
            "org.example.accessibility", "org.example.feature.AccessibilityService"))
        assertEquals("org.example/org.example.Service",
            AccessibilityServiceDetailRoute.flattenedService("org.example", ".Service"))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", null))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", "a;reboot"))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", "a/b"))
    }

    @Test fun rootLaunchNamesOnlyTheValidatedServiceAndDoesNotToggleItsState() {
        val cmd = AccessibilityServiceDetailRoute.rootLaunchCommand(0, service)
        assertEquals(
            "am start -W --user 0 -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS" +
                " -c android.intent.category.DEFAULT -p com.android.settings" +
                " --es android.intent.extra.COMPONENT_NAME $service", cmd)
        assertFalse(cmd.contains("settings put"))
        assertFalse(cmd.contains("pm enable"))
        assertTrue(runCatching {
            AccessibilityServiceDetailRoute.rootLaunchCommand(-1, service)
        }.isFailure)
        assertTrue(runCatching {
            AccessibilityServiceDetailRoute.rootLaunchCommand(0, "org.evil/a;reboot")
        }.isFailure)
    }

    @Test fun rootResultRejectsWrongPackageAndErrors() {
        assertTrue(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: com.android.settings/.Settings\nComplete"))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: com.example.OtherActivity\nComplete"))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Permission Denial\nStatus: error\nActivity: com.android.settings/.Settings"))
    }
}
