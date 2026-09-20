package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WebTargetKindTest {
    @Test fun genericWebHandlersStayBrowsers() {
        assertEquals(
            IntentKind.BROWSER,
            classifyWebTarget(
                handlesAllWebUrls = true,
                authorityCount = 0,
                filterKnown = true,
                hasWebScheme = true
            )
        )
        assertEquals(
            IntentKind.BROWSER,
            classifyWebTarget(
                handlesAllWebUrls = false,
                authorityCount = 0,
                filterKnown = true,
                hasWebScheme = true
            )
        )
    }

    @Test fun hostSpecificHandlersBecomeDeepLinks() {
        assertEquals(
            IntentKind.DEEP_LINK,
            classifyWebTarget(
                handlesAllWebUrls = false,
                authorityCount = 1,
                filterKnown = true,
                hasWebScheme = true
            )
        )
        assertEquals(
            IntentKind.DEEP_LINK,
            classifyWebTarget(
                handlesAllWebUrls = false,
                authorityCount = -1,
                filterKnown = false,
                hasWebScheme = false
            )
        )
    }
}
