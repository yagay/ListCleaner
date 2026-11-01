package com.yagay.ListCleaner.domain

import com.yagay.ListCleaner.data.IntentCatalog
import org.junit.Assert.*
import org.junit.Test

class CatalogEvidenceTest {
    private val rule = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open")

    @Test fun unrestrictedEvidenceWinsOverRestrictedProbe() {
        val limited = ComponentCandidate(rule, "App", "Open", evidence = listOf("BROAD"), restricted = true)
        val ordinary = limited.copy(evidence = listOf("ORDINARY"), restricted = false)
        val merged = IntentCatalog.merge(listOf(limited, ordinary)).single()
        assertFalse(merged.restricted)
        assertEquals(listOf("BROAD", "ORDINARY"), merged.evidence)
    }

    @Test fun resolverProbesFromManyMimeTypesMergeIntoOneReceiverRowPerAction() {
        val single = ComponentRule(IntentKind.SHARE, "com.example", "com.example.Share")
        val multi = single.copy(kind = IntentKind.SHARE_MULTIPLE)
        val all = (1..60).map {
            ComponentCandidate(single, "App", "Share", evidence = listOf("SEND mime=application/x-kind-$it"))
        } + ComponentCandidate(multi, "App", "Share",
            evidence = listOf("SEND_MULTIPLE mime=application/pdf"))
        val merged = IntentCatalog.merge(all)
        assertEquals(2, merged.size)
        val shared = merged.first { it.rule.kind == IntentKind.SHARE }
        assertEquals(60, shared.evidence.size)
        assertTrue(shared.evidence.contains("SEND mime=application/x-kind-60"))
        assertEquals(1, merged.first { it.rule.kind == IntentKind.SHARE_MULTIPLE }.evidence.size)
    }

    @Test fun realFileEvidenceComesFirstAndDuplicatesAreRemoved() {
        val candidate = ComponentCandidate(rule, "App", "Open", evidence = listOf("ORDINARY", "REAL_FILE mime=application/pdf"))
        val merged = IntentCatalog.merge(listOf(candidate, candidate)).single()
        assertEquals(2, merged.evidence.size)
        assertTrue(merged.evidence.first().startsWith("REAL_FILE "))
    }
}
