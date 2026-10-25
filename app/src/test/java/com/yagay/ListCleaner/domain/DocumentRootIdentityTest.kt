package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRootIdentityTest {
    @Test fun stableAndDistinctRootIds() {
        val a = SyntheticEntryKeys.documentRootClass("com.example.docs", "home")
        assertEquals(a, SyntheticEntryKeys.documentRootClass("com.example.docs", "home"))
        assertNotEquals(a, SyntheticEntryKeys.documentRootClass("com.example.docs", "downloads"))
        assertNotEquals(a, SyntheticEntryKeys.documentRootClass("com.other.docs", "home"))
        assertTrue(a.startsWith("@docroot_"))
        assertTrue(a.length <= 64)
    }

    @Test fun ruleIsRoundTrippable() {
        val item = ComponentRule(
            IntentKind.DOCUMENT_ROOT, "com.example.docs",
            SyntheticEntryKeys.documentRootClass("com.example.docs.provider", "home")
        )
        assertEquals(item, ComponentRule.fromId(item.id))
    }
}
