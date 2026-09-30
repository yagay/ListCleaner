package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverOrderingEngineTest {
    private val engine = ResolverOrderingEngine {}

    @Test fun typedOpenPriorityOverridesGenericOpenPriority() {
        val current = snapshot(
            priorities = PriorityConfig(apps = mapOf(IntentKind.OPEN to listOf("generic"))),
            openTypes = OpenTypeConfig(
                priorities = mapOf(OpenPreset.PDF to listOf("typed", "generic"))
            ),
        )
        assertEquals(
            listOf("typed", "generic"),
            engine.effectivePriorities(IntentKind.OPEN, OpenPreset.PDF, null, current),
        )
    }

    @Test fun hostDeepLinkPriorityOverridesGenericDeepLinkPriority() {
        val current = snapshot(
            priorities = PriorityConfig(apps = mapOf(IntentKind.DEEP_LINK to listOf("generic"))),
            browserLinks = BrowserLinkConfig(
                hosts = setOf("example.com"),
                priorities = mapOf("example.com" to listOf("host", "generic")),
            ),
        )
        assertEquals(
            listOf("host", "generic"),
            engine.effectivePriorities(IntentKind.DEEP_LINK, null, "example.com", current),
        )
    }

    @Test fun browserCapabilityIncludesBrowserAndHostPriorities() {
        val browser = snapshot(
            priorities = PriorityConfig(apps = mapOf(IntentKind.BROWSER to listOf("browser"))),
        )
        assertTrue(engine.hasEffectivePriorities(IntentKind.BROWSER, null, null, browser))
        val deepLink = snapshot(
            browserLinks = BrowserLinkConfig(
                hosts = setOf("example.com"),
                priorities = mapOf("example.com" to listOf("host")),
            ),
        )
        assertTrue(engine.hasEffectivePriorities(IntentKind.BROWSER, null, "example.com", deepLink))
        assertFalse(engine.hasEffectivePriorities(IntentKind.BROWSER, null, "other.example", deepLink))
    }

    private fun snapshot(
        priorities: PriorityConfig = PriorityConfig(),
        openTypes: OpenTypeConfig = OpenTypeConfig(),
        browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    ) = RuntimeRuleSnapshot(
        configured = emptySet(),
        displayMode = DisplayMode.HIDE_SELECTED,
        priorities = priorities,
        openTypes = openTypes,
        browserLinks = browserLinks,
        diagnostic = false,
    )
}
