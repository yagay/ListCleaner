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

    @Test fun assistantRolePathIsCoveredButOtherKnownRolePathsAreVisibleAsGaps() {
        assertTrue(IntentKind.ASSISTANT.runtimeDefinition()!!.missingPaths.isEmpty())
        assertTrue(EntryRuntimePath.ROLE_CONTROLLER in IntentKind.HOME.runtimeDefinition()!!.missingPaths)
        assertTrue(EntryRuntimePath.ROLE_CONTROLLER in IntentKind.BROWSER.runtimeDefinition()!!.missingPaths)
        assertTrue(EntryRuntimePath.ROLE_CONTROLLER in IntentKind.CALL_SCREENING.runtimeDefinition()!!.missingPaths)
    }

    @Test fun packageManagerServiceSurfacesExposeSystemCallerRisk() {
        assertTrue(IntentKind.INPUT_METHOD.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.ACCESSIBILITY.runtimeDefinition()!!.systemCallerBypassPossible)
        assertTrue(IntentKind.DOCUMENT_PROVIDER.runtimeDefinition()!!.systemCallerBypassPossible)
        assertFalse(IntentKind.SHARE.runtimeDefinition()!!.systemCallerBypassPossible)
    }

    @Test fun emptyResultBehaviorMatchesCurrentSafetyGuards() {
        assertEquals(
            EmptyResultBehavior.ALLOW_EMPTY,
            IntentKind.PROCESS_TEXT.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.RESOLVER_ACTIVITY]
        )
        assertEquals(
            EmptyResultBehavior.RESTORE_ORIGINAL,
            IntentKind.SHARE.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.RESOLVER_ACTIVITY]
        )
        assertEquals(
            EmptyResultBehavior.RESTORE_ORIGINAL,
            IntentKind.SHORTCUT_ITEM.runtimeDefinition()!!.emptyBehavior[EntryRuntimePath.SHORTCUT_SERVICE]
        )
    }
}
