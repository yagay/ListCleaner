package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemEffectPreviewRouteTest {
    @Test fun actualAndroidChooserIsUsedForShareAndOpenEntries() {
        listOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE,
            IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.BROWSER,
            IntentKind.DIAL, IntentKind.PROCESS_TEXT, IntentKind.HOME
        ).forEach {
            assertEquals(it.name, SystemPreviewRoute.CHOOSER, systemPreviewRoute(it))
        }
    }

    @Test fun storageAndPhotoEntriesOpenActualSystemPickers() {
        listOf(IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_PROVIDER,
            IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE
        ).forEach {
            assertEquals(it.name, SystemPreviewRoute.PICKER, systemPreviewRoute(it))
        }
    }

    @Test fun privilegedSubsystemsAreNotMisreportedAsChooserResults() {
        listOf(IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY,
            IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.PRINT
        ).forEach {
            assertEquals(it.name, SystemPreviewRoute.SETTINGS, systemPreviewRoute(it))
        }
        listOf(IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER,
            IntentKind.SHORTCUT_ITEM
        ).forEach {
            assertEquals(it.name, SystemPreviewRoute.UNAVAILABLE, systemPreviewRoute(it))
        }
    }

    @Test fun rootTileAndWidgetDoNotPretendToHavePublicPickers() {
        assertEquals(SystemPreviewRoute.CHOOSER, systemPreviewRoute(CleanupKind.LAUNCHER))
        assertEquals(SystemPreviewRoute.CHOOSER, systemPreviewRoute(CleanupKind.SHORTCUT))
        assertEquals(SystemPreviewRoute.UNAVAILABLE, systemPreviewRoute(CleanupKind.TILE))
        assertEquals(SystemPreviewRoute.UNAVAILABLE, systemPreviewRoute(CleanupKind.WIDGET))
    }
}
