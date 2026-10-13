package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriorityDragTargetTest {
    private val visible = listOf("one.app", "two.app", "three.app")
    private val prefix = "app|OPEN|ALL|"

    @Test fun newlyVisibleRowsRemainDropTargets() {
        assertEquals("three.app", priorityDragTargetPackage(
            "app|OPEN|ALL|three.app", prefix, visible))
    }

    @Test fun otherScopesAndUnavailableRowsAreIgnored() {
        assertNull(priorityDragTargetPackage("app|OPEN|OTHER|two.app", prefix, visible))
        assertNull(priorityDragTargetPackage("app|SEND|ALL|two.app", prefix, visible))
        assertNull(priorityDragTargetPackage("app|OPEN|ALL|missing.app", prefix, visible))
        assertNull(priorityDragTargetPackage("section|prioritized", prefix, visible))
        assertNull(priorityDragTargetPackage(123, prefix, visible))
    }
}
