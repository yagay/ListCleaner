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
        listOf(IntentKind.ACCESSIBILITY,
            IntentKind.PRINT, IntentKind.VPN, IntentKind.NFC_HCE,
            IntentKind.NOTIFICATION_LISTENER, IntentKind.DREAM,
            IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER)
            .forEach { assertEquals(it.name, SystemPreviewRoute.SETTINGS, systemPreviewRoute(it)) }
    }

    @Test fun keyboardSwitchIsNativePickerWithSeparateSettings() {
        assertEquals(SystemPreviewRoute.INPUT_METHOD_PICKER,
            systemPreviewRoute(IntentKind.INPUT_METHOD))
        assertEquals(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.INPUT_METHOD).first())
    }

    @Test fun defaultAppFallbackActionsAreNotIntentCandidateActions() {
        assertEquals(android.provider.Settings.ACTION_HOME_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.HOME).first())
        assertEquals("android.intent.action.MANAGE_DEFAULT_APP",
            SystemEffectPreview.settingsActions(IntentKind.ASSISTANT).first())
        assertEquals(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.ASSISTANT)[1])
        assertEquals(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.ASSISTANT)[2])
        assertEquals(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.BROWSER).first())
        assertEquals(android.provider.Settings.ACTION_SECURITY_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.CREDENTIAL_PROVIDER).first())
        assertEquals(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            SystemEffectPreview.settingsActions(IntentKind.AUTOFILL).first())
        assertFalse(SystemEffectPreview.settingsActions(IntentKind.CREDENTIAL_PROVIDER).first() ==
            SystemEffectPreview.settingsActions(IntentKind.AUTOFILL).first())
        assertEquals(1, SystemEffectPreview.settingsActions(IntentKind.CREDENTIAL_PROVIDER).size)
        assertEquals(1, SystemEffectPreview.settingsActions(IntentKind.AUTOFILL).size)
    }

    @Test fun aospCombinedPickerIsTheSameRealSettingsPageForBothProviders() {
        assertEquals("com.android.settings", AospProviderSettingsRoute.SETTINGS_PACKAGE)
        assertEquals(
            "com.android.settings.applications.credentials.CredentialsPickerActivity",
            AospProviderSettingsRoute.pickerComponent(IntentKind.CREDENTIAL_PROVIDER)
        )
        assertEquals(AospProviderSettingsRoute.pickerComponent(IntentKind.AUTOFILL),
            AospProviderSettingsRoute.pickerComponent(IntentKind.CREDENTIAL_PROVIDER))
    }

    @Test fun providerSettingsFallbacksArePublicAndNeverImpersonateAProvider() {
        assertEquals(listOf(android.provider.Settings.ACTION_SECURITY_SETTINGS,
            android.provider.Settings.ACTION_SETTINGS),
            AospProviderSettingsRoute.fallbackActions(IntentKind.CREDENTIAL_PROVIDER))
        assertEquals(listOf(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            android.provider.Settings.ACTION_SETTINGS),
            AospProviderSettingsRoute.fallbackActions(IntentKind.AUTOFILL))
        val actions = IntentKind.entries.filter(AospProviderSettingsRoute::isProvider)
            .flatMap(AospProviderSettingsRoute::fallbackActions)
        assertFalse(actions.any { it.contains("CREDENTIAL_PROVIDER") ||
            it.contains("REQUEST_SET_AUTOFILL_SERVICE") })
    }

    @Test fun aospProviderRouteExcludesUnrelatedSystemLists() {
        assertTrue(AospProviderSettingsRoute.isProvider(IntentKind.CREDENTIAL_PROVIDER))
        assertTrue(AospProviderSettingsRoute.isProvider(IntentKind.AUTOFILL))
        assertFalse(AospProviderSettingsRoute.isProvider(IntentKind.ACCESSIBILITY))
        assertEquals(emptyList<String>(),
            AospProviderSettingsRoute.fallbackActions(IntentKind.ACCESSIBILITY))
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
