package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRuntimeDefinitionsTest {
    @Test fun everySelectableKindHasRuntimeDefinition() {
        val selectable = IntentKind.entries.filter { it.isSelectableEntryKind() }.toSet()
        assertEquals(selectable, ENTRY_RUNTIME_DEFINITIONS.keys)
    }

    @Test fun knownRolePathsUseSharedRoleControllerCoverage() {
        assertTrue(IntentKind.ASSISTANT.runtimeDefinition()!!.missingPaths.isEmpty())
        assertTrue(IntentKind.HOME.runtimeDefinition()!!.missingPaths.isEmpty())
        assertTrue(IntentKind.BROWSER.runtimeDefinition()!!.missingPaths.isEmpty())
        assertTrue(IntentKind.CALL_SCREENING.runtimeDefinition()!!.missingPaths.isEmpty())
        assertTrue(EntryRuntimePath.PACKAGE_MANAGER_SERVICE in IntentKind.ASSISTANT.runtimeDefinition()!!.coveredPaths)
    }

    @Test fun finalSystemAuthoritiesAreRegistered() {
        assertTrue(EntryRuntimePath.ACCESSIBILITY_MANAGER in IntentKind.ACCESSIBILITY.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.INPUT_METHOD_MANAGER in IntentKind.INPUT_METHOD.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.PRINT_MANAGER in IntentKind.PRINT.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.SETTINGS_VPN in IntentKind.VPN.runtimeDefinition()!!.coveredPaths)
        assertFalse(EntryRuntimePath.VPN_APP_OPS in IntentKind.VPN.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.CREDENTIAL_MANAGER in IntentKind.CREDENTIAL_PROVIDER.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.COMBINED_PROVIDER_SETTINGS in IntentKind.AUTOFILL.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.SETTINGS_AUTOFILL_PICKER in IntentKind.AUTOFILL.runtimeDefinition()!!.coveredPaths)
        assertTrue(EntryRuntimePath.NFC_CARD_EMULATION in IntentKind.NFC_HCE.runtimeDefinition()!!.coveredPaths)
        listOf(
            IntentKind.ACCESSIBILITY,
            IntentKind.INPUT_METHOD,
            IntentKind.PRINT,
            IntentKind.VPN,
            IntentKind.CREDENTIAL_PROVIDER,
            IntentKind.AUTOFILL,
            IntentKind.NFC_HCE,
        ).forEach { assertTrue("$it", it.runtimeDefinition()!!.missingPaths.isEmpty()) }
    }

    @Test fun onlyResidualPackageManagerSurfacesExposeSystemCallerRisk() {
        assertFalse(IntentKind.INPUT_METHOD.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.ACCESSIBILITY.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.VPN.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.DOCUMENT_PROVIDER.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.NOTIFICATION_LISTENER.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.ASSISTANT.runtimeDefinition()!!.systemCallerBypassPossible)
    }

    @Test fun emptyResultBehaviorMatchesAuthoritySemantics() {
        assertEquals(
            EmptyResultBehavior.ALLOW_EMPTY,
            IntentKind.PROCESS_TEXT.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.RESOLVER_ACTIVITY]
        )
        assertEquals(
            EmptyResultBehavior.RESTORE_ORIGINAL,
            IntentKind.SHARE.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.RESOLVER_ACTIVITY]
        )
        assertEquals(
            EmptyResultBehavior.ALLOW_EMPTY,
            IntentKind.SHORTCUT_ITEM.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.SHORTCUT_SERVICE]
        )
        assertEquals(
            EmptyResultBehavior.ALLOW_EMPTY,
            IntentKind.VPN.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.SETTINGS_VPN]
        )
        assertEquals(
            EmptyResultBehavior.ALLOW_EMPTY,
            IntentKind.NFC_HCE.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.NFC_CARD_EMULATION]
        )
    }
}
