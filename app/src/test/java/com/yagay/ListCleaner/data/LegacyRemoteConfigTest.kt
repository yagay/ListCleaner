package com.yagay.ListCleaner.data

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyRemoteConfigTest {
    @Test
    fun retiredLauncherShortcutRowsAreDroppedDuringLegacyRecovery() {
        val share = ComponentRule(
            IntentKind.SHARE,
            "com.example.share",
            "com.example.share.ShareActivity",
        )
        val retired = ComponentRule(
            IntentKind.LAUNCHER_SHORTCUT,
            "com.example.app",
            "com.example.app.MainActivity",
        )

        assertEquals(setOf(share), decodeLegacyRuleIds(setOf(share.id, retired.id)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedActiveRowsStillRejectLegacyRecovery() {
        decodeLegacyRuleIds(setOf("SHARE|bad package|bad class"))
    }
}
