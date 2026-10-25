package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProbeScenarioTest {
    @Test fun shareAndOpenUseTypedIntentScenarios() {
        for (kind in listOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.OPEN)) {
            assertEquals(
                listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF),
                availableProbeScenarios(kind)
            )
        }
        assertEquals("image/png", SystemProbeScenario.IMAGE.mimeType)
        assertEquals("application/pdf", SystemProbeScenario.PDF.mimeType)
    }

    @Test fun fileAndSendToTestTypesAreNotAllTheSame() {
        val pickerTypes = availableProbeScenarios(IntentKind.GET_CONTENT)
        assertTrue(SystemProbeScenario.VIDEO in pickerTypes)
        assertTrue(SystemProbeScenario.AUDIO in pickerTypes)
        assertEquals(SystemProbeScenario.ANY, defaultProbeScenario(IntentKind.OPEN_DOCUMENT))
        assertEquals(
            listOf(SystemProbeScenario.MAIL, SystemProbeScenario.SMS),
            availableProbeScenarios(IntentKind.SEND_TO)
        )
        assertEquals(
            listOf(SystemProbeScenario.HTTPS, SystemProbeScenario.HTTP),
            availableProbeScenarios(IntentKind.DEEP_LINK)
        )
    }

    @Test fun frameworkServiceCandidatesMustMatchManifestBindPermission() {
        val nfc = SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == IntentKind.NFC_HCE }
        assertEquals(2, nfc.size)
        assertTrue(nfc.all { it.acceptsPermission("android.permission.BIND_NFC_SERVICE") })
        assertFalse(nfc.any { it.acceptsPermission("android.permission.BIND_VPN_SERVICE") })
        val credential = SYSTEM_SERVICE_ENTRY_DEFINITIONS.single {
            it.kind == IntentKind.CREDENTIAL_PROVIDER
        }
        assertFalse(credential.acceptsPermission(null))
    }
}
