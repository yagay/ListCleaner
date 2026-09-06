package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PriorityConfigTest {
    private data class Target(val pkg: String, val component: String = "main", val user: Int = 0)
    private fun sort(input: List<Target>, vararg packages: String) =
        prioritizeApps(input, packages.toList(), { it.pkg }, { it.user })

    @Test fun promotesConfiguredAppsWhileKeepingOtherAppsAndSiblingComponentsStable() {
        val input = listOf(Target("other", "a"), Target("second"), Target("first", "x"), Target("other", "b"), Target("first", "y"))
        assertEquals(listOf(input[2], input[4], input[1], input[0], input[3]), sort(input, "first", "second"))
        assertEquals("other", input.first().pkg)
    }

    @Test fun missingOrFilteredAppIsNeverInjected() {
        val input = listOf(Target("a"), Target("b"))
        assertEquals(input, sort(input, "hidden", "uninstalled"))
    }

    @Test fun clearingPrioritiesPreservesTheOriginalOrder() {
        val input = listOf(Target("b"), Target("a"))
        assertSame(input, sort(input))
    }

    @Test fun userProfileSlotsDoNotMove() {
        val input = listOf(Target("b", user = 0), Target("b", user = 10), Target("a", user = 0), Target("a", user = 10))
        val output = sort(input, "a")
        assertEquals(listOf(input[2], input[3], input[0], input[1]), output)
        assertEquals(input.map { it.user }, output.map { it.user })
    }

    @Test fun categoriesAndPackageOrderSurviveBackupRoundTrip() {
        val priority = PriorityConfig(mapOf(IntentKind.SHARE to listOf("com.b", "com.a"), IntentKind.OPEN to listOf("com.a", "com.b")))
        val backup = RuleBackup(version = 2, blacklist = false, rules = emptySet(), priorities = priority)
        val text = Json.encodeToString(RuleBackup.serializer(), backup)
        assertEquals(backup, Json.decodeFromString(RuleBackup.serializer(), text))
    }

    @Test fun componentTitlesRoundTripAndRequireCanonicalRuleIds() {
        val rule = ComponentRule(IntentKind.OPEN, "com.example", "com.example.OpenActivity")
        val value = PriorityConfig(titles = mapOf(rule.id to "PDF 阅读器")).validated()
        val text = Json.encodeToString(PriorityConfig.serializer(), value)
        assertEquals(value, Json.decodeFromString(PriorityConfig.serializer(), text).validated())
        assertThrows(IllegalArgumentException::class.java) {
            PriorityConfig(titles = mapOf("OPEN|com.example|.OpenActivity" to "bad")).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PriorityConfig(titles = mapOf(rule.id to "x".repeat(65))).validated()
        }
    }

    @Test fun oldPriorityJsonDefaultsTitlesToEmpty() {
        val decoded = Json.decodeFromString(PriorityConfig.serializer(), """{"apps":{"SHARE":["com.a"]}}""")
        assertTrue(decoded.titles.isEmpty())
    }

    @Test fun versionOneBackupDefaultsToSystemOrder() {
        val backup = Json.decodeFromString(RuleBackup.serializer(), """{"version":1,"blacklist":true,"rules":[]}""")
        assertTrue(backup.priorities.apps.isEmpty())
    }

    @Test fun invalidPriorityListsAreRejected() {
        listOf(listOf("com.a", "com.a"), listOf(""), listOf("bad|pkg"), (0..200).map { "com.app$it" }).forEach {
            assertThrows(IllegalArgumentException::class.java) { PriorityConfig(mapOf(IntentKind.SHARE to it)).validated() }
        }
    }
}
