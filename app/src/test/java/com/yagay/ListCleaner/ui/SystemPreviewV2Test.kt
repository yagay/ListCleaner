package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the replacement engine, not the retired preview fallback graph. */
class SystemPreviewV2Test {
    private val kind = IntentKind.SHARE
    private val request = SystemProbeRequest(entryKind = kind, userId = 0,
        scenario = SystemProbeScenario.TEXT)
    private val first = SystemProbeItem("A", "com.example.a", "com.example.a.Share")
    private val second = SystemProbeItem("B", "com.example.b", "com.example.b.Share")
    private val inventory = SystemProbeResult(0, 0, items = listOf(first, second))
    private val now = 50_000L
    private val action = "android.intent.action.SEND"

    private fun capture(
        actionValue: String = action, mime: String = "text/plain",
        host: String = "", at: Long = now - 50L, user: Int = 0,
        source: String = "CHOOSER_ADAPTER_ORDER",
    ) = RuntimeInterfaceSnapshot(
        kind = kind.name, source = source, callerUserId = user,
        callerPackage = "com.android.intentresolver", callerUid = 1000,
        requestAction = actionValue, requestMimeType = mime,
        requestHost = host, capturedAtMillis = at,
        packages = listOf("com.example.b", "com.example.a"),
        components = listOf(second.component, first.component),
    )

    private fun resolve(
        value: SystemProbeResult = inventory,
        launched: Long? = now - 500L,
        started: Long? = now - 500L,
        selected: Set<ComponentRule> = emptySet(),
        pending: Boolean = false,
        mime: String = "text/plain",
        host: String? = null,
    ): PreviewV2Result = SystemPreviewV2.resolve(
        request = request, probe = value, atMillis = now,
        queryStartedAt = started, systemLaunchedAt = launched,
        managerUid = 12345, expectedAction = action,
        expectedMime = mime, expectedHost = host,
        selected = selected, pendingDraft = pending,
    )

    @Test fun adapterCaptureWinsAndPreservesOrderOnlyWhenSameRequest() {
        val output = resolve(inventory.copy(orderedReturn = capture()))
        assertEquals(PreviewV2Source.ADAPTER_OBSERVED, output.source)
        assertEquals(listOf(second.component, first.component), output.items.map { it.component })
    }

    @Test fun anotherMimeOrHostCannotBecomeTheCurrentSystemList() {
        val image = inventory.copy(orderedReturn = capture(mime = "image/png"))
        assertEquals(PreviewV2Source.INTENT_CANDIDATES, resolve(image).source)
        val anotherHost = inventory.copy(orderedReturn = capture(host = "example.com"))
        assertEquals(PreviewV2Source.INTENT_CANDIDATES, resolve(anotherHost).source)
    }

    @Test fun oldDifferentProfileOrUnlaunchedCapturesAreNotCurrent() {
        for (captured in listOf(capture(user = 10), capture(at = now - 2000),
                capture().copy(truncated = true))) {
            assertEquals(PreviewV2Source.INTENT_CANDIDATES,
                resolve(inventory.copy(orderedReturn = captured), launched = now - 1000).source)
        }
        assertEquals(PreviewV2Source.INTENT_CANDIDATES,
            resolve(inventory.copy(orderedReturn = capture()), launched = null).source)
    }

    @Test fun filteredReturnNeedsExactIntentAndCallerUid() {
        val pm = capture(source = "PACKAGE_MANAGER_FILTERED_RETURN")
            .copy(callerUid = 12345)
        assertEquals(PreviewV2Source.FILTERED_OBSERVED,
            resolve(inventory.copy(runtimeReturn = pm)).source)
        assertEquals(PreviewV2Source.INTENT_CANDIDATES,
            resolve(inventory.copy(runtimeReturn = pm.copy(callerUid = 5555))).source)
        assertEquals(PreviewV2Source.INTENT_CANDIDATES,
            resolve(inventory.copy(runtimeReturn = pm.copy(requestMimeType = "image/png"))).source)
    }

    @Test fun pendingDraftCannotBeMislabelledAsActualSystemCapture() {
        val source = inventory.copy(orderedReturn = capture())
        val output = resolve(source, selected = setOf(ComponentRule(kind,
            first.packageName, first.className)), pending = true)
        assertEquals(PreviewV2Source.DRAFT_SIMULATION, output.source)
        assertEquals(listOf(second.component), output.items.map { it.component })
    }

