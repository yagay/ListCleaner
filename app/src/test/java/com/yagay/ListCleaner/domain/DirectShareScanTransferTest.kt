package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectShareScanTransferTest {
    @Test fun chunksRoundTripWithoutDroppingUnicode() {
        val data = "目标😀".repeat(6000)
        val chunks = DirectShareScanTransfer.split(data)
        assertTrue(chunks.size > 1)
        assertEquals(data, DirectShareScanTransfer.join(chunks, chunks.size))
    }

    @Test fun rejectsIncompleteOversizedAndEmptyTransfers() {
        val chunks = DirectShareScanTransfer.split("x".repeat(9000))
        assertNull(DirectShareScanTransfer.join(chunks.dropLast(1), chunks.size))
        assertNull(DirectShareScanTransfer.join(listOf(""), 1))
        assertNull(DirectShareScanTransfer.join(listOf("x".repeat(8001)), 1))
        assertNull(DirectShareScanTransfer.join(emptyList(), 0))
    }
}
