package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRegistryTest {
    @Test
    fun everySelectableEntryHasSurfaceAndRuntimeDefinition() {
        IntentKind.entries.filter(IntentKind::isSelectableEntryKind).forEach { kind ->
            assertNotNull("surface definition missing for $kind", kind.surfaceDefinition())
            assertNotNull("runtime definition missing for $kind", kind.runtimeDefinition())
        }
    }

    @Test
    fun registryKeysAndEmptyBehaviorStaySelfConsistent() {
        ENTRY_SURFACE_DEFINITIONS.forEach { (kind, definition) ->
            assertEquals(kind, definition.kind)
        }
        ENTRY_RUNTIME_DEFINITIONS.forEach { (kind, definition) ->
            assertEquals(kind, definition.kind)
            assertTrue(definition.expectedPaths.isNotEmpty())
            assertTrue(definition.emptyBehavior.keys.all { it in definition.expectedPaths })
        }
    }

    @Test
    fun packageScopedAuthoritiesUsePackageIdentity() {
        setOf(
            IntentKind.BROWSER,
            IntentKind.HOME,
            IntentKind.ASSISTANT,
            IntentKind.VPN,
            IntentKind.AUTOFILL,
            IntentKind.CREDENTIAL_PROVIDER,
            IntentKind.CALL_SCREENING,
        ).forEach { kind ->
            assertEquals(EntryIdentityScope.PACKAGE, requireNotNull(kind.surfaceDefinition()).identity)
        }
    }

    @Test
    fun directShareDeclaresEveryImplementedRuntimePath() {
        val definition = requireNotNull(IntentKind.DIRECT_SHARE.runtimeDefinition())
        assertTrue(EntryRuntimePath.DIRECT_SHARE_CHOOSER in definition.expectedPaths)
        assertTrue(EntryRuntimePath.DIRECT_SHARE_EMBEDDED in definition.expectedPaths)
        assertTrue(EntryRuntimePath.SHORTCUT_SERVICE in definition.expectedPaths)
    }
}
