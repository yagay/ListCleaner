package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPreviewAuthorityTest {
    @Test fun everySelectableIntentHasOneExplicitAuthority() {
        // Exhaustive when compiles against every enum entry; this test catches future drift.
        val grouped = IntentKind.entries.groupBy(IntentKind::previewAuthority)
        assertEquals(IntentKind.entries.size, grouped.values.sumOf { it.size })
        assertTrue(grouped.values.all { it.isNotEmpty() })
    }

    @Test fun contentPickerAndRootAreNotResolvedActivityLists() {
        for (kind in listOf(
            IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
            IntentKind.DOCUMENT_ROOT,
        )) {
            assertEquals(PreviewAuthority.PICKER_PRIVATE, kind.previewAuthority())
            assertFalse(kind.previewAuthority().hasPublicFinalPickerList())
        }
        assertEquals(PreviewAuthority.PROVIDER_CANDIDATES,
            IntentKind.DOCUMENT_PROVIDER.previewAuthority())
        assertEquals(PreviewAuthority.PROVIDER_CANDIDATES,
            IntentKind.PHOTO_SOURCE.previewAuthority())
    }

    @Test fun onlyManagerEnabledQueriesMayClaimEnabledEntries() {
        val expected = setOf(IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY)
        assertEquals(expected,
            IntentKind.entries.filter { it.previewAuthority().hasPublicEnabledList() }.toSet())
    }

    @Test fun rolesServicesShortcutsAndOtherHostsAreNotFinalLists() {
        for (kind in listOf(
            IntentKind.HOME, IntentKind.ASSISTANT, IntentKind.BROWSER, IntentKind.CALL_SCREENING,
        )) assertEquals(PreviewAuthority.ROLE_CANDIDATES, kind.previewAuthority())
        for (kind in listOf(
            IntentKind.PRINT, IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER,
            IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
            IntentKind.WALLPAPER, IntentKind.DREAM,
        )) assertEquals(PreviewAuthority.SERVICE_CANDIDATES, kind.previewAuthority())
        for (kind in listOf(
            IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT,
        )) assertEquals(PreviewAuthority.SHORTCUT_PRIVATE, kind.previewAuthority())
        assertTrue(CleanupKind.entries.all {
            it.previewAuthority() == PreviewAuthority.HOST_CANDIDATES
        })
    }

    @Test fun resolverMatchesAreCandidatesNotVerifiedDisplayedItems() {
        for (kind in listOf(
            IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.SEND_TO,
            IntentKind.OPEN, IntentKind.DEEP_LINK, IntentKind.DIAL,
            IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
            IntentKind.PROCESS_TEXT,
        )) {
            assertEquals(PreviewAuthority.RESOLVER_CANDIDATES, kind.previewAuthority())
            assertFalse(kind.previewAuthority().hasPublicEnabledList())
        }
    }

    @Test fun requestMatchingRemainsProfileAndComponentSpecific() {
        val request = SystemProbeRequest(
            entryKind = IntentKind.SHARE, userId = 10,
            focusPackage = "com.example.a", focusClass = "com.example.a.Share"
        )
        assertTrue(request.matches(SystemProbeItem("Share", "com.example.a", "com.example.a.Share")))
        assertFalse(request.matches(SystemProbeItem("Share", "com.example.a", "com.example.a.Other")))
        assertFalse(request.matches(SystemProbeItem("Share", "com.example.b", "com.example.a.Share")))
    }
}
