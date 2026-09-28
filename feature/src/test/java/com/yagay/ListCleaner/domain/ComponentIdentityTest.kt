package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentIdentityTest {
    @Test fun aliasUsesTargetActivityAsStableRuleIdentity() {
        assertEquals(
            "com.UCMobile.main.UCMobile",
            ComponentIdentity.canonicalClassName(
                "com.UCMobile",
                "com.UCMobile.main.UCMobile.DefaultBrowserEntry",
                "com.UCMobile.main.UCMobile"
            )
        )
    }

    @Test fun normalActivityKeepsItsOwnName() {
        assertEquals(
            "com.example.ShareActivity",
            ComponentIdentity.canonicalClassName(
                "com.example", "com.example.ShareActivity", null
            )
        )
    }

    @Test fun relativeAndShortNamesAreExpandedDefensively() {
        assertEquals(
            "com.example.AliasTarget",
            ComponentIdentity.canonicalClassName("com.example", "com.example.Alias", ".AliasTarget")
        )
        assertEquals(
            "com.example.OpenActivity",
            ComponentIdentity.canonicalClassName("com.example", "OpenActivity", null)
        )
    }
}
