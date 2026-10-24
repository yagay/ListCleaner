package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the app-group sort order when the UI reuses a cached candidate index. */
class CandidateGroupingSortTest {
    private fun entry(pkg: String, name: String, suffix: String): ComponentCandidate =
        ComponentCandidate(
            rule = ComponentRule(IntentKind.SHARE, pkg, "$pkg.$suffix"),
            appLabel = name,
            activityLabel = suffix,
        )

    @Test fun selectedFirstRanksFullThenPartialThenUnselected() {
        val full = entry("com.example.zulu", "Zulu", "Single")
        val partialA = entry("com.example.beta", "Beta", "First")
        val partialB = entry("com.example.beta", "Beta", "Second")
        val none = entry("com.example.alpha", "Alpha", "Single")
        val candidates = listOf(none, partialB, full, partialA)
        val selected = setOf(full.rule, partialA.rule)

        val groups = filterAppGroups(
            baseAppGroups(candidates), selected, IntentKind.SHARE, "",
            UiFilter.ALL, UiAppSort.SELECTED_FIRST,
        )
        assertEquals(
            listOf("com.example.zulu", "com.example.beta", "com.example.alpha"),
            groups.map { it.packageName },
        )
        assertEquals(listOf(1, 2, 1), groups.map { it.components.size })

        val unselected = filterAppGroups(
            baseAppGroups(candidates), selected, IntentKind.SHARE, "",
            UiFilter.HIDE_SELECTED, UiAppSort.SELECTED_FIRST,
        )
        assertEquals(
            listOf("com.example.alpha", "com.example.beta"),
            unselected.map { it.packageName },
        )
        assertEquals(listOf(partialB.rule), unselected.last().components.map { it.rule })
    }

    @Test fun changingSearchOrSortDoesNotMutateTheCachedBaseGroups() {
        val alpha = entry("com.example.alpha", "Alpha", "Share")
        val beta = entry("com.example.beta", "Beta", "Share")
        val base = baseAppGroups(listOf(beta, alpha))
        val matching = filterAppGroups(
            base, setOf(beta.rule), IntentKind.SHARE, "Alpha",
            UiFilter.ALL, UiAppSort.SELECTED_FIRST,
        )
        assertEquals(listOf("com.example.alpha"), matching.map { it.packageName })

        val alphabetical = filterAppGroups(
            base, setOf(beta.rule), IntentKind.SHARE, "",
            UiFilter.ALL, UiAppSort.NAME,
        )
        assertEquals(listOf("com.example.alpha", "com.example.beta"), alphabetical.map { it.packageName })
        assertEquals(2, base.size)
        assertEquals(2, base.sumOf { it.components.size })
    }
    @Test fun filteringNormalizesSearchNeedleOnceWithoutChangingResults() {
        val github = entry("com.example.github", "GitHub", "DeepLinkActivity")
        val gmail = entry("com.example.gmail", "Gmail", "MailActivity")
        val base = baseAppGroups(listOf(github, gmail))

        val result = filterAppGroups(
            base, emptySet(), IntentKind.SHARE, "  gItHuB  ",
            UiFilter.ALL, UiAppSort.NAME,
        )
        assertEquals(listOf("com.example.github"), result.map { it.packageName })
        assertEquals(
            listOf("com.example.github", "com.example.gmail"),
            filterAppGroups(base, emptySet(), IntentKind.SHARE, " ",
                UiFilter.ALL, UiAppSort.NAME).map { it.packageName },
        )
    }

}
