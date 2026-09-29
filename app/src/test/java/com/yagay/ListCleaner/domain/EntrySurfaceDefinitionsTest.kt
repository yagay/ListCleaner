package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySurfaceDefinitionsTest {
    @Test fun everySelectableKindHasSurfaceDefinition() {
        val selectable = IntentKind.entries.filter { it.isSelectableEntryKind() }.toSet()
        assertEquals(selectable, ENTRY_SURFACE_DEFINITIONS.keys)
    }

    @Test fun roleKindsUsePackageIdentity() {
        assertEquals(EntryIdentityScope.PACKAGE, IntentKind.ASSISTANT.surfaceDefinition()!!.identity)
        assertEquals(EntryIdentityScope.PACKAGE, IntentKind.HOME.surfaceDefinition()!!.identity)
        assertEquals(EntryIdentityScope.PACKAGE, IntentKind.BROWSER.surfaceDefinition()!!.identity)
        assertEquals(EntryIdentityScope.PACKAGE, IntentKind.CALL_SCREENING.surfaceDefinition()!!.identity)
    }

    @Test fun accessibilityDeclaresBothDiscoverySources() {
        val sources = IntentKind.ACCESSIBILITY.surfaceDefinition()!!.discoverySources
        assertTrue(EntryDiscoverySource.SYSTEM_SERVICE in sources)
        assertTrue(EntryDiscoverySource.ACCESSIBILITY_SHORTCUT_ACTIVITY in sources)
    }

    @Test fun observedDynamicSurfacesKeepRuntimeItemIdentity() {
        assertEquals(EntryIdentityScope.RUNTIME_ITEM, IntentKind.DIRECT_SHARE.surfaceDefinition()!!.identity)
        assertEquals(EntryIdentityScope.RUNTIME_ITEM, IntentKind.SHORTCUT_ITEM.surfaceDefinition()!!.identity)
    }

    @Test fun logicalNormalizationCollapsesPackageRoleComponents() {
        val first = ComponentCandidate(
            ComponentRule(IntentKind.HOME, "com.example.home", "com.example.home.One"),
            "Home",
            "One",
            evidence = listOf("one"),
        )
        val second = ComponentCandidate(
            ComponentRule(IntentKind.HOME, "com.example.home", "com.example.home.Two"),
            "Home",
            "Two",
            evidence = listOf("two"),
        )

        val normalized = normalizeLogicalCandidates(listOf(first, second))

        assertEquals(1, normalized.size)
        assertEquals(SyntheticEntryKeys.packageScopedRule(IntentKind.HOME, "com.example.home"), normalized.single().rule)
        assertTrue("one" in normalized.single().evidence)
        assertTrue("two" in normalized.single().evidence)
    }
}