    @Test fun queryEmptyStaysEmptyAndNeverUsesScanCache() {
        val empty = SystemProbeResult(0, 0, items = emptyList())
        val output = resolve(empty)
        assertEquals(PreviewV2Source.INTENT_CANDIDATES, output.source)
        assertTrue(output.items.isEmpty())
    }

    @Test fun historicalShortcutIsNotSilentlyPresentedAsCurrent() {
        val shortcut = SystemProbeResult(0, 0, historicalItems = listOf(first))
        val direct = SystemProbeRequest(entryKind = IntentKind.DIRECT_SHARE, userId = 0)
        val current = SystemPreviewV2.resolve(direct, shortcut, now)
        assertEquals(PreviewV2Source.UNAVAILABLE, current.source)
        val history = SystemPreviewV2.resolve(direct, shortcut, now, historyRequested = true)
        assertEquals(PreviewV2Source.HISTORY, history.source)
        assertEquals(first, history.items.single())
    }

    private fun assistantRoleSnapshot(
        source: String = "ROLE_CONTROLLER_RETURN",
        user: Int = 0,
        at: Long = now - 40,
        role: String = "android.app.role.ASSISTANT",
        process: String = "com.google.android.permissioncontroller",
        truncated: Boolean = false,
    ) = RuntimeInterfaceSnapshot(
        kind = IntentKind.ASSISTANT.name,
        source = source, callerUserId = user, callerUid = 10080,
        callerPackage = process, requestAction = role, capturedAtMillis = at,
        packages = listOf("com.example.google", "com.example.macro",
            "com.example.secure", "com.example.browser"),
        truncated = truncated,
    )

    private val assistantRequest = SystemProbeRequest(entryKind = IntentKind.ASSISTANT, userId = 0)
    private val assistantHolder = SystemProbeItem(
        "Google", "com.example.google", "@role/ASSISTANT")
    private val assistOnly = SystemProbeItem(
        "Google", "com.example.google", "com.example.google.AssistActivity")

    @Test fun assistantRoleEligibilityIncludesVoiceServiceAppsMissingFromActionAssist() {
        val response = SystemProbeResult(
            0, 0, items = listOf(assistantHolder), enabledItems = listOf(assistantHolder),
            diagnosticCandidates = listOf(assistOnly),
            roleEligibilityReturn = assistantRoleSnapshot(),
            // A matching ACTION_ASSIST return must not replace the eligible role apps.
            runtimeReturn = capture(source = "PACKAGE_MANAGER_FILTERED_RETURN")
                .copy(kind = "ASSISTANT", packages = listOf("com.example.google")),
        )
        val result = SystemPreviewV2.resolve(assistantRequest, response, now,
            systemLaunchedAt = now - 500, queryStartedAt = now - 500, managerUid = 12345,
            expectedAction = "android.intent.action.ASSIST")
        assertEquals(PreviewV2Source.ROLE_ELIGIBILITY, result.source)
        assertEquals(4, result.items.size)
        assertTrue(result.items.any { it.packageName == "com.example.macro" })
    }

    @Test fun assistantNeverTreatsUnrelatedOrStaleRoleCaptureAsSystemEligibility() {
        val incorrect = listOf(
            assistantRoleSnapshot(source = "PACKAGE_MANAGER_FILTERED_RETURN"),
            assistantRoleSnapshot(user = 10),
            assistantRoleSnapshot(at = now - 120_001L),
            assistantRoleSnapshot(role = "android.app.role.BROWSER"),
            assistantRoleSnapshot(process = "com.example.untrusted"),
            assistantRoleSnapshot(truncated = true),
        )
        for (capture in incorrect) {
            val response = SystemProbeResult(0, 0,
                items = listOf(assistantHolder),
                diagnosticCandidates = listOf(assistOnly),
                roleEligibilityReturn = capture)
            val result = SystemPreviewV2.resolve(assistantRequest, response, now)
            assertEquals(PreviewV2Source.ROLE_CANDIDATES, result.source)
            assertEquals(listOf("com.example.google"), result.items.map { it.packageName })
        }
    }

