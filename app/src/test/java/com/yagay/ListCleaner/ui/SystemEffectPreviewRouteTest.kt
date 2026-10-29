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

    @Test fun providerManagerOnlyQueriesRegisteredPlatformSettings() {
        assertEquals(
            "cmd package query-activities --brief --components --user 0 -a android.settings.CREDENTIAL_PROVIDER",
            ProviderSettingsBridge.discoverCommand(0)
        )
        assertEquals(
            "cmd package list packages -s --user 10",
            ProviderSettingsBridge.systemPackageQueryCommand(10)
        )
        assertFalse(ProviderSettingsBridge.discoverCommand(0)
            .contains("REQUEST_SET_AUTOFILL_SERVICE"))
    }

    @Test fun providerManagerRejectsUnregisteredOrNonSystemSettingsHandlers() {
        val output = """
            com.evil.settings/.CredentialPicker
            com.android.settings/.Settings${'$'}CredentialManagerActivity
            com.oplus.settings/.CredentialsSettings
        """.trimIndent()
        assertEquals(
            "com.android.settings/.Settings\$CredentialManagerActivity",
            ProviderSettingsBridge.selectRegisteredComponent(
                output, setOf("com.android.settings", "com.oplus.settings")
            )
        )
        assertEquals(
            null,
            ProviderSettingsBridge.selectRegisteredComponent(
                "com.evil.settings/.CredentialPicker", setOf("com.android.settings")
            )
        )
    }

    @Test fun providerManagerShellLaunchQuotesNestedActivitiesAndVerifiesAmResult() {
        val component = "com.android.settings/.Settings\$CredentialManagerActivity"
        assertEquals(
            "am start -W --user 10 -n '$component' -a android.settings.CREDENTIAL_PROVIDER",
            ProviderSettingsBridge.launchCommand(10, component)
        )
        assertTrue(ProviderSettingsBridge.launchAccepted(
            "Status: ok\nActivity: $component", component
        ))
        assertFalse(ProviderSettingsBridge.launchAccepted(
            "Status: ok\nActivity: com.example/.Settings", component
        ))
        assertFalse(ProviderSettingsBridge.launchAccepted(
            "Status: error\nActivity: $component", component
        ))
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
}CredentialManagerActivity
            com.oplus.settings/.CredentialsSettings
        """.trimIndent()
        assertEquals(
            "com.android.settings/.Settings\$CredentialManagerActivity",
            ProviderSettingsBridge.selectRegisteredComponent(
                output, setOf("com.android.settings", "com.oplus.settings")
            )
        )
        assertEquals(
            null,
            ProviderSettingsBridge.selectRegisteredComponent(
                "com.evil.settings/.CredentialPicker", setOf("com.android.settings")
            )
        )
    }

    @Test fun providerManagerShellLaunchQuotesNestedActivitiesAndVerifiesAmResult() {
        val component = "com.android.settings/.Settings\$CredentialManagerActivity"
        assertEquals(
            "am start -W --user 10 -n '$component' -a android.settings.CREDENTIAL_PROVIDER",
            ProviderSettingsBridge.launchCommand(10, component)
        )
        assertTrue(ProviderSettingsBridge.launchAccepted(
            "Status: ok\nActivity: $component", component
        ))
        assertFalse(ProviderSettingsBridge.launchAccepted(
            "Status: ok\nActivity: com.example/.Settings", component
        ))
        assertFalse(ProviderSettingsBridge.launchAccepted(
            "Status: error\nActivity: $component", component
        ))
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
