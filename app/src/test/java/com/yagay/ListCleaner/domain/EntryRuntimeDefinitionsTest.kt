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

    @Test fun packageManagerServiceSurfacesExposeResidualSystemCallerRisk() {
        assertTrue(IntentKind.INPUT_METHOD.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.ACCESSIBILITY.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.DOCUMENT_PROVIDER.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.ASSISTANT.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.SHARE.runtimeDefinition()!!.systemCallerBypassPossible)
    }

    @Test fun emptyResultBehaviorAllowsExplicitlyCleanableEntrySurfacesToBecomeEmpty() {
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
            IntentKind.INPUT_METHOD.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.PACKAGE_MANAGER_SERVICE]
        )
    }
}
