package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRolePickerBridgeTest {
    private val component =
        "com.google.android.permissioncontroller/com.android.permissioncontroller.role.ui.DefaultAppActivity"
    private val settings = "com.android.settings/com.android.settings.SubSettings"
    private val matches = "2 activities found:\n$settings\n$component"
    private val systemList = "package:com.android.settings\npackage:com.google.android.permissioncontroller"

    @Test fun discoveryUsesSystemPackageResolverAndRequestedUser() {
        assertEquals(
            "cmd package query-activities --brief --components --user 0" +
                " -a android.intent.action.MANAGE_DEFAULT_APP" +
                " -c android.intent.category.DEFAULT",
            AssistantRolePickerBridge.discoverCommand(0)
        )
        assertEquals(
            "cmd package list packages -s --user 10",
            AssistantRolePickerBridge.systemPackageQueryCommand(10)
        )
    }

    @Test fun ignoresSettingsAndFindsActuallyRegisteredRoleActivity() {
        val found = AssistantRolePickerBridge.selectRegisteredComponent(
            matches, AssistantRolePickerBridge.systemPackages(systemList)
        )
        assertEquals(component, found)
        assertNull(AssistantRolePickerBridge.selectRegisteredComponent(
            settings, AssistantRolePickerBridge.systemPackages(systemList)
        ))
    }

    @Test fun rejectsSpoofedThirdPartyAndUnverifiedPackages() {
        val thirdParty = "com.other.permissioncontroller/com.other.role.ui.DefaultAppActivity"
        assertNull(AssistantRolePickerBridge.selectRegisteredComponent(
            thirdParty, AssistantRolePickerBridge.systemPackages(systemList)
        ))
        assertNull(AssistantRolePickerBridge.selectRegisteredComponent(
            matches, setOf("com.android.settings")
        ))
        assertFalse(AssistantRolePickerBridge.systemPackages(systemList)
            .contains("com.other.permissioncontroller"))
    }

    @Test fun explicitlyStartsRegisteredComponentWithoutRoleMutation() {
        val command = AssistantRolePickerBridge.launchCommand(0, component)
        assertEquals(
            "am start -W --user 0 -n $component" +
                " -a android.intent.action.MANAGE_DEFAULT_APP" +
                " -c android.intent.category.DEFAULT" +
                " --es android.intent.extra.ROLE_NAME android.app.role.ASSISTANT",
            command
        )
        assertFalse(command.contains("cmd role add-role-holder"))
        assertTrue(runCatching {
            AssistantRolePickerBridge.launchCommand(0, "com.evil/x;reboot")
        }.isFailure)
        assertTrue(runCatching {
            AssistantRolePickerBridge.launchCommand(-1, component)
        }.isFailure)
    }

    @Test fun acceptsOnlyAnExplicitSuccessfulStart() {
        assertTrue(AssistantRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $component\nTotalTime: 97\nComplete",
            component
        ))
        assertFalse(AssistantRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $settings\nComplete", component
        ))
        assertFalse(AssistantRolePickerBridge.launchAccepted(
            "SecurityException: permission denial\nStatus: error", component
        ))
        assertFalse(AssistantRolePickerBridge.launchAccepted(
            "Starting: Intent", component
        ))
    }

    @Test fun foregroundVerifierRejectsOemRedirectionToSettings() {
        val actual = "topResumedActivity=ActivityRecord{123 u0 $component t10}"
        val redirected = "topResumedActivity=ActivityRecord{123 u0 $settings t12}"
        assertTrue(AssistantRolePickerBridge.foregroundMatches(actual, component))
        assertFalse(AssistantRolePickerBridge.foregroundMatches(redirected, component))
        assertEquals("com.android.settings",
            AssistantRolePickerBridge.foregroundPackage(redirected))
        assertNull(AssistantRolePickerBridge.foregroundPackage("Nothing resumed"))
    }
}
