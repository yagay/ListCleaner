package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEffectPreviewRouteTest {
    @Test fun intentionalSharesAreTheOnlyForcedChoosers() {
        listOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE)
            .forEach { assertEquals(it.name, SystemPreviewRoute.CHOOSER, systemPreviewRoute(it)) }
    }

    @Test fun standaloneIntentsDoNotForceAnUnrelatedChooser() {
        listOf(IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.DEEP_LINK,
            IntentKind.DIAL, IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO,
            IntentKind.RECORD_AUDIO, IntentKind.WALLPAPER)
            .forEach { assertEquals(it.name, SystemPreviewRoute.DIRECT, systemPreviewRoute(it)) }
    }

    @Test fun documentAndPhotoEntriesKeepActualSystemPickers() {
        listOf(IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_PROVIDER,
            IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE)
            .forEach { assertEquals(it.name, SystemPreviewRoute.PICKER, systemPreviewRoute(it)) }
    }

    @Test fun roleDefaultsOpenSettingsInsteadOfFakedDefaultAppChoosers() {
        listOf(IntentKind.BROWSER, IntentKind.HOME, IntentKind.ASSISTANT,
            IntentKind.CALL_SCREENING)
            .forEach { assertEquals(it.name, SystemPreviewRoute.SETTINGS, systemPreviewRoute(it)) }
        assertTrue(supportsScenarioTest(IntentKind.BROWSER))
        assertTrue(supportsScenarioTest(IntentKind.HOME))
        assertTrue(supportsScenarioTest(IntentKind.ASSISTANT))
        assertFalse(supportsScenarioTest(IntentKind.CALL_SCREENING))
    }

    @Test fun systemServicesUseSettingsWithExplicitLimitations() {
        listOf(IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY,
            IntentKind.PRINT, IntentKind.VPN, IntentKind.NFC_HCE,
            IntentKind.NOTIFICATION_LISTENER, IntentKind.DREAM,
            IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER)
            .forEach { assertEquals(it.name, SystemPreviewRoute.SETTINGS, systemPreviewRoute(it)) }
    }

    @Test fun dynamicShortcutsAndTextSelectionAreNotMisrepresented() {
        assertEquals(SystemPreviewRoute.CONTEXT_MENU,
            systemPreviewRoute(IntentKind.PROCESS_TEXT))
        assertEquals(SystemPreviewRoute.UNAVAILABLE,
            systemPreviewRoute(IntentKind.SHORTCUT_ITEM))
        assertEquals(SystemPreviewRoute.UNAVAILABLE,
            systemPreviewRoute(IntentKind.LAUNCHER_SHORTCUT))
    }

    @Test fun rootWidgetHostIsTemporaryButTileEditorIsNotPublic() {
        assertEquals(SystemPreviewRoute.CHOOSER, systemPreviewRoute(CleanupKind.LAUNCHER))
        assertEquals(SystemPreviewRoute.CHOOSER, systemPreviewRoute(CleanupKind.SHORTCUT))
        assertEquals(SystemPreviewRoute.WIDGET_PICKER, systemPreviewRoute(CleanupKind.WIDGET))
        assertEquals(SystemPreviewRoute.UNAVAILABLE, systemPreviewRoute(CleanupKind.TILE))
    }

    @Test fun everySelectableKindHasOneContract() {
        assertEquals(IntentKind.entries.size - 1,
            IntentKind.entries.count { it != IntentKind.LAUNCHER_SHORTCUT })
        IntentKind.entries.forEach { kind ->
            assertTrue(systemPreviewRoute(kind) in SystemPreviewRoute.entries)
        }
    }
}
