package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceDetailRouteTest {
    private val service = "org.example.accessibility/org.example.feature.AccessibilityService"
    private val activity =
        "com.android.settings/com.android.settings.Settings" +
            "$" + "AccessibilityDetailsSettingsActivity"

    @Test fun acceptsOnlyWellFormedServiceComponents() {
        assertEquals(service, AccessibilityServiceDetailRoute.flattenedService(
            "org.example.accessibility", "org.example.feature.AccessibilityService"))
        assertEquals("org.example/org.example.Service",
            AccessibilityServiceDetailRoute.flattenedService("org.example", ".Service"))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", null))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", "a;reboot"))
        assertNull(AccessibilityServiceDetailRoute.flattenedService("org.example", "a/b"))
    }

    @Test fun discoveryUsesTheDetailActionAndDoesNotGuessACandidate() {
        assertEquals(
            "cmd package query-activities --brief --components --user 10" +
                " -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS" +
                " -c android.intent.category.DEFAULT",
            AccessibilityServiceDetailRoute.discoverCommand(10))
        val output = "2 activities found:\ncom.android.settings/.Settings\n$activity"
        assertEquals(activity, AccessibilityServiceDetailRoute.selectRegisteredDetails(output))
        assertNull(AccessibilityServiceDetailRoute.selectRegisteredDetails(
            "com.android.settings/com.android.settings.Settings"))
        assertNull(AccessibilityServiceDetailRoute.selectRegisteredDetails(
            "com.other.settings/com.other.Settings" + "$" + "AccessibilityDetailsSettingsActivity"))
    }

    @Test fun explicitRootLaunchQuotesTheActivityAndPassesTheService() {
        val cmd = AccessibilityServiceDetailRoute.rootLaunchCommand(0, service, activity)
        assertEquals(
            "am start -W --user 0 -n '$activity'" +
                " -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS" +
                " -c android.intent.category.DEFAULT" +
                " --es android.intent.extra.COMPONENT_NAME $service",
            cmd)
        assertFalse(cmd.contains("settings put"))
        assertTrue(runCatching {
            AccessibilityServiceDetailRoute.rootLaunchCommand(0, service, "com.android.settings/.Settings")
        }.isFailure)
        assertTrue(runCatching {
            AccessibilityServiceDetailRoute.rootLaunchCommand(0, "org.evil/a;reboot", activity)
        }.isFailure)
    }

    @Test fun shellLaunchMustNameTheRightRegisteredSettingsPackage() {
        assertTrue(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: $activity\nComplete", activity))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Status: ok\nActivity: com.example.OtherActivity", activity))
        assertFalse(AccessibilityServiceDetailRoute.launchAccepted(
            "Permission Denial\nStatus: error\nActivity: $activity", activity))
    }

    @Test fun mainAccessibilityPageMustNeverCountAsDetailSuccess() {
        assertTrue(AccessibilityServiceDetailRoute.detailFragmentVisible(
            "ToggleAccessibilityServicePreferenceFragment{dd110}"))
        assertFalse(AccessibilityServiceDetailRoute.detailFragmentVisible(
            "AccessibilitySettings{abc}"))
        assertTrue(AccessibilityServiceDetailRoute.parentAccessibilityVisible(
            "AccessibilitySettings{abc}"))
        assertFalse(AccessibilityServiceDetailRoute.parentAccessibilityVisible(
            "ToggleAccessibilityServicePreferenceFragment{abc}"))
    }
}
