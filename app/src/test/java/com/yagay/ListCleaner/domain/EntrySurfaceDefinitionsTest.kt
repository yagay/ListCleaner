package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySurfaceDefinitionsTest {
    @Test fun everySelectableKindHasSurfaceDefinition() {
        val selectable = IntentKind.entries.filter { it.isSelectableEntryKind() }.toSet()
        assertEquals(selectable, ENTRY_SURFACE_DEFINITIONS.keys)
    }

    @Test fun packageLevelAndroidSurfacesUsePackageIdentity() {
        val packageKinds = setOf(
            IntentKind.ASSISTANT,
            IntentKind.HOME,
            IntentKind.BROWSER,
            IntentKind.CALL_SCREENING,
            IntentKind.VPN,
            IntentKind.AUTOFILL,
            IntentKind.CREDENTIAL_PROVIDER,
        )
        packageKinds.forEach { kind ->
            assertEquals("$kind", EntryIdentityScope.PACKAGE, kind.surfaceDefinition()!!.identity)
        }
    }

    @Test fun managerBackedKindsDeclareTheirAndroidAuthority() {
        assertEquals(EntryAuthority.INPUT_METHOD_MANAGER, IntentKind.INPUT_METHOD.entryAuthority())
        assertEquals(EntryAuthority.ACCESSIBILITY_MANAGER, IntentKind.ACCESSIBILITY.entryAuthority())
        assertEquals(EntryAuthority.PRINT_MANAGER, IntentKind.PRINT.entryAuthority())
        assertEquals(EntryAuthority.VPN_APP_OPS, IntentKind.VPN.entryAuthority())
        assertEquals(EntryAuthority.CREDENTIAL_MANAGER, IntentKind.CREDENTIAL_PROVIDER.entryAuthority())
        assertEquals(EntryAuthority.COMBINED_PROVIDER_SETTINGS, IntentKind.AUTOFILL.entryAuthority())
        assertEquals(EntryAuthority.NFC_CARD_EMULATION, IntentKind.NFC_HCE.entryAuthority())
    }

    @Test fun accessibilityDeclaresManagerAndShortcutSources() {
        val sources = IntentKind.ACCESSIBILITY.surfaceDefinition()!!.discoverySources
        assertTrue(EntryDiscoverySource.ACCESSIBILITY_MANAGER in sources)
        assertTrue(EntryDiscoverySource.ACCESSIBILITY_SHORTCUT_ACTIVITY in sources)
    }

    @Test fun observedDynamicSurfacesKeepRuntimeItemIdentity() {
        assertEquals(EntryIdentityScope.RUNTIME_ITEM, IntentKind.DIRECT_SHARE.surfaceDefinition()!!.identity)
        assertEquals(EntryIdentityScope.RUNTIME_ITEM, IntentKind.SHORTCUT_ITEM.surfaceDefinition()!!.identity)
    }

    @Test fun logicalNormalizationCollapsesPackageAuthorityComponents() {
        val first = ComponentCandidate(
            ComponentRule(IntentKind.VPN, "com.example.vpn", "com.example.vpn.One"),
            "VPN",
            "One",
            evidence = listOf("one"),
        )
        val second = ComponentCandidate(
            ComponentRule(IntentKind.VPN, "com.example.vpn", "com.example.vpn.Two"),
            "VPN",
            "Two",
            evidence = listOf("two"),
        )

        val normalized = normalizeLogicalCandidates(listOf(first, second))

        assertEquals(1, normalized.size)
        assertEquals(SyntheticEntryKeys.packageScopedRule(IntentKind.VPN, "com.example.vpn"), normalized.single().rule)
        assertTrue("one" in normalized.single().evidence)
        assertTrue("two" in normalized.single().evidence)
    }
}
