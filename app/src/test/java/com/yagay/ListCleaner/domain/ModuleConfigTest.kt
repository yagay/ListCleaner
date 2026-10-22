package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModuleConfigTest {
    @Test fun distinctAndroidUsersSurviveSerializationWithoutChangingPrimaryRules() {
        val personal = ComponentRule(IntentKind.OPEN, "com.example.personal", "com.example.personal.Main")
        val work = ComponentRule(IntentKind.OPEN, "com.example.work", "com.example.work.Main")
        val input = ModuleConfig(
            rules = setOf(personal),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(),
            diagnostic = false,
            userSpaces = mapOf(10 to UserSpaceRules(
                rules = setOf(work),
                mode = DisplayMode.SHOW_SELECTED,
                priorities = PriorityConfig(mapOf(IntentKind.OPEN to listOf("com.example.work"))),
            )),
        ).validated()
        val restored = Json.decodeFromString(ModuleConfig.serializer(),
            Json.encodeToString(ModuleConfig.serializer(), input)).validated()
        assertEquals(setOf(personal), restored.rules)
        assertEquals(setOf(work), restored.userSpaces.getValue(10).rules)
        assertEquals(DisplayMode.HIDE_SELECTED, restored.mode)
        assertEquals(DisplayMode.SHOW_SELECTED, restored.userSpaces.getValue(10).mode)
        val workEdited = restored.copy(
            userSpaces = restored.userSpaces + (10 to restored.userSpaces.getValue(10).copy(
                rules = emptySet(),
            )),
        )
        assertEquals(restored.rules, workEdited.rules)
        assertEquals(restored.priorities, workEdited.priorities)
    }

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

    @Test fun legacyDomainRulesCopyDeepLinkTitleAndNormalizeBrowserRoleTitle() {
        val browserRule = ComponentRule(IntentKind.BROWSER, "com.example", "com.example.Target")
        val browserPackageRule = SyntheticEntryKeys.packageScopedRule(IntentKind.BROWSER, "com.example")
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

        assertEquals(setOf(browserPackageRule), config.rules)
        assertTrue(deepLinkRule in config.browserLinks.selectedRules("example.com"))
        assertFalse(browserRule.id in config.priorities.titles)
        assertEquals("Target", config.priorities.titles[browserPackageRule.id])
        assertEquals("Target", config.priorities.titles[deepLinkRule.id])
    }

    @Test fun packageScopedAuthorityComponentsCollapseAndTitlesMigrate() {
        val kinds = listOf(
            IntentKind.ASSISTANT,
            IntentKind.HOME,
            IntentKind.BROWSER,
            IntentKind.CALL_SCREENING,
            IntentKind.VPN,
            IntentKind.AUTOFILL,
            IntentKind.CREDENTIAL_PROVIDER,
        )
        kinds.forEach { kind ->
            val packageName = "com.example.${kind.name.lowercase()}"
            val first = ComponentRule(kind, packageName, "$packageName.First")
            val second = ComponentRule(kind, packageName, "$packageName.Second")
            val packageRule = SyntheticEntryKeys.packageScopedRule(kind, packageName)
            val config = ModuleConfig(
                rules = setOf(first, second),
                mode = DisplayMode.HIDE_SELECTED,
                priorities = PriorityConfig(titles = mapOf(first.id to "Authority label")),
                diagnostic = false,
            ).validated()

            assertEquals("$kind", setOf(packageRule), config.rules)
            assertEquals("$kind", "Authority label", config.priorities.titles[packageRule.id])
            assertFalse(first.id in config.priorities.titles)
            assertFalse(second.id in config.priorities.titles)
        }
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
        assertFalse(FilterPolicy.restoreEmpty("OPEN", 4, 0))
    }

    @Test fun identicalAppInOtherProfileIsNotSameCaller() {
        assertFalse(FilterPolicy.sameCaller(10001, 1010001))
        assertTrue(FilterPolicy.sameCaller(10001, 10001))
    }
    @Test fun independentUserSpacesRemainIndependentAfterSerialization() {
        val primary = ComponentRule(IntentKind.OPEN, "com.primary.example", "com.primary.example.Open")
        val profile = ComponentRule(IntentKind.OPEN, "com.profile.example", "com.profile.example.Open")
        val config = ModuleConfig(
            rules = setOf(primary),
            mode = DisplayMode.HIDE_SELECTED,
            priorities = PriorityConfig(),
            diagnostic = false,
            userSpaces = mapOf(
                10 to UserSpaceRules(
                    rules = setOf(profile),
                    mode = DisplayMode.SHOW_SELECTED,
                    priorities = PriorityConfig(),
                ),
                11 to UserSpaceRules(),
            )
        )
        val decoded = Json.decodeFromString(
            ModuleConfig.serializer(), Json.encodeToString(ModuleConfig.serializer(), config)
        ).validated()
        assertEquals(setOf(primary), decoded.rules)
        assertEquals(setOf(profile), decoded.userSpaces.getValue(10).rules)
        assertEquals(DisplayMode.SHOW_SELECTED, decoded.userSpaces.getValue(10).mode)
        assertTrue(decoded.userSpaces.getValue(11).rules.isEmpty())
        assertEquals(DisplayMode.SHOW_ALL, decoded.userSpaces.getValue(11).mode)
    }

    @Test fun legacyUserZeroConfigurationLoadsWithNoOtherUsers() {
        val legacy = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        val restored = Json { ignoreUnknownKeys = true }.decodeFromString(
            ModuleConfig.serializer(), legacy
        ).validated()
        assertTrue(restored.userSpaces.isEmpty())
    }

}

