package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProbeRequestTest {
    @Test fun componentScopedSelectionRequiresExactPackageAndClass() {
        val request = SystemProbeRequest(
            entryKind = IntentKind.SHARE,
            userId = 10,
            focusPackage = "com.example.alpha",
            focusClass = "com.example.alpha.ShareTarget",
        )
        assertTrue(request.matches(SystemProbeItem(
            "One", "com.example.alpha", "com.example.alpha.ShareTarget"
        )))
        assertFalse(request.matches(SystemProbeItem(
            "Wrong class", "com.example.alpha", "com.example.alpha.Other"
        )))
        assertFalse(request.matches(SystemProbeItem(
            "Wrong app", "com.example.beta", "com.example.alpha.ShareTarget"
        )))
    }

    @Test fun packageScopedRoleMatchesAnyQualifiedComponentInThatPackage() {
        val request = SystemProbeRequest(
            entryKind = IntentKind.BROWSER,
            userId = 0,
            focusPackage = "com.example.browser",
            focusClass = "com.yagay.synthetic.Browser",
        )
        assertTrue(request.matches(SystemProbeItem(
            "Web", "com.example.browser", "com.example.browser.WebActivity"
        )))
        assertFalse(request.matches(SystemProbeItem(
            "Other", "com.example.other", "com.example.other.WebActivity"
        )))
    }

    @Test fun rootComponentFocusRemainsComponentSpecific() {
        val request = SystemProbeRequest(
            componentKind = CleanupKind.TILE,
            userId = 0,
            focusPackage = "com.example.alpha",
            focusClass = "com.example.alpha.TileService",
        )
        assertTrue(request.matches(SystemProbeItem(
            "Tile", "com.example.alpha", "com.example.alpha.TileService"
        )))
        assertFalse(request.matches(SystemProbeItem(
            "Other", "com.example.alpha", "com.example.alpha.OtherTile"
        )))
    }
}
