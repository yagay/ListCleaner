package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectSharePolicyTest {
    @Test
    fun removesTargetsWhoseOwnerAppIsNotVisibleInShareResolver() {
        assertEquals(
            listOf(0, 2),
            directShareVisibleIndices(
                listOf("com.chat", "com.hidden", "com.chat"),
                setOf("com.chat")
            )
        )
    }

    @Test
    fun emptyVisibleAppSetRemovesAllKnownDirectShareTargets() {
        assertEquals(
            emptyList<Int>(),
            directShareVisibleIndices(
                listOf("com.chat", "com.mail"),
                emptySet()
            )
        )
    }

    @Test
    fun unknownTargetPackageFailsOpen() {
        assertEquals(
            listOf(0),
            directShareVisibleIndices(
                listOf(null, "com.hidden"),
                emptySet()
            )
        )
    }

    @Test
    fun individualDirectShareRulesApplyAfterAppVisibility() {
        assertEquals(
            listOf(1),
            directShareFilteredIndices(
                targetPackages = listOf("com.chat", "com.chat", "com.hidden"),
                ruleIds = listOf("r1", "r2", "r3"),
                visibleSharePackages = setOf("com.chat"),
                selectedRuleIds = setOf("r1"),
                displayMode = DisplayMode.HIDE_SELECTED,
                priorities = emptyList(),
            )
        )
    }

    @Test
    fun showSelectedKeepsUnknownRuleIdsFailOpen() {
        assertEquals(
            listOf(0, 1),
            directShareFilteredIndices(
                targetPackages = listOf("com.chat", "com.chat"),
                ruleIds = listOf(null, "selected"),
                visibleSharePackages = setOf("com.chat"),
                selectedRuleIds = setOf("selected"),
                displayMode = DisplayMode.SHOW_SELECTED,
                priorities = emptyList(),
            )
        )
    }

    @Test
    fun prioritiesReorderTargetsWithoutChangingMembership() {
        assertEquals(
            listOf(1, 0, 2),
            directShareFilteredIndices(
                targetPackages = listOf("com.mail", "com.chat", "com.other"),
                ruleIds = listOf(null, null, null),
                visibleSharePackages = setOf("com.mail", "com.chat", "com.other"),
                selectedRuleIds = emptySet(),
                displayMode = DisplayMode.HIDE_SELECTED,
                priorities = listOf("com.chat", "com.mail"),
            )
        )
    }
}
