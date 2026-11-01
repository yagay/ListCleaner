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

    @Test fun foreignProfileDoesNotShowThisUsersCandidates() {
        val foreign = request.copy(userId = 10)
        val unavailable = SystemProbeResult(0, 0, supported = false)
        assertEquals(PreviewV2Source.UNAVAILABLE,
            SystemPreviewV2.resolve(foreign, unavailable, now).source)
    }
}
