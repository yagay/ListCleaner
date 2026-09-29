package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedEntryCacheCodecTest {
    @Test
    fun mergeKeepsNewestRecordAndBothSupportedKinds() {
        val older = ObservedEntryRecord(
            kind = IntentKind.DIRECT_SHARE.name,
            packageName = "com.example.chat",
            syntheticClass = "com.example.chat.Share#direct#abc",
            label = "Old",
            observedAt = 10L,
        )
        val newer = older.copy(label = "New", observedAt = 20L)
        val shortcut = ObservedEntryRecord(
            kind = IntentKind.SHORTCUT_ITEM.name,
            packageName = "com.example.chat",
            syntheticClass = "com.example.chat.Main#shortcut#def",
            label = "Scan",
            observedAt = 15L,
        )

        val merged = ObservedEntryCacheCodec.merge(listOf(older), listOf(newer, shortcut))

        assertEquals(2, merged.size)
        assertEquals("New", merged.first { it.key == newer.key }.label)
        assertTrue(merged.any { it.kind == IntentKind.SHORTCUT_ITEM.name })
    }

    @Test
    fun encodeRoundTripRejectsUnsupportedKinds() {
        val valid = ObservedEntryRecord(
            kind = IntentKind.DIRECT_SHARE.name,
            packageName = "com.example.chat",
            syntheticClass = "@entry#direct#abc",
            label = "Alice",
            observedAt = 1L,
        )
        val retired = valid.copy(kind = IntentKind.LAUNCHER_SHORTCUT.name, syntheticClass = "legacy")

        val encoded = ObservedEntryCacheCodec.encode(listOf(valid, retired))
        val decoded = ObservedEntryCacheCodec.decode(encoded)

        assertEquals(listOf(valid), decoded)
    }

    @Test
    fun decodeFailsClosedForOversizedOrInvalidContent() {
        assertTrue(ObservedEntryCacheCodec.decode("not-json").isEmpty())
        assertTrue(
            ObservedEntryCacheCodec.decode("x".repeat(ObservedEntryCacheCodec.MAX_ENCODED_CHARS + 1)).isEmpty()
        )
    }
}
