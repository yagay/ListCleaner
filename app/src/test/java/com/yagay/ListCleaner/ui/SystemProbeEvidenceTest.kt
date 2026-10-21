package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No category may silently fall back to 'resolver handlers = final visible UI'. */
class SystemProbeEvidenceTest {
    @Test fun systemOwnedPickersDoNotPretendToExposeTheirVisibleSources() {
        listOf(
            IntentKind.GET_CONTENT,
            IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT,
            IntentKind.DOCUMENT_ROOT,
            IntentKind.PHOTO_SOURCE,
            IntentKind.DIRECT_SHARE,
            IntentKind.SHORTCUT_ITEM,
            IntentKind.LAUNCHER_SHORTCUT,
        ).forEach { kind ->
            assertEquals(kind.name, ProbeEvidence.NATIVE_UI_ONLY, previewEvidence(kind))
        }
    }

    @Test fun installedServicesAreNotConfusedWithFinalSystemRolesOrSettings() {
        listOf(
            IntentKind.VPN, IntentKind.CREDENTIAL_PROVIDER, IntentKind.AUTOFILL,
            IntentKind.PRINT, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
            IntentKind.WALLPAPER, IntentKind.DREAM,
        ).forEach { kind ->
            assertEquals(kind.name, ProbeEvidence.SERVICE_REGISTRY, previewEvidence(kind))
        }
        listOf(
            IntentKind.HOME, IntentKind.ASSISTANT, IntentKind.BROWSER,
            IntentKind.CALL_SCREENING,
        ).forEach { kind ->
            assertEquals(kind.name, ProbeEvidence.ROLE_CANDIDATES, previewEvidence(kind))
        }
    }

    @Test fun availableManagerAndProviderRegistriesIdentifyTheirOwnScope() {
        assertEquals(ProbeEvidence.MANAGER_REGISTRY, previewEvidence(IntentKind.INPUT_METHOD))
        assertEquals(ProbeEvidence.MANAGER_REGISTRY, previewEvidence(IntentKind.ACCESSIBILITY))
        assertEquals(ProbeEvidence.PROVIDER_REGISTRY, previewEvidence(IntentKind.DOCUMENT_PROVIDER))
        assertEquals(ProbeEvidence.LAUNCHER_REGISTRY, previewEvidence(CleanupKind.LAUNCHER))
        assertEquals(ProbeEvidence.LAUNCHER_REGISTRY, previewEvidence(CleanupKind.SHORTCUT))
        assertEquals(ProbeEvidence.LAUNCHER_REGISTRY, previewEvidence(CleanupKind.WIDGET))
        assertEquals(ProbeEvidence.SERVICE_REGISTRY, previewEvidence(CleanupKind.TILE))
    }

    @Test fun resolverIntentsRemainLabeledCandidatesEvenWithAWorkingHook() {
        listOf(
            IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.SEND_TO,
            IntentKind.OPEN, IntentKind.DEEP_LINK, IntentKind.DIAL,
            IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO,
            IntentKind.RECORD_AUDIO, IntentKind.PROCESS_TEXT,
        ).forEach { kind ->
            assertEquals(kind.name, ProbeEvidence.RESOLVER_CANDIDATES, previewEvidence(kind))
        }
    }

    @Test fun everySelectableRuleKindHasAnExplicitProvenanceClass() {
        val assigned = IntentKind.entries.groupBy { previewEvidence(it) }
        assertEquals(IntentKind.entries.size, assigned.values.sumOf { it.size })
        assertTrue(assigned.keys.containsAll(ProbeEvidence.entries.filterNot {
            it == ProbeEvidence.LAUNCHER_REGISTRY
        }))
        assertFalse(assigned.values.any { it.isEmpty() })
        assertEquals(4, CleanupKind.entries.size)
        CleanupKind.entries.forEach { previewEvidence(it) }
    }

    @Test fun nativeOsPickerStillUsesTheSameTestScenario() {
        for (kind in listOf(IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT)) {
            assertEquals(ProbeEvidence.NATIVE_UI_ONLY, previewEvidence(kind))
            val scenarios = availableProbeScenarios(kind)
            assertTrue(scenarios.contains(SystemProbeScenario.IMAGE))
            assertTrue(scenarios.contains(SystemProbeScenario.PDF))
        }
    }
}
