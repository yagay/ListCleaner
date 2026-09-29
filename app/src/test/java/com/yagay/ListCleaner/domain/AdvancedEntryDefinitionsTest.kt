package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEntryDefinitionsTest {
    @Test
    fun serviceActionsAreUniqueAndKindsAreAdvanced() {
        assertEquals(
            SYSTEM_SERVICE_ENTRY_DEFINITIONS.size,
            SYSTEM_SERVICE_ENTRY_DEFINITIONS.map { it.action }.toSet().size,
        )
        SYSTEM_SERVICE_ENTRY_DEFINITIONS.forEach { definition ->
            assertEquals(EntryGroup.ADVANCED, definition.kind.entryGroup())
            assertEquals(definition.kind, systemServiceEntryKind(definition.action))
            assertFalse(definition.action.isBlank())
        }
    }

    @Test
    fun autofillAcceptsCurrentAndLegacyBindPermissions() {
        val definition = SYSTEM_SERVICE_ENTRY_DEFINITIONS.single { it.kind == IntentKind.AUTOFILL }
        assertTrue(definition.acceptsPermission("android.permission.BIND_AUTOFILL_SERVICE"))
        assertTrue(definition.acceptsPermission("android.permission.BIND_AUTOFILL"))
        assertFalse(definition.acceptsPermission("android.permission.BIND_ACCESSIBILITY_SERVICE"))
    }

    @Test
    fun syntheticShortcutKeysAreStableAndSeparatedBySurface() {
        val first = SyntheticEntryKeys.shortcutItemClass("a.b.Main", "chat/123")
        val same = SyntheticEntryKeys.shortcutItemClass("a.b.Main", "chat/123")
        val direct = SyntheticEntryKeys.directShareClass("a.b.Main", "chat/123")
        assertEquals(first, same)
        assertNotEquals(first, direct)
        assertTrue(first.contains("#shortcut#"))
        assertTrue(direct.contains("#direct#"))
    }

    @Test
    fun legacyLauncherShortcutSurfaceIsNotSelectableOrRuntimeFiltered() {
        assertFalse(IntentKind.LAUNCHER_SHORTCUT.isSelectableEntryKind())
        assertFalse(IntentKind.LAUNCHER_SHORTCUT.isSpecialEntrySurface())
        assertTrue(IntentKind.SHORTCUT_ITEM.isSelectableEntryKind())
        assertTrue(IntentKind.SHORTCUT_ITEM.isSpecialEntrySurface())
    }
}
