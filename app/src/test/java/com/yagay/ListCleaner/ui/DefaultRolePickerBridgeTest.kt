package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRolePickerBridgeTest {
    private val target =
        "com.google.android.permissioncontroller/com.android.permissioncontroller.role.ui.DefaultAppActivity"
    private val unrelated =
        "com.google.android.permissioncontroller/com.android.permissioncontroller.role.ui.DefaultAppListActivity"
    private val settings = "com.android.settings/com.android.settings.SubSettings"
    private val systems = setOf(
        "com.google.android.permissioncontroller", "com.android.settings"
    )

    @Test fun supportsOnlyKnownRolePickerKinds() {
        assertEquals("android.app.role.BROWSER",
            DefaultRolePickerBridge.roleName(IntentKind.BROWSER))
        assertEquals("android.app.role.HOME",
            DefaultRolePickerBridge.roleName(IntentKind.HOME))
        assertEquals("android.app.role.CALL_SCREENING",
            DefaultRolePickerBridge.roleName(IntentKind.CALL_SCREENING))
        assertEquals("android.app.role.ASSISTANT",
            DefaultRolePickerBridge.roleName(IntentKind.ASSISTANT))
        assertTrue(runCatching {
            DefaultRolePickerBridge.roleName(IntentKind.OPEN)
        }.isFailure)
    }

    @Test fun registeredSystemRoleActivityOnly() {
        val output = "3 activities found:\n$settings\n$unrelated\n$target"
        assertEquals(target,
            DefaultRolePickerBridge.selectRegisteredComponent(output, systems))
        assertNull(DefaultRolePickerBridge.selectRegisteredComponent(
            output, setOf("com.android.settings")))
        assertNull(DefaultRolePickerBridge.selectRegisteredComponent(
            settings + "\n" + unrelated, systems))
    }

    @Test fun exactRootCommandForBrowserAndCrossProfile() {
        val command = DefaultRolePickerBridge.launchCommand(
            10, target, IntentKind.BROWSER
        )
        assertEquals(
            "am start -W --user 10 -n $target" +
                " -a android.intent.action.MANAGE_DEFAULT_APP" +
                " -c android.intent.category.DEFAULT" +
                " --es android.intent.extra.ROLE_NAME android.app.role.BROWSER",
            command,
        )
        assertTrue(runCatching {
            DefaultRolePickerBridge.launchCommand(
                0, target, IntentKind.OPEN
            )
        }.isFailure)
        assertTrue(runCatching {
            DefaultRolePickerBridge.launchCommand(
                0, "com.evil/a;reboot", IntentKind.BROWSER
            )
        }.isFailure)
        assertTrue(runCatching {
            DefaultRolePickerBridge.launchCommand(
                -1, target, IntentKind.BROWSER
            )
        }.isFailure)
    }

    @Test fun rejectsPermissionDenialsAndWrongActivityOnShellSuccess() {
        assertTrue(DefaultRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $target\nComplete", target))
        assertFalse(DefaultRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $unrelated\nComplete", target))
        assertFalse(DefaultRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $settings\nComplete", target))
        assertFalse(DefaultRolePickerBridge.launchAccepted(
            "Status: error\nPermission Denial\nActivity: $target", target))
    }

    @Test fun actualResumedActivityMustMatchClassNotOnlyPackage() {
        val ok = "topResumedActivity=ActivityRecord{abc u0 $target t10}"
        val redirectedSamePkg = "topResumedActivity=ActivityRecord{abc u0 $unrelated t10}"
        val redirectedSettings = "topResumedActivity=ActivityRecord{abc u0 $settings t12}"
        assertTrue(DefaultRolePickerBridge.foregroundMatches(ok, target))
        assertFalse(DefaultRolePickerBridge.foregroundMatches(redirectedSamePkg, target))
        assertFalse(DefaultRolePickerBridge.foregroundMatches(redirectedSettings, target))
        assertEquals(unrelated,
            DefaultRolePickerBridge.foregroundComponent(redirectedSamePkg))
        assertNull(DefaultRolePickerBridge.foregroundComponent("No activity resumed"))
    }

    @Test fun shortActivityNameResolvesToQualifiedName() {
        val short = "com.android.permissioncontroller/.role.ui.DefaultAppActivity"
        val expanded =
            "com.android.permissioncontroller/com.android.permissioncontroller.role.ui.DefaultAppActivity"
        val output = "topResumedActivity=ActivityRecord{cafe u0 $short t10}"
        assertTrue(DefaultRolePickerBridge.foregroundMatches(output, expanded))
        assertTrue(DefaultRolePickerBridge.launchAccepted(
            "Status: ok\nActivity: $short\nComplete", expanded))
    }
}
