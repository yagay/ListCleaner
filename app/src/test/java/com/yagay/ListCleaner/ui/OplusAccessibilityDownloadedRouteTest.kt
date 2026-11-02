package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OplusAccessibilityDownloadedRouteTest {
    @Test fun deviceSpecificRouteIsNotUsedOnAospPhones() {
        assertTrue(OplusAccessibilityDownloadedRoute.supportsManufacturer("OnePlus"))
        assertTrue(OplusAccessibilityDownloadedRoute.supportsManufacturer("OPPO"))
        assertTrue(OplusAccessibilityDownloadedRoute.supportsManufacturer("realme"))
        assertFalse(OplusAccessibilityDownloadedRoute.supportsManufacturer("Google"))
        assertFalse(OplusAccessibilityDownloadedRoute.supportsManufacturer("Samsung"))
    }

    @Test fun launchUsesExactFragmentCapturedFromOxygenOsSettings() {
        assertEquals(
            "am start -W --user 0 -n com.android.settings/.SubSettings" +
                " -a android.intent.action.MAIN" +
                " --es ':settings:show_fragment' " +
                "com.oplus.settings.feature.accessibility.AccessibilityDownloadedSettings" +
                " --ez ':settings:is_second_layer_page' true",
            OplusAccessibilityDownloadedRoute.rootLaunchCommand(0),
        )
        assertTrue(runCatching {
            OplusAccessibilityDownloadedRoute.rootLaunchCommand(-1)
        }.isFailure)
    }

    @Test fun onlyExplicitSubSettingsLaunchCountsAsAccepted() {
        assertTrue(OplusAccessibilityDownloadedRoute.activityLaunchAccepted(
            "Status: ok\nActivity: com.android.settings/.SubSettings\nComplete"))
        assertTrue(OplusAccessibilityDownloadedRoute.activityLaunchAccepted(
            "Status: ok\nActivity: com.android.settings/com.android.settings.SubSettings"))
        assertFalse(OplusAccessibilityDownloadedRoute.activityLaunchAccepted(
            "Status: ok\nActivity: com.android.settings/.Settings"))
        assertFalse(OplusAccessibilityDownloadedRoute.activityLaunchAccepted(
            "Permission Denial\nStatus: error\nActivity: com.android.settings/.SubSettings"))
    }

    @Test fun downloadedFragmentMustActuallyBeObserved() {
        assertTrue(OplusAccessibilityDownloadedRoute.actualFragmentVisible(
            "Active fragment com.oplus.settings.feature.accessibility.AccessibilityDownloadedSettings"))
        assertFalse(OplusAccessibilityDownloadedRoute.actualFragmentVisible(
            "AccessibilitySettings: user_installed_services_category"))
        assertFalse(OplusAccessibilityDownloadedRoute.actualFragmentVisible(
            "com.oplus.settings.feature.accessibility.OplusToggleAccessibilityServicePreferenceFragment"))
    }
}
