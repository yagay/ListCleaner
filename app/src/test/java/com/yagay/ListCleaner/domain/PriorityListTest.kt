package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class PriorityListTest {
    private fun item(pkg: String, label: String = pkg, cls: String = "Activity", kind: IntentKind = IntentKind.SHARE) =
        ComponentCandidate(ComponentRule(kind, pkg, cls), label, cls)

    private fun groups(items: List<ComponentCandidate>, saved: List<String>, filter: PriorityListFilter = PriorityListFilter.ALL,
                       query: String = "", selected: Set<ComponentRule> = emptySet()) =
        priorityAppGroups(items, selected, DisplayMode.HIDE_SELECTED, IntentKind.SHARE, saved, query, filter)

    @Test fun checkingMovesIntoPriorityOrderAndUncheckingRestoresDefaultPosition() {
        val items = listOf(item("b"), item("a"), item("c"))
        assertEquals(listOf("a", "b", "c"), groups(items, emptyList()).map { it.packageName })
        assertEquals(listOf("c", "a", "b"), groups(items, listOf("c")).map { it.packageName })
        assertEquals(listOf("c", "b", "a"), groups(items, listOf("c", "b")).map { it.packageName })
        assertEquals(listOf("b", "a", "c"), groups(items, listOf("b")).map { it.packageName })
        assertEquals(listOf(1, 2, null), groups(items, listOf("c", "a")).map { it.rank })
    }

    @Test fun systemFilterPreservesSelectedOrderAndGlobalRanks() {
        val system = item("system", label = "System").copy(appType = AppType.SYSTEM)
        val userA = item("user.a", label = "Alpha").copy(appType = AppType.USER)
        val userB = item("user.b", label = "Beta").copy(appType = AppType.USER)
        val saved = listOf("system", "user.b", "user.a")
        val items = listOf(system, userA, userB)

        val visible = priorityAppGroups(items, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, saved, "", PriorityListFilter.ALL,
            appTypeFilter = AppTypeFilter.USER)
        assertEquals(listOf("user.b", "user.a"), visible.map { it.packageName })
        assertEquals(listOf(2, 3), visible.map { it.rank })

        val moved = moveVisiblePriority(saved, visible.map { it.packageName }, "user.a", -1)
        assertEquals(listOf("system", "user.a", "user.b"), moved)
        assertEquals(listOf("system", "user.b", "user.a"), saved)
        val userOnlyAfterMove = priorityAppGroups(items, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, moved, "", PriorityListFilter.ALL,
            appTypeFilter = AppTypeFilter.USER)
        assertEquals(listOf("user.a", "user.b"), userOnlyAfterMove.map { it.packageName })
        assertEquals(listOf(2, 3), userOnlyAfterMove.map { it.rank })
        val restored = priorityAppGroups(items, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, moved, "", PriorityListFilter.ALL,
            appTypeFilter = AppTypeFilter.ALL)
        assertEquals(listOf("system", "user.a", "user.b"), restored.map { it.packageName })
        assertEquals(listOf(1, 2, 3), restored.map { it.rank })
        val systemOnly = priorityAppGroups(items, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, moved, "", PriorityListFilter.ALL,
            appTypeFilter = AppTypeFilter.SYSTEM)
        assertEquals(listOf("system"), systemOnly.map { it.packageName })
        assertEquals(listOf(1), systemOnly.map { it.rank })
    }

    @Test fun filteringNonAdjacentSelectedSystemAppsKeepsOriginalRankGaps() {
        val firstUser = item("user.first").copy(appType = AppType.USER)
        val firstSystem = item("sys.first").copy(appType = AppType.SYSTEM)
        val secondUser = item("user.second").copy(appType = AppType.USER)
        val secondSystem = item("sys.second").copy(appType = AppType.SYSTEM)
        val saved = listOf("sys.first", "user.second", "sys.second", "user.first")
        val rows = listOf(firstUser, firstSystem, secondUser, secondSystem)
        val users = priorityAppGroups(rows, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, saved, "", PriorityListFilter.SELECTED,
            appTypeFilter = AppTypeFilter.USER)
        assertEquals(listOf("user.second", "user.first"), users.map { it.packageName })
        assertEquals(listOf(2, 4), users.map { it.rank })
        val systems = priorityAppGroups(rows, emptySet(), DisplayMode.HIDE_SELECTED,
            IntentKind.SHARE, saved, "", PriorityListFilter.SELECTED,
            appTypeFilter = AppTypeFilter.SYSTEM)
        assertEquals(listOf("sys.first", "sys.second"), systems.map { it.packageName })
        assertEquals(listOf(1, 3), systems.map { it.rank })
        assertEquals(listOf("sys.first", "user.second", "sys.second", "user.first"), saved)
    }

    @Test fun systemOnlyShowsNoUserEntriesAndHidesUnrankedSystemWhenUserOnly() {
        val system = item("system").copy(appType = AppType.SYSTEM)
        val user = item("user").copy(appType = AppType.USER)
        val rows = listOf(system, user)
        assertEquals(listOf("system"), priorityAppGroups(rows, emptySet(),
            DisplayMode.HIDE_SELECTED, IntentKind.SHARE, listOf("user"), "",
            PriorityListFilter.ALL, appTypeFilter = AppTypeFilter.SYSTEM)
            .map { it.packageName })
        assertEquals(listOf("user"), priorityAppGroups(rows, emptySet(),
            DisplayMode.HIDE_SELECTED, IntentKind.SHARE, listOf("user"), "",
            PriorityListFilter.ALL, appTypeFilter = AppTypeFilter.USER)
            .map { it.packageName })
    }

    @Test fun selectedFollowsSavedOrderAndUnselectedRemainsAlphabetic() {
        val items = listOf(item("b"), item("a"), item("c"), item("d"))
        val saved = listOf("c", "a")
        assertEquals(listOf("c", "a"), groups(items, saved, PriorityListFilter.SELECTED).map { it.packageName })
        assertEquals(listOf("b", "d"), groups(items, saved, PriorityListFilter.UNSELECTED).map { it.packageName })
    }

    @Test fun filteringKeepsOnlyMatchingComponentsAndRespectsRuleCleanup() {
        val hidden = item("a", cls = "Hidden")
        val match = item("a", cls = "Match")
        val other = item("a", cls = "Other")
        val results = groups(listOf(hidden, match, other, item("b", kind = IntentKind.OPEN)),
            listOf("a"), query = "match", selected = setOf(hidden.rule))
        assertEquals(listOf(match), results.single().components)
        assertTrue(groups(listOf(hidden), listOf("a"), selected = setOf(hidden.rule)).isEmpty())
    }

    @Test fun hiddenSavedEntriesStayInPlaceWhenMovingSearchResults() {
        val saved = listOf("a", "hidden", "b", "c")
        val matches = groups(listOf(item("a", "Match A"), item("b", "Other"), item("c", "Match C")),
            saved, PriorityListFilter.SELECTED, query = "match")
        assertEquals(listOf(1, 3), matches.map { it.rank })
        assertEquals(listOf("c", "hidden", "b", "a"),
            moveVisiblePriority(saved, matches.map { it.packageName }, "c", -1))
        assertEquals(listOf("a", "hidden", "b", "c"), saved)
    }

    @Test fun missingRestrictedAndOtherCategoriesDoNotBecomePriorityOptions() {
        val items = listOf(item("a").copy(unavailable = true), item("b").copy(restricted = true),
            item("c", kind = IntentKind.OPEN))
        assertTrue(groups(items, listOf("a", "b", "c")).isEmpty())
    }
    @Test fun visibleAppRowsCountEachPackageOnceAfterAllPriorityFilters() {
        val first = item("a", cls = "A")
        val second = item("a", cls = "B")
        val another = item("b", cls = "C")
        val hidden = item("c", cls = "D")
        val candidates = listOf(first, second, another, hidden)
        val selected = setOf(hidden.rule)

        val displayed = groups(candidates, listOf("a", "c"), selected = selected)
        assertEquals(2, displayed.size)
        assertEquals(setOf("a", "b"), displayed.map { it.packageName }.toSet())
        assertEquals(1, groups(candidates, listOf("a", "c"), query = "A", selected = selected).size)
        assertEquals(1, groups(candidates, listOf("a", "c"), PriorityListFilter.SELECTED, selected = selected).size)
        assertEquals(1, groups(candidates, listOf("a", "c"), PriorityListFilter.UNSELECTED, selected = selected).size)
    }
}

