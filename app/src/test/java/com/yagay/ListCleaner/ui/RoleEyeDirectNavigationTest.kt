package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleEyeDirectNavigationTest {
    @Test fun defaultAppRoleEyesOpenTheNativePickerWithoutLocalSnapshotDialog() {
        listOf(
            IntentKind.BROWSER, IntentKind.HOME, IntentKind.ASSISTANT,
            IntentKind.CALL_SCREENING,
        ).forEach { kind ->
            assertTrue(kind.name, opensRolePickerDirectly(kind))
        }
    }

    @Test fun browserProtocolChoicesDoNotForceAnIntermediateDialog() {
        // Browser has HTTP and HTTPS probe scenarios, but these do not
        // represent separate default-app roles and must not trigger a menu.
        assertTrue(availableProbeScenarios(IntentKind.BROWSER).size > 1)
        assertTrue(opensRolePickerDirectly(IntentKind.BROWSER))
    }

    @Test fun nonRoleCategoriesKeepTheirExistingPreviewFlow() {
        listOf(
            IntentKind.OPEN, IntentKind.SHARE, IntentKind.DEEP_LINK,
            IntentKind.GET_CONTENT,
        ).forEach { kind ->
            assertFalse(kind.name, opensRolePickerDirectly(kind))
        }
        assertFalse(opensRolePickerDirectly(null))
    }
}
