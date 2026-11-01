package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.DirectShareType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPreviewProjectionTest {
    private val kind = IntentKind.OPEN
    private val a = ComponentRule(kind, "com.test.a", "com.test.a.Main")
    private val b = ComponentRule(kind, "com.test.b", "com.test.b.Main")
    private val candidates = listOf(
        ComponentCandidate(a, "A", "Activity A", evidence = listOf(
            "VIEW scheme=content mime=text/plain sample=a.txt",
            "VIEW scheme=content mime=application/pdf sample=a.pdf",
        )),
        ComponentCandidate(b, "B", "Activity B", evidence = listOf(
            "VIEW scheme=content mime=text/plain sample=b.txt",
            "VIEW scheme=content mime=application/pdf sample=b.pdf",
        )),
    )
    private fun estimate(
        saved: Set<ComponentRule>,
        mode: DisplayMode = DisplayMode.HIDE_SELECTED,
        overrides: Map<IntentKind, DisplayMode> = emptyMap(),
    ) = SystemPreviewProjection.estimate(kind, candidates, saved, mode, overrides, null)!!

    @Test fun hidingOneSelectionShowsOnlyTheOther() {
        assertEquals(listOf("com.test.b"), estimate(setOf(a)).items.map { it.packageName })
        assertEquals(PreviewListSource.ESTIMATED, estimate(setOf(a)).source)
    }
    @Test fun showSelectedUsesConfirmedRules() {
        assertEquals(listOf("com.test.a"),
            estimate(setOf(a), DisplayMode.SHOW_SELECTED).items.map { it.packageName })
    }
    @Test fun noSelectedRulesFailOpen() {
        assertEquals(2, estimate(emptySet(), DisplayMode.SHOW_SELECTED).items.size)
    }
    @Test fun perCategoryOverrideWins() {
        assertEquals(2, estimate(setOf(a), DisplayMode.HIDE_SELECTED,
            mapOf(kind to DisplayMode.SHOW_ALL)).items.size)
    }
    @Test fun noCandidatesDoesNotInventAny() {
        assertNull(SystemPreviewProjection.estimate(kind, emptyList(), setOf(a),
            DisplayMode.HIDE_SELECTED, emptyMap(), null))
    }
    @Test fun otherKindsDoNotSelectOpen() {
        val other = ComponentRule(IntentKind.SHARE, "com.test.a", "com.test.a.Main")
        assertEquals(2, estimate(setOf(other)).items.size)
    }
    @Test fun estimatedUsesScenarioApiOrderInsteadOfAlphabetizedCatalog() {
        val queried = SystemProbeResult(
            sourceRes = 0, explanationRes = 0,
            items = listOf(
                SystemProbeItem("B", "com.test.b", "com.test.b.Main"),
                SystemProbeItem("A", "com.test.a", "com.test.a.Main"),
            ),
        )
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), queried,
        )!!
        assertEquals(listOf("com.test.b", "com.test.a"),
            result.items.map { it.packageName })
    }

    @Test fun estimatedGenericPriorityMatchesStableRuntimePromotion() {
        val queried = SystemProbeResult(
            0, 0, items = listOf(
                SystemProbeItem("B", "com.test.b", "com.test.b.Main"),
                SystemProbeItem("A", "com.test.a", "com.test.a.Main"),
            ),
        )
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.HIDE_SELECTED, emptyMap(), queried,
            priorities = PriorityConfig(apps = mapOf(kind to listOf("com.test.a"))),
        )!!
        assertEquals(listOf("com.test.a", "com.test.b"),
            result.items.map { it.packageName })
    }

    @Test fun typedPdfPriorityOverridesGenericAndScenarioTextUsesGeneric() {
        val scanned = listOf(candidates[1], candidates[0])
        val generic = PriorityConfig(apps = mapOf(kind to listOf("com.test.a")))
        val typed = OpenTypeConfig(priorities = mapOf(OpenPreset.PDF to listOf("com.test.b")))
        fun preview(scenario: SystemProbeScenario) = SystemPreviewProjection.estimate(
            kind, scanned, emptySet(), DisplayMode.HIDE_SELECTED, emptyMap(), null,
            priorities = generic, openTypes = typed, scenario = scenario,
        )!!.items.map { it.packageName }
        assertEquals(listOf("com.test.b", "com.test.a"), preview(SystemProbeScenario.PDF))
        assertEquals(listOf("com.test.a", "com.test.b"), preview(SystemProbeScenario.TEXT))
    }

    @Test fun deepLinkHostPriorityOnlyAppliesForMatchingRequestHost() {
        val deep = IntentKind.DEEP_LINK
        val hostCandidates = listOf(
            ComponentCandidate(ComponentRule(deep, "com.test.a", "a.Main"), "A", "A"),
            ComponentCandidate(ComponentRule(deep, "com.test.b", "b.Main"), "B", "B"),
        )
        val config = BrowserLinkConfig(
            hosts = setOf("example.com"),
            priorities = mapOf("example.com" to listOf("com.test.b")),
        )
        // The tested query establishes that both apps match this URI; scan
        // candidates without host evidence alone cannot prove that.
        val queried = SystemProbeResult(0, 0, items = hostCandidates.map {
            SystemProbeItem(it.activityLabel, it.rule.packageName, it.rule.className)
        })
        fun preview(host: String?) = SystemPreviewProjection.estimate(
            deep, hostCandidates, emptySet(), DisplayMode.HIDE_SELECTED, emptyMap(), queried,
            browserLinks = config, requestHost = host,
        )!!.items.map { it.packageName }
        assertEquals(listOf("com.test.b", "com.test.a"), preview("example.com"))
        assertEquals(listOf("com.test.a", "com.test.b"), preview("unrelated.example"))
    }

    @Test fun regularPreviewPreservesVerifiedHookOrder() {
        val hooked = PreviewListResult(
            listOf(SystemProbeItem("B", "com.test.b", "b.Main"),
                SystemProbeItem("A", "com.test.a", "a.Main")),
            PreviewListSource.HOOK_OBSERVATION,
        )
        val projected = PreviewListResult(
            listOf(hooked.items[1], hooked.items[0]), PreviewListSource.ESTIMATED,
        )
        assertEquals(hooked, preferredPreviewResult(PreviewListMode.CURRENT, hooked, projected))
    }

    @Test fun regularPreviewAutomaticallyUsesEstimateWhenOnlyApiCandidatesAreAvailable() {
        val api = PreviewListResult(
            listOf(SystemProbeItem("A", "com.test.a", "a.Main")),
            PreviewListSource.API_RETURN,
        )
        val estimated = PreviewListResult(
            listOf(SystemProbeItem("B", "com.test.b", "b.Main")),
            PreviewListSource.ESTIMATED,
        )
        assertEquals(estimated, preferredPreviewResult(PreviewListMode.CURRENT, api, estimated))
        assertEquals(api, preferredPreviewResult(PreviewListMode.CANDIDATES, api, estimated))
        assertEquals(api, preferredPreviewResult(PreviewListMode.CURRENT, api, null))
    }

    @Test fun showAllKeepsSystemCandidateOrderEvenWithPriorities() {
        val original = SystemProbeResult(
            0, 0, items = listOf(
                SystemProbeItem("B", "com.test.b", "com.test.b.Main"),
                SystemProbeItem("A", "com.test.a", "com.test.a.Main"),
            ),
        )
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.SHOW_ALL, emptyMap(), original,
            priorities = PriorityConfig(apps = mapOf(kind to listOf("com.test.a"))),
        )!!
        assertEquals(listOf("com.test.b", "com.test.a"), result.items.map { it.packageName })
    }

    @Test fun exactApiQueryDoesNotInheritOtherScannedTargets() {
        val kind = IntentKind.SHARE
        val b = ComponentCandidate(
            ComponentRule(kind, "com.test.b", "com.test.b.Send"), "B", "Send",
            evidence = listOf("SEND mime=text/plain"),
        )
        val a = ComponentCandidate(
            ComponentRule(kind, "com.test.a", "com.test.a.Send"), "A", "Send",
            evidence = listOf("SEND mime=text/plain"),
        )
        val image = ComponentCandidate(
            ComponentRule(kind, "com.test.image", "com.test.image.Send"), "Image", "Image",
            evidence = listOf("SEND mime=image/png"),
        )
        val probe = SystemProbeResult(0, 0,
            items = listOf(SystemProbeItem("B", "com.test.b", "com.test.b.Send")))
        val result = SystemPreviewProjection.estimate(kind, listOf(a,b,image),
            emptySet(), DisplayMode.SHOW_ALL, emptyMap(), probe,
            scenario = SystemProbeScenario.TEXT)!!
        assertEquals(listOf("com.test.b"), result.items.map { it.packageName })
        assertEquals(PreviewListSource.ESTIMATED, result.source)
    }

    @Test fun successfulEmptyIntentQueryMustRemainEmptyDespiteBroadScanMatches() {
        val share = IntentKind.SHARE
        val scanned = ComponentCandidate(
            ComponentRule(share, "com.test.scanned", "com.test.scanned.Send"),
            "Scanned", "Send",
            evidence = listOf("SEND mime=text/plain"),
        )
        val emptySystemQuery = SystemProbeResult(0, 0, items = emptyList(),
            evidence = ProbeEvidence.REGISTERED)
        assertNull(SystemPreviewProjection.estimate(
            share, listOf(scanned), emptySet(), DisplayMode.SHOW_ALL,
            emptyMap(), emptySystemQuery, scenario = SystemProbeScenario.TEXT,
        ))
    }

    @Test fun missingMimeEvidenceIsNeverAssumedToMatchTheCurrentSharesheet() {
        val share = IntentKind.SHARE
        val unknown = ComponentCandidate(
            ComponentRule(share, "com.test.unknown", "com.test.unknown.Send"),
            "Unknown", "Unknown", evidence = listOf("UNVERIFIED registration"),
        )
        assertNull(SystemPreviewProjection.estimate(
            share, listOf(unknown), emptySet(), DisplayMode.SHOW_ALL,
            emptyMap(), null, scenario = SystemProbeScenario.TEXT,
        ))
    }

    @Test fun openPdfScanIsPreservedWhileOtherFileTypesStayOut() {
        val pdf = ComponentCandidate(
            ComponentRule(kind, "com.test.pdf", "com.test.pdf.Open"), "PDF", "PDF",
            evidence = listOf("VIEW scheme=content mime=application/pdf sample=example.pdf"),
        )
        val text = ComponentCandidate(
            ComponentRule(kind, "com.test.txt", "com.test.txt.Open"), "Text", "Text",
            evidence = listOf("VIEW scheme=content mime=text/plain sample=example.txt"),
        )
        val result = SystemPreviewProjection.estimate(
            kind, listOf(text,pdf), emptySet(), DisplayMode.SHOW_ALL, emptyMap(), null,
            scenario = SystemProbeScenario.PDF, requestedPreset = OpenPreset.PDF,
        )!!
        assertEquals(listOf("com.test.pdf"), result.items.map { it.packageName })
    }

    @Test fun scopedDraftImmediatelyChangesEstimatedVisibleListWithoutSavingRules() {
        val uncommitted = setOf(b)
        val result = SystemPreviewProjection.estimate(
            kind, candidates, emptySet(), DisplayMode.HIDE_SELECTED, emptyMap(), null,
            scopeSelected = uncommitted,
        )!!
        assertEquals(listOf("com.test.a"), result.items.map { it.packageName })
        val source = PreviewListResult(emptyList(), PreviewListSource.ORDERED_ADAPTER)
        assertEquals(result, preferredPreviewResult(PreviewListMode.CURRENT, source, result,
            hasPendingDraft = true))
        assertEquals(source, preferredPreviewResult(PreviewListMode.CURRENT, source, result))
    }

    @Test fun scopedDeepLinkHostDoesNotLeakCandidatesFromOtherHosts() {
        val kind = IntentKind.DEEP_LINK
        val first = ComponentCandidate(
            ComponentRule(kind, "com.test.first", "com.test.first.Link"), "First", "First",
            browserHosts = setOf("a.example"),
        )
        val other = ComponentCandidate(
            ComponentRule(kind, "com.test.other", "com.test.other.Link"), "Other", "Other",
            browserHosts = setOf("b.example"),
        )
        val result = SystemPreviewProjection.estimate(
            kind, listOf(first, other), emptySet(), DisplayMode.SHOW_ALL,
            emptyMap(), null, requestHost = "a.example",
        )!!
        assertEquals(listOf("com.test.first"), result.items.map { it.packageName })
    }

    @Test fun currentDirectShareUsesMatchingActiveScanInsteadOfUnrelatedHistory() {
        val kind = IntentKind.DIRECT_SHARE
        val image = ComponentCandidate(
            ComponentRule(kind, "com.test.image", "com.test.image.Direct"), "Image", "Image",
            evidence = listOf("DIRECT_SHARE_MATCHED_MIME=IMAGE"),
        )
        val text = ComponentCandidate(
            ComponentRule(kind, "com.test.text", "com.test.text.Direct"), "Text", "Text",
            evidence = listOf("DIRECT_SHARE_MATCHED_MIME=TEXT"),
        )
        val result = SystemPreviewProjection.estimate(
            kind, listOf(image,text), emptySet(), DisplayMode.SHOW_ALL, emptyMap(), null,
            directShareType = DirectShareType.IMAGE,
        )!!
        assertEquals(listOf("com.test.image"), result.items.map { it.packageName })
        val history = PreviewListResult(
            listOf(SystemProbeItem("Old", "com.test.old", "@old")),
            PreviewListSource.HISTORY,
        )
        assertEquals(result, preferredPreviewResult(
            PreviewListMode.CURRENT, history, result, kind = kind,
            directShareType = DirectShareType.IMAGE,
        ))
    }

    @Test fun schemeAndDocumentSubtypesRetainTheirExactTestParameters() {
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            scopedProbeMime(OpenPreset.WORD))
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            scopedProbeUri(OpenPreset.MAGNET))
        assertEquals(SystemProbeScenario.PDF,
            scopedProbeScenario(IntentKind.OPEN, OpenPreset.PDF))
        assertEquals(SystemProbeScenario.IMAGE,
            scopedProbeScenario(IntentKind.DIRECT_SHARE, directShareType = DirectShareType.IMAGE))
    }

}
