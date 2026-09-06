package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class OpenTypeConfigTest {
    @Test fun typedConfigRequiresOpenComponents() {
        val open = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Reader")
        OpenTypeConfig(rules = mapOf(OpenPreset.PDF to setOf(open.id)), priorities = mapOf(OpenPreset.PDF to listOf("com.example"))).validated()
        assertThrows(IllegalArgumentException::class.java) {
            OpenTypeConfig(rules = mapOf(OpenPreset.PDF to setOf(ComponentRule(IntentKind.SHARE, "com.example", "com.example.Share").id))).validated()
        }
    }
}
