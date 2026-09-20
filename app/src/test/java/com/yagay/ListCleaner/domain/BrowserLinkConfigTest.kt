package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLinkConfigTest {
    @Test fun normalizesPastedUrls() {
        assertEquals("github.com", normalizeBrowserHost("HTTPS://GitHub.com/yagay/ListCleaner"))
        assertEquals("sub.example.com", normalizeBrowserHost("sub.example.com:443/path"))
        assertNull(normalizeBrowserHost("bad host"))
    }

    @Test fun oldBrowserDomainRulesMigrateToDeepLinks() {
        val oldBrowser = ComponentRule(IntentKind.BROWSER, "com.example", "com.example.Target")
        val deepLink = oldBrowser.copy(kind = IntentKind.DEEP_LINK)
        val config = BrowserLinkConfig(
            hosts = setOf("github.com"),
            rules = mapOf("github.com" to setOf(oldBrowser.id)),
            priorities = mapOf("github.com" to listOf("com.example"))
        ).validated()
        assertTrue(deepLink in config.selectedRules("GITHUB.COM"))
        assertEquals(listOf("com.example"), config.priority("https://github.com/test"))
    }
}
