package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModuleConfigTest {
    @Test fun hiddenFromAppsRoundTrip() {
        val config = ModuleConfig(
            emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715,
            hiddenFromApps = setOf("com.estrongs.android.pop")
        )
        val encoded = Json.encodeToString(ModuleConfig.serializer(), config)
        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(), encoded).validated())
    }

    @Test fun atomicConfigurationRoundTrip() {
        val config = ModuleConfig(
            setOf(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open")),
            DisplayMode.SHOW_SELECTED,
            PriorityConfig(mapOf(IntentKind.OPEN to listOf("com.example"))),
            true,
            10715
        )
        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(),
            Json.encodeToString(ModuleConfig.serializer(), config)).validated())
    }

    @Test fun removedLegacyRuntimeFieldsAreIgnored() {
        val encoded = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false,"tiles":{"enabled":true,"hidden":[]},"defaultOpen":{"preferred":{}}}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(
            ModuleConfig.serializer(), encoded
        ).validated()
        assertEquals(DisplayMode.HIDE_SELECTED, decoded.mode)
        assertTrue(decoded.rules.isEmpty())
    }

    @Test fun retiredLauncherShortcutStateIsDiscarded() {
        val legacy = ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            "com.example",
            "com.example.MainActivity",
        )
        val config = ModuleConfig(
            rules = setOf(legacy),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(
                apps = mapOf(IntentKind.LAUNCHER_SHORTCUT to listOf("com.example")),
                titles = mapOf(legacy.id to "Legacy"),
            ),
            diagnostic = false,
        ).validated()

        assertTrue(config.rules.isEmpty())
        assertFalse(IntentKind.LAUNCHER_SHORTCUT in config.priorities.apps)
        assertFalse(legacy.id in config.priorities.titles)
    }

    @Test fun legacyDomainRulesAndTitlesMigrateWithoutDeletingBrowserTitle() {
        val browserRule = ComponentRule(IntentKind.BROWSER, "com.example", "com.example.Target")
        val deepLinkRule = browserRule.copy(kind = IntentKind.DEEP_LINK)
        val config = ModuleConfig(
            rules = setOf(browserRule),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(titles = mapOf(browserRule.id to "Target")),
            diagnostic = false,
            browserLinks = BrowserLinkConfig(
                hosts = setOf("example.com"),
                rules = mapOf("example.com" to setOf(browserRule.id))
            )
        ).validated()

        assertTrue(deepLinkRule in config.browserLinks.selectedRules("example.com"))
        assertEquals("Target", config.priorities.titles[browserRule.id])
        assertEquals("Target", config.priorities.titles[deepLinkRule.id])
    }

    @Test fun assistantComponentRulesCollapseToPackageRuleAndTitleMigrates() {
        val packageName = "com.example.assistant"
        val activity = ComponentRule(IntentKind.ASSISTANT, packageName, "$packageName.AssistActivity")
        val voiceService = ComponentRule(IntentKind.ASSISTANT, packageName, "$packageName.VoiceService")
        val packageRule = SyntheticEntryKeys.assistantPackageRule(packageName)
        val config = ModuleConfig(
            rules = setOf(activity, voiceService),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(
                titles = mapOf(activity.id to "Assistant label")
            ),
            diagnostic = false,
        ).validated()

        assertEquals(setOf(packageRule), config.rules)
        assertEquals("Assistant label", config.priorities.titles[packageRule.id])
        assertFalse(activity.id in config.priorities.titles)
        assertFalse(voiceService.id in config.priorities.titles)
    }

    @Test fun runtimeComponentPolicyRoundTrip() {
        val config = ModuleConfig(
            emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715,
            rootDisabledComponents = setOf("0|com.example|com.example.Tile")
        )
        val encoded = Json.encodeToString(ModuleConfig.serializer(), config)
        val decoded = Json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
        assertEquals(config.rootDisabledComponents, decoded.rootDisabledComponents)
    }

    @Test fun legacyRuntimeConfigKeepsRootPolicyUnspecified() {
        val encoded = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(
            ModuleConfig.serializer(), encoded
        ).validated()
        assertNull(decoded.rootDisabledComponents)
    }

    @Test fun malformedRuntimeComponentPolicyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleConfig(
                emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false,
                rootDisabledComponents = setOf("bad-key")
            ).validated()
        }
    }

    @Test fun relativeNamesMatchExpandedRuleIds() {
        assertEquals(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open").id,
            ComponentRule(IntentKind.OPEN, "com.example", ".Open").id)
    }

    @Test fun controlCharactersAreRejected() {
        assertFalse(ComponentRule(IntentKind.OPEN, "com.example", "bad\nname").isValid())
    }

    @Test fun emptyTextMenuIsAllowedButFileDestinationIsProtected() {
        assertFalse(FilterPolicy.restoreEmpty("PROCESS_TEXT", 4, 0))
        assertTrue(FilterPolicy.restoreEmpty("OPEN", 4, 0))
    }

    @Test fun identicalAppInOtherProfileIsNotSameCaller() {
        assertFalse(FilterPolicy.sameCaller(10001, 1010001))
        assertTrue(FilterPolicy.sameCaller(10001, 10001))
    }
}
