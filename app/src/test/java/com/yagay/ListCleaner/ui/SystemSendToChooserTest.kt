package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSendToChooserTest {
    @Test fun smsPreviewForcesRealAndroidChooserEvenWithDefaultSmsApp() {
        assertEquals(SystemPreviewRoute.DIRECT, systemPreviewRoute(IntentKind.SEND_TO))
        assertTrue(forceSystemChooserForSendTo(IntentKind.SEND_TO, SystemProbeScenario.SMS))
    }

    @Test fun emailSendToAndOtherEntrypointsRemainUnchanged() {
        assertFalse(forceSystemChooserForSendTo(IntentKind.SEND_TO, SystemProbeScenario.MAIL))
        assertFalse(forceSystemChooserForSendTo(IntentKind.OPEN, SystemProbeScenario.SMS))
        assertFalse(forceSystemChooserForSendTo(IntentKind.SHARE, SystemProbeScenario.SMS))
        assertEquals(SystemPreviewRoute.CHOOSER, systemPreviewRoute(IntentKind.SHARE))
    }
}