    @Test fun assistantExplicitlySeparatesHolderPossibleCandidatesAndPendingDraft() {
        val holdersOnly = SystemProbeResult(0, 0,
            items = listOf(assistantHolder), enabledItems = listOf(assistantHolder))
        assertEquals(PreviewV2Source.ROLE_HOLDER,
            SystemPreviewV2.resolve(assistantRequest, holdersOnly, now).source)
        val qualified = holdersOnly.copy(
            roleEligibilityReturn = assistantRoleSnapshot())
        val pending = SystemPreviewV2.resolve(
            assistantRequest, qualified, now, pendingDraft = true,
            selected = setOf(ComponentRule(IntentKind.ASSISTANT,
                "com.example.macro", "@role/ASSISTANT")))
        assertEquals(PreviewV2Source.DRAFT_SIMULATION, pending.source)
        assertTrue(pending.items.none { it.packageName == "com.example.macro" })
    }

    @Test fun savedAssistantCleanupFiltersRawFallbackWithoutPendingDraft() {
        val candidates = SystemProbeResult(0, 0, diagnosticCandidates = listOf(
            SystemProbeItem("Google", "com.example.google", "com.example.google.Assist"),
            SystemProbeItem("MacroDroid", "com.example.macro", "com.example.macro.VoiceService"),
            SystemProbeItem("Secure Settings", "com.example.secure", "com.example.secure.Assist"),
        ))
        val selected = setOf(ComponentRule(IntentKind.ASSISTANT,
            "com.example.macro", "@assistant"))
        val output = SystemPreviewV2.resolve(
            assistantRequest, candidates, now, selected = selected)
        assertEquals(PreviewV2Source.ROLE_CANDIDATES, output.source)
        assertEquals(listOf("com.example.google", "com.example.secure"),
            output.items.map { it.packageName })
    }

    @Test fun savedAssistantCleanupReprojectsStaleObservedRoleCapture() {
        val response = SystemProbeResult(0, 0,
            roleEligibilityReturn = assistantRoleSnapshot())
        val selected = setOf(ComponentRule(IntentKind.ASSISTANT,
            "com.example.macro", "@assistant"))
        val output = SystemPreviewV2.resolve(
            assistantRequest, response, now, selected = selected)
        assertEquals(PreviewV2Source.ROLE_POLICY_PROJECTED, output.source)
        assertTrue(output.items.none { it.packageName == "com.example.macro" })
        assertEquals(3, output.items.size)
    }

    @Test fun assistantDisplayModesAndOverridesRemainAuthoritative() {
        val response = SystemProbeResult(0, 0,
            roleEligibilityReturn = assistantRoleSnapshot())
        val selected = setOf(ComponentRule(IntentKind.ASSISTANT,
            "com.example.macro", "@assistant"))
        val shownOnly = SystemPreviewV2.resolve(
            assistantRequest, response, now, selected = selected,
            globalMode = DisplayMode.SHOW_SELECTED)
        assertEquals(listOf("com.example.macro"),
            shownOnly.items.map { it.packageName })
        val override = SystemPreviewV2.resolve(
            assistantRequest, response, now, selected = selected,
            globalMode = DisplayMode.SHOW_SELECTED,
            overrides = mapOf(IntentKind.ASSISTANT to DisplayMode.HIDE_SELECTED))
        assertTrue(override.items.none { it.packageName == "com.example.macro" })
        val showAll = SystemPreviewV2.resolve(
            assistantRequest, response, now, selected = selected,
            globalMode = DisplayMode.SHOW_ALL)
        assertEquals(4, showAll.items.size)
    }

    @Test fun assistantWithAllCandidatesRemovedMustStayEmpty() {
        val candidate = SystemProbeItem(
            "MacroDroid", "com.example.macro", "com.example.macro.VoiceService")
        val response = SystemProbeResult(0, 0,
            diagnosticCandidates = listOf(candidate))
        val output = SystemPreviewV2.resolve(
            assistantRequest, response, now,
            selected = setOf(ComponentRule(IntentKind.ASSISTANT,
                "com.example.macro", "@assistant")))
        assertEquals(PreviewV2Source.ROLE_CANDIDATES, output.source)
        assertTrue(output.items.isEmpty())
    }

    @Test fun foreignProfileDoesNotShowThisUsersCandidates() {
        val foreign = request.copy(userId = 10)
        val unavailable = SystemProbeResult(0, 0, supported = false)
        assertEquals(PreviewV2Source.UNAVAILABLE,
            SystemPreviewV2.resolve(foreign, unavailable, now).source)
    }
}
