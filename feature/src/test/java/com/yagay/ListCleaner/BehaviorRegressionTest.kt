package com.yagay.ListCleaner

import com.yagay.ListCleaner.data.readBackupText
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.RuleBackup
import com.yagay.ListCleaner.ui.groupCandidates
import com.yagay.ListCleaner.ui.UiFilter
import com.yagay.ListCleaner.ui.retainConfiguredCandidates
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class BehaviorRegressionTest {
    @Test fun everyCategoryIgnoresMetadataAndManagerPermissionAsVisibilityVetoes() {
        IntentKind.entries.forEach { kind ->
            val resolved = candidate(kind).copy(
                evidence = listOf("activityEnabled=false", "appEnabled=false", "managerGranted=false"),
                restricted = com.yagay.ListCleaner.domain.FilterPolicy.catalogRestricted(true, 10001, 10002))
            assertTrue(resolved.isCatalogCandidate)
            assertEquals(1, groupCandidates(listOf(resolved), emptySet(), kind, "", UiFilter.ALL).size)
            assertEquals(1, groupCandidates(listOf(resolved), setOf(resolved.rule), kind, "", UiFilter.ALL).size)
        }
    }

    @Test fun menuLabelSearchFindsComponentUnderItsActualOwnerApp() {
        val entry = candidate(IntentKind.PROCESS_TEXT, "com.google.android.googlequicksearchbox", "Google")
            .copy(activityLabel = "Ask Gemini")
        assertTrue(entry.matchesQuery("ask gemini"))
        assertTrue(entry.matchesQuery("PROCESS_TEXTActivity"))
        assertEquals(1, groupCandidates(listOf(entry), emptySet(), IntentKind.PROCESS_TEXT, "Gemini", UiFilter.ALL).size)
        assertTrue(listOf(entry).any { it.isCatalogCandidate && it.matchesQuery("Gemini") })
        assertFalse(entry.matchesQuery("absent label"))
    }

    @Test fun allDiscoveryQueriesExcludeDisabledAndUninstalledMatches() {
        IntentKind.entries.forEach { kind ->
            for (discovery in listOf(true, false)) {
                val flags = com.yagay.ListCleaner.data.IntentCatalog.queryFlags(kind, discovery)
                val excluded = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                    android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
                    android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
                assertEquals(0, flags and excluded)
            }
        }
    }

    @Test fun browserDiscoveryDoesNotUseMenuFlags() {
        val flags = com.yagay.ListCleaner.data.IntentCatalog.queryFlags(IntentKind.BROWSER, true)
        assertTrue(flags and android.content.pm.PackageManager.MATCH_ALL != 0)
        assertTrue(flags and android.content.pm.PackageManager.MATCH_DEFAULT_ONLY != 0)
        assertEquals(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            com.yagay.ListCleaner.data.IntentCatalog.queryFlags(IntentKind.OPEN, false))
        assertEquals(0, com.yagay.ListCleaner.data.IntentCatalog.queryFlags(IntentKind.PROCESS_TEXT, false))
    }

    @Test fun unmatchedRulesOnlyAppearInSelectedView() {
        val old = candidate(IntentKind.BROWSER).copy(unavailable = true)
        val selected = setOf(old.rule)
        assertTrue(groupCandidates(listOf(old), selected, null, "", UiFilter.ALL).isEmpty())
        assertEquals(listOf(old), groupCandidates(listOf(old), selected, null, "", UiFilter.SHOW_SELECTED).single().components)
        assertTrue(groupCandidates(listOf(old), selected, null, "", UiFilter.HIDE_SELECTED).isEmpty())
        assertTrue(groupCandidates(listOf(old), emptySet(), null, "", UiFilter.SHOW_SELECTED).isEmpty())
    }

    @Test fun broadMatchesRemainNormalButRestrictedRulesNeedSelectedView() {
        val broad = candidate(IntentKind.OPEN).copy(broadMatch = true)
        assertEquals(1, groupCandidates(listOf(broad), emptySet(), null, "", UiFilter.ALL).size)
        val restricted = broad.copy(restricted = true)
        assertTrue(groupCandidates(listOf(restricted), emptySet(), null, "", UiFilter.ALL).isEmpty())
        assertTrue(groupCandidates(listOf(restricted), setOf(restricted.rule), null, "", UiFilter.ALL).isEmpty())
        assertEquals(1, groupCandidates(listOf(restricted), setOf(restricted.rule), null, "", UiFilter.SHOW_SELECTED).size)
    }

    @Test fun cancellingMissingRuleDoesNotLeaveHistoricalCandidates() {
        val old = candidate(IntentKind.OPEN).copy(unavailable = true)
        assertEquals(listOf(old), retainConfiguredCandidates(listOf(old), setOf(old.rule)))
        assertTrue(retainConfiguredCandidates(listOf(old), emptySet()).isEmpty())
    }

    @Test fun unavailableSelectedRulesStillRespectCategoryAndSearch() {
        val old = candidate(IntentKind.OPEN).copy(unavailable = true)
        assertTrue(groupCandidates(listOf(old), setOf(old.rule), IntentKind.BROWSER, "", UiFilter.SHOW_SELECTED).isEmpty())
        assertTrue(groupCandidates(listOf(old), setOf(old.rule), null, "no match", UiFilter.SHOW_SELECTED).isEmpty())
        assertEquals(1, groupCandidates(listOf(old), setOf(old.rule), IntentKind.OPEN, "example", UiFilter.SHOW_SELECTED).size)
    }

    @Test fun relativeStoredRuleUsesCanonicalIdentity() {
        assertEquals(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open"),
            ComponentRule.fromId("OPEN|com.example|.Open"))
    }

    @Test fun selectingInAllViewDoesNotRemoveAnApp() {
        val item = candidate(IntentKind.BROWSER)
        assertEquals(groupCandidates(listOf(item), emptySet(), IntentKind.BROWSER, "", UiFilter.ALL),
            groupCandidates(listOf(item), setOf(item.rule), IntentKind.BROWSER, "", UiFilter.ALL))
    }

    @Test fun freshScanDoesNotBringBackUnselectedOldItems() {
        val old = candidate(IntentKind.BROWSER)
        val fresh = candidate(IntentKind.OPEN)
        val selected = setOf(old.rule)
        val replacement = retainConfiguredCandidates(listOf(fresh), selected)
        assertEquals(2, replacement.size)
        assertTrue(replacement.single { it.rule == old.rule }.unavailable)
        assertEquals(listOf(fresh), retainConfiguredCandidates(listOf(fresh), emptySet()))
    }

    @Test fun matchingSelectedRuleDoesNotCreateDuplicatePlaceholder() {
        val fresh = candidate(IntentKind.OPEN)
        assertEquals(listOf(fresh), retainConfiguredCandidates(listOf(fresh), setOf(fresh.rule)))
    }

    private fun candidate(kind: IntentKind, pkg: String = "com.example", label: String = "Example") =
        ComponentCandidate(ComponentRule(kind, pkg, "$pkg.${kind.name}Activity"), label, "Target")

    @Test fun categorySelectionDoesNotIncludeOtherKindsFromTheSameApp() {
        val share = candidate(IntentKind.SHARE)
        val browser = candidate(IntentKind.BROWSER)
        val groups = groupCandidates(listOf(browser, share), emptySet(), IntentKind.SHARE, "", UiFilter.ALL)
        assertEquals(listOf(share), groups.single().components)
    }

    @Test fun appAndCategoryOrderStayStable() {
        val share = candidate(IntentKind.SHARE)
        val browser = candidate(IntentKind.BROWSER)
        val other = candidate(IntentKind.OPEN, "com.other", "Alpha")
        val groups = groupCandidates(listOf(browser, other, share), emptySet(), null, "", UiFilter.ALL)
        assertEquals(listOf("com.other", "com.example"), groups.map { it.packageName })
        assertEquals(listOf(share, browser), groups.last().components)
    }

    @Test fun classNameSearchIsCaseInsensitiveAndStillRespectsCategory() {
        val share = candidate(IntentKind.SHARE)
        assertEquals(listOf(share), groupCandidates(listOf(share), emptySet(), null, "shareactivity", UiFilter.ALL).single().components)
        assertTrue(groupCandidates(listOf(share), emptySet(), IntentKind.OPEN, "shareactivity", UiFilter.ALL).isEmpty())
    }

    @Test fun missingSelectedRuleRemainsManageable() {
        val item = candidate(IntentKind.OPEN)
        val retained = retainConfiguredCandidates(emptyList(), setOf(item.rule))
        assertEquals(item.rule, retained.single().rule)
        assertTrue(retained.single().unavailable)
        assertEquals(1, retainConfiguredCandidates(listOf(item), setOf(item.rule)).size)
    }

    @Test fun backupReaderHandlesShortReadsWithoutLosingUnicodeOrNewlines() {
        val expected = "中文\n备份😀"
        val reader = object : Reader() {
            var position = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (position == expected.length) return -1
                buffer[offset] = expected[position++]
                return 1
            }
            override fun close() {}
        }
        assertEquals(expected, reader.use { it.readBackupText(expected.length) })
    }

    @Test fun backupReaderAllowsExactLimitAndEmptyInput() {
        assertEquals("abcd", StringReader("abcd").use { it.readBackupText(4) })
        assertEquals("", StringReader("").use { it.readBackupText(4) })
    }

    @Test fun oversizedBackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StringReader("abcde").use { it.readBackupText(4) }
        }
    }

    @Test fun appTypeFilterSeparatesUserAndSystemAppsWithoutOrderingSemantics() {
        assertTrue(AppTypeFilter.ALL.matches(AppType.USER))
        assertTrue(AppTypeFilter.ALL.matches(AppType.SYSTEM))
        assertTrue(AppTypeFilter.USER.matches(AppType.USER))
        assertFalse(AppTypeFilter.USER.matches(AppType.SYSTEM))
        assertTrue(AppTypeFilter.SYSTEM.matches(AppType.SYSTEM))
        assertFalse(AppTypeFilter.SYSTEM.matches(AppType.USER))
    }

    @Test fun componentStatePolicyBuildsReusableSafeCommandLine() {
        val line = ComponentStatePolicy.commandLine(
            "com.example",
            "com.example.Outer\$Tile",
            0,
            false
        )
        assertEquals(
            "/system/bin/pm disable --user 0 'com.example/com.example.Outer\$Tile'",
            line
        )
        assertTrue(ComponentStatePolicy.command(
            "com.example",
            "com.example.Tile",
            0,
            true
        ).endsWith("exec /system/bin/pm enable --user 0 'com.example/com.example.Tile'"))
    }

    @Test fun existingBackupWithoutExplicitVersionRetainsWhitelist() {
        val original = """{"blacklist":false,"rules":[{"kind":"SHARE","packageName":"com.example","className":"com.example.Share"}]}"""
        val backup = Json.decodeFromString(RuleBackup.serializer(), original)
        assertEquals(1, backup.version)
        assertFalse(backup.blacklist)
        assertEquals(setOf(ComponentRule(IntentKind.SHARE, "com.example", "com.example.Share")), backup.rules)
        assertEquals(backup, Json.decodeFromString(RuleBackup.serializer(), Json.encodeToString(RuleBackup.serializer(), backup)))
    }
}
