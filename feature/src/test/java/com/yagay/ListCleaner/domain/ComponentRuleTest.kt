package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentRuleTest {
    @Test
    fun idRoundTripPreservesRule() {
        val rule = ComponentRule(
            IntentKind.SHARE,
            "com.example.target",
            "com.example.target.ShareActivity"
        )

        assertEquals(rule, ComponentRule.fromId(rule.id))
    }

    @Test
    fun invalidComponentDelimiterIsRejected() {
        val rule = ComponentRule(IntentKind.OPEN, "com.example|bad", ".OpenActivity")

        assertTrue(!rule.isValid())
        assertNull(ComponentRule.fromId(rule.id))
    }

    @Test
    fun unknownIntentKindIsRejected() {
        assertNull(ComponentRule.fromId("UNKNOWN|com.example|.Activity"))
    }

    @Test
    fun malformedIdsAreRejected() {
        assertNull(ComponentRule.fromId("SHARE|com.example"))
        assertNull(ComponentRule.fromId("SHARE||.Activity"))
        assertNull(ComponentRule.fromId("SHARE|com.example|"))
    }
}
