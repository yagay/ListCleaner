package com.yagay.ListCleaner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootUserIsolationTest {
    @Test fun sameComponentInTwoSpacesGeneratesDifferentUserScopedCommands() {
        val primary = ComponentStatePolicy.commandLine(
            "com.example.test", "com.example.test.TileService", 0, false
        )
        val work = ComponentStatePolicy.commandLine(
            "com.example.test", "com.example.test.TileService", 10, false
        )
        assertNotEquals(primary, work)
        assertTrue(primary.contains("--user 0 "))
        assertTrue(work.contains("--user 10 "))
        assertTrue(primary.endsWith("'com.example.test/com.example.test.TileService'"))
        assertTrue(work.endsWith("'com.example.test/com.example.test.TileService'"))
        assertFalse(work.contains("--user 0 "))
    }
}
