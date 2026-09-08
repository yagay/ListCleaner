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

    @Test fun realFileEvidenceComesFirstAndDuplicatesAreRemoved() {
        val candidate = ComponentCandidate(rule, "App", "Open", evidence = listOf("ORDINARY", "REAL_FILE mime=application/pdf"))
        val merged = IntentCatalog.merge(listOf(candidate, candidate)).single()
        assertEquals(2, merged.evidence.size)
        assertTrue(merged.evidence.first().startsWith("REAL_FILE "))
    }
}
