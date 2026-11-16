package com.yagay.ListCleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileShellBatchTest {
    @Test fun successfulBatchEntriesRemainSeparate() {
        val output = "\n__LC_QUERY_START_0__\ncom.example.a/.Activity\n__LC_QUERY_END_0__:0" +
            "\n__LC_QUERY_START_1__\ncom.example.b/.Activity\n__LC_QUERY_END_1__:0\n"
        assertEquals("com.example.a/.Activity", ProfileShellBatch.entry(output, 0, "LC_QUERY")?.trim())
        assertEquals("com.example.b/.Activity", ProfileShellBatch.entry(output, 1, "LC_QUERY")?.trim())
    }

    @Test fun aFailedCommandNeverBecomesVerifiedEmptyDespiteFinalSuccessfulPrintf() {
        val output = "\n__LC_QUERY_START_0__\n\n__LC_QUERY_END_0__:1" +
            "\n__LC_QUERY_START_1__\n\n__LC_QUERY_END_1__:0\n"
        assertNull(ProfileShellBatch.entry(output, 0, "LC_QUERY"))
        assertEquals("", ProfileShellBatch.entry(output, 1, "LC_QUERY")?.trim())
    }

    @Test fun missingMarkersOrPartialOutputCannotBeCached() {
        assertNull(ProfileShellBatch.entry("__LC_QUERY_START_0__\n", 0, "LC_QUERY"))
        assertNull(ProfileShellBatch.entry("no markers", 0, "LC_QUERY"))
        assertNull(ProfileShellBatch.entry("\n__LC_QUERY_START_0__\nError: locked\n__LC_QUERY_END_0__:0",
            0, "LC_QUERY"))
    }

    @Test fun shellCommandPreservesEachExitStatus() {
        val command = ProfileShellBatch.script(listOf("false", "true"), "LC_QUERY")
        assertTrue(command.contains("lc_status=\$?"))
        assertTrue(command.contains("LC_QUERY_END_0"))
        assertTrue(command.contains("LC_QUERY_END_1"))
        assertFalse(ProfileShellBatch.clean("Error: user locked"))
        assertTrue(ProfileShellBatch.clean("com.example.app/.MainActivity"))
    }
}
