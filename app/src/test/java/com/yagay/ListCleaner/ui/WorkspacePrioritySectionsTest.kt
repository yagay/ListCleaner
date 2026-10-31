package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same section boundaries drive Entry, Root Tiles and Settings Apps. */
class WorkspacePrioritySectionsTest {
    private data class Row(val id: String, val prioritized: Boolean)

    private fun boundaries(rows: List<Row>): List<Pair<Int, Boolean>> =
        rows.indices.filter { startsPrioritySection(rows, it) { row -> row.prioritized } }
            .map { it to rows[it].prioritized }

    @Test fun emptyCatalogProducesNoHeaders() {
        assertTrue(boundaries(emptyList()).isEmpty())
        assertFalse(startsPrioritySection(emptyList<Row>(), 0) { it.prioritized })
    }

    @Test fun mixedListProducesOneHeaderPerPrioritySection() {
        val rows = listOf(Row("vpn", true), Row("wifi", true),
            Row("camera", false), Row("battery", false))
        assertEquals(listOf(0 to true, 2 to false), boundaries(rows))
    }

    @Test fun filteredListHasNoEmptyPrioritySection() {
        assertEquals(listOf(0 to false),
            boundaries(listOf(Row("camera", false), Row("battery", false))))
        assertEquals(listOf(0 to true),
            boundaries(listOf(Row("vpn", true), Row("wifi", true))))
    }

    @Test fun togglingPriorityRebuildsCorrectBoundaries() {
        val initial = listOf(Row("vpn", true), Row("wifi", false))
        assertEquals(listOf(0 to true, 1 to false), boundaries(initial))
        val allPriority = initial.map { it.copy(prioritized = true) }
        assertEquals(listOf(0 to true), boundaries(allPriority))
    }
}
