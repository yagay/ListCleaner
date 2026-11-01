package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full registry & provenance regression; device-specific hook behaviour is tested separately. */
class PreviewContractTest {
    private val now = 300_000L

    @Test fun allEntryAndRootKindsHaveExactlyOneExplicitPreviewContract() {
        IntentKind.entries.forEach { kind ->
            val contract = PreviewContracts.forEntry(kind)
            assertEquals(kind.name, kind.isPackageScopedEntryForTest(), contract.packageScoped)
            assertTrue(kind.name, contract.adapter in PreviewAdapter.entries)
            assertTrue(kind.name, contract.nativeAction in PreviewNativeAction.entries)
        }
        CleanupKind.entries.forEach { kind ->
            val contract = PreviewContracts.forRoot(kind)
            assertEquals(PreviewAdapter.ROOT_COMPONENT_STATE, contract.adapter)
            assertFalse(contract.supportsLiveSystemList)
        }
        assertEquals(35, IntentKind.entries.size)
        assertEquals(6, CleanupKind.entries.size)
    }

    @Test fun wrongPickerForRegistryAndPrivateServicesIsNotExposed() {
        listOf(IntentKind.DOCUMENT_PROVIDER, IntentKind.PROCESS_TEXT,
            IntentKind.SHORTCUT_ITEM, IntentKind.CREDENTIAL_PROVIDER).forEach {
            assertEquals(it.name, PreviewNativeAction.NONE,
                PreviewContracts.forEntry(it).nativeAction)
        }
        assertEquals(PreviewNativeAction.CHOOSER_TEST,
            PreviewContracts.forEntry(IntentKind.DIRECT_SHARE).nativeAction)
        assertEquals(PreviewNativeAction.INTENT_TEST,
            PreviewContracts.forEntry(IntentKind.WALLPAPER).nativeAction)
        listOf(IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
            IntentKind.CALL_SCREENING).forEach { kind ->
            assertEquals(kind.name, PreviewAdapter.ROLE_CONTROLLER,
                PreviewContracts.forEntry(kind).adapter)
            assertEquals(kind.name, PreviewNativeAction.SYSTEM_SETTINGS,
                PreviewContracts.forEntry(kind).nativeAction)
        }
    }

    @Test fun everyPublicCandidateCategoryRespectsSavedHideSelectedRules() {
        val excluded = setOf(IntentKind.CREDENTIAL_PROVIDER,
            IntentKind.LAUNCHER_SHORTCUT, IntentKind.SHORTCUT_ITEM,
            IntentKind.DIRECT_SHARE, IntentKind.DOCUMENT_ROOT)
        IntentKind.entries.filterNot { it in excluded }.forEach { kind ->
            val hidden = SystemProbeItem("Hidden", "org.example.hide", "org.example.hide.Entry")
            val shown = SystemProbeItem("Shown", "org.example.show", "org.example.show.Entry")
            val probe = SystemProbeResult(0, 0, items = listOf(hidden, shown),
                diagnosticCandidates = listOf(hidden, shown))
            val projected = SystemPreviewV2.resolve(
                SystemProbeRequest(entryKind = kind, userId = 0),
                probe, now, selected = setOf(ComponentRule(kind, hidden.packageName,
                    hidden.className)),
            )
            assertFalse(kind.name + " failed to hide saved rule",
                projected.items.any { it.packageName == hidden.packageName })
        }
    }

    @Test fun draftRemovalsReplaceRatherThanUnionSavedRuleForAnyKind() {
        val saved = setOf(ComponentRule(IntentKind.OPEN, "org.example.old", "Old"),
            ComponentRule(IntentKind.SHARE, "org.example.share", "Share"))
        val scoped = setOf(ComponentRule(IntentKind.OPEN, "org.example.new", "New"))
        val result = EffectivePreviewPolicy.selection(saved, scoped, IntentKind.OPEN)
        assertFalse(result.any { it.packageName == "org.example.old" })
        assertTrue(result.any { it.packageName == "org.example.new" })
        assertTrue(result.any { it.packageName == "org.example.share" })
    }

    @Test fun rootPreviewUsesScannedEnabledStateAndNeverResurrectsDisabledItems() {
        val active = SystemProbeItem("Active", "org.example.active", "Active")
        val disabled = SystemProbeItem("Disabled", "org.example.disabled", "Disabled")
        val snapshot = RootPreviewSnapshot(userId = 10, pendingDraft = false, items = listOf(
            RootPreviewTarget(CleanupKind.TILE, 10, active, true),
            RootPreviewTarget(CleanupKind.TILE, 10, disabled, false),
            RootPreviewTarget(CleanupKind.TILE, 0, disabled, true),
        ))
        val request = SystemProbeRequest(componentKind = CleanupKind.TILE, userId = 10)
        val result = SystemPreviewV2.resolve(request,
            SystemProbeResult(0, 0, supported = false), now, rootSnapshot = snapshot)
        assertEquals(PreviewV2Source.ROOT_STATE, result.source)
        assertEquals(listOf(active), result.items)
        val draft = SystemPreviewV2.resolve(request, null, now,
            rootSnapshot = snapshot.copy(pendingDraft = true))
        assertEquals(PreviewV2Source.ROOT_DRAFT_SIMULATION, draft.source)
        assertEquals(listOf(active), draft.items)
        assertTrue(SystemPreviewV2.resolve(request, null, now,
            rootSnapshot = snapshot.copy(userId = 0)).items.isEmpty())
    }

    @Test fun privateHistoryWorksWithoutPublicLiveRegistry() {
        val item = SystemProbeItem("Share target", "org.example", "@shortcut/target")
        val probe = SystemProbeResult(0, 0, supported = false,
            historicalItems = listOf(item))
        val request = SystemProbeRequest(entryKind = IntentKind.DIRECT_SHARE, userId = 0)
        val current = SystemPreviewV2.resolve(request, probe, now)
        assertEquals(PreviewV2Source.UNAVAILABLE, current.source)
        val history = SystemPreviewV2.resolve(request, probe, now,
            historyRequested = true)
        assertEquals(PreviewV2Source.HISTORY, history.source)
        assertEquals(listOf(item), history.items)
    }

    @Test fun roleEligibilityIsSharedAcrossHomeBrowserAssistantAndCallScreening() {
        for (kind in listOf(IntentKind.HOME, IntentKind.BROWSER,
            IntentKind.ASSISTANT, IntentKind.CALL_SCREENING)) {
            val snapshot = RuntimeInterfaceSnapshot(
                kind = kind.name, source = "ROLE_CONTROLLER_RETURN",
                callerUserId = 0, callerUid = 10080,
                callerPackage = "com.google.android.permissioncontroller",
                requestAction = "android.app.role." + kind.name,
                capturedAtMillis = now - 100L,
                packages = listOf("org.example.one", "org.example.two"),
            )
            val result = SystemPreviewV2.resolve(
                SystemProbeRequest(entryKind = kind, userId = 0),
                SystemProbeResult(0, 0, roleEligibilityReturn = snapshot),
                now,
            )
            assertEquals(kind.name, PreviewV2Source.ROLE_ELIGIBILITY, result.source)
            assertEquals(kind.name, 2, result.items.size)
        }
    }

    private fun IntentKind.isPackageScopedEntryForTest(): Boolean =
        com.yagay.ListCleaner.domain.ENTRY_SURFACE_DEFINITIONS[this]?.identity ==
            com.yagay.ListCleaner.domain.EntryIdentityScope.PACKAGE
}
