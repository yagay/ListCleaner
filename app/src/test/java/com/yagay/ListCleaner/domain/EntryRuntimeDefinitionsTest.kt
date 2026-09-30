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

    @Test fun knownRolePathsDeclareSharedRoleControllerRuntime() {
        val assistant = IntentKind.ASSISTANT.runtimeDefinition()!!
        assertTrue(EntryRuntimePath.RESOLVER_ACTIVITY in assistant.expectedPaths)
        assertTrue(EntryRuntimePath.ROLE_CONTROLLER in assistant.expectedPaths)
        assertTrue(EntryRuntimePath.PACKAGE_MANAGER_SERVICE in assistant.expectedPaths)

        listOf(IntentKind.HOME, IntentKind.BROWSER).forEach { kind ->
            val definition = kind.runtimeDefinition()!!
            assertTrue(EntryRuntimePath.RESOLVER_ACTIVITY in definition.expectedPaths)
            assertTrue(EntryRuntimePath.ROLE_CONTROLLER in definition.expectedPaths)
        }

        val callScreening = IntentKind.CALL_SCREENING.runtimeDefinition()!!
        assertTrue(EntryRuntimePath.PACKAGE_MANAGER_SERVICE in callScreening.expectedPaths)
        assertTrue(EntryRuntimePath.ROLE_CONTROLLER in callScreening.expectedPaths)
    }

    @Test fun finalSystemAuthoritiesAreDeclaredAsExpectedPaths() {
        assertTrue(EntryRuntimePath.ACCESSIBILITY_MANAGER in IntentKind.ACCESSIBILITY.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.INPUT_METHOD_MANAGER in IntentKind.INPUT_METHOD.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.PRINT_MANAGER in IntentKind.PRINT.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.SETTINGS_VPN in IntentKind.VPN.runtimeDefinition()!!.expectedPaths)
        assertFalse(EntryRuntimePath.VPN_APP_OPS in IntentKind.VPN.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.CREDENTIAL_MANAGER in IntentKind.CREDENTIAL_PROVIDER.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.COMBINED_PROVIDER_SETTINGS in IntentKind.AUTOFILL.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.SETTINGS_AUTOFILL_PICKER in IntentKind.AUTOFILL.runtimeDefinition()!!.expectedPaths)
        assertTrue(EntryRuntimePath.NFC_CARD_EMULATION in IntentKind.NFC_HCE.runtimeDefinition()!!.expectedPaths)
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
