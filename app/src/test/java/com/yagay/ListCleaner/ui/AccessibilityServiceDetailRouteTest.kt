package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceDetailRouteTest {
    private val service = ComponentName(
        "org.example.accessibility",
        "org.example.feature.AccessibilityService",
    )

    @Test fun acceptsOnlyWellFormedServiceComponents() {
        assertEquals(service, AccessibilityServiceDetailRoute.component(
            "org.example.accessibility", "org.example.feature.AccessibilityService"))
        assertEquals(ComponentName("org.example", "org.example.Service"),
            AccessibilityServiceDetailRoute.component("org.example", ".Service"))
        assertNull(AccessibilityServiceDetailRoute.component("org.example", null))
        assertNull(AccessibilityServiceDetailRoute.component("org.example", "a;reboot"))
        assertNull(AccessibilityServiceDetailRoute.component("org.example", "a/b"))
    }

    @Test fun detailIntentTargetsSystemSettingsAndCarriesExactServiceName() {
        val intent = AccessibilityServiceDetailRoute.detailIntent(service)
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", intent.action)
        assertEquals("com.android.settings", intent.`package`)
        assertEquals(service.flattenToString(),
            intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME))
        assertTrue(intent.hasCategory(Intent.CATEGORY_DEFAULT))
    }

    @Test fun rootLaunchNamesOnlyTheValidatedServiceAndDoesNotToggleItsState() {
        val cmd = AccessibilityServiceDetailRoute.rootLaunchCommand(0, service)
        assertEquals(
            "am start -W --user 0 -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS" +
                " -c android.intent.category.DEFAULT -p com.android.settings" +
                " --es android.intent.extra.COMPONENT_NAME ${service.flattenToString()}",
            cmd)
        assertFalse(cmd.contains("settings put"))
        assertFalse(cmd.contains("pm enable"))
        assertTrue(runCatching {
            AccessibilityServiceDetailRoute.rootLaunchCommand(-1, service)
        }.isFailure)
    }

    @Test fun rootResultRejectsParentAndErrors() {
        assertTrue(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: com.android.settings/.Settings\\$AccessibilityDetailsSettingsActivity\nComplete"))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: com.example.OtherActivity\nComplete"))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Permission Denial\nStatus: error\nActivity: com.android.settings/.Settings"))
    }
}
