package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun normalizeKeepsNewestDuplicateInsideSameBatch() {
        val newer = ObservedEntryRecord(
            kind = IntentKind.VPN.name,
            packageName = "com.example.vpn",
            syntheticClass = "@vpn",
            label = "New",
            observedAt = 20L,
        )
        val older = newer.copy(label = "Old", observedAt = 10L)

        val decoded = ObservedEntryCacheCodec.decode(
            ObservedEntryCacheCodec.encode(listOf(older, newer))
        )

        assertEquals(1, decoded.size)
        assertEquals("New", decoded.single().label)
        assertEquals(20L, decoded.single().observedAt)
    }

    @Test
    fun reconcileRemoteReplacesAuthorityRowsButKeepsShortcutHistory() {
        val staleVpn = ObservedEntryRecord(
            IntentKind.VPN.name,
            "com.example.oldvpn",
            "@vpn",
            observedAt = 10L,
        )
        val currentVpn = ObservedEntryRecord(
            IntentKind.VPN.name,
            "com.example.currentvpn",
            "@vpn",
            observedAt = 20L,
        )
        val shortcut = ObservedEntryRecord(
            IntentKind.SHORTCUT_ITEM.name,
            "com.example.chat",
            "com.example.chat.Main#shortcut#def",
            observedAt = 15L,
        )

        val reconciled = ObservedEntryCacheCodec.reconcileRemote(
            listOf(staleVpn, shortcut),
            listOf(currentVpn),
        )

        assertFalse(reconciled.any { it.packageName == staleVpn.packageName })
        assertTrue(reconciled.any { it.packageName == currentVpn.packageName })
        assertTrue(reconciled.any { it.key == shortcut.key })
    }

    @Test
    fun reconcileEmptyRemoteClearsAuthorityRows() {
        val assistant = ObservedEntryRecord(
            IntentKind.ASSISTANT.name,
            "com.example.assistant",
            "@assistant",
            observedAt = 10L,
        )

        val reconciled = ObservedEntryCacheCodec.reconcileRemote(listOf(assistant), emptyList())

        assertTrue(reconciled.isEmpty())
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
    fun roleAuthorityKindsRoundTrip() {
        val assistant = ObservedEntryRecord(
            IntentKind.ASSISTANT.name,
            "com.example.assistant",
            "@assistant",
            observedAt = 2L,
        )

        assertEquals(listOf(assistant), ObservedEntryCacheCodec.decode(ObservedEntryCacheCodec.encode(listOf(assistant))))
    }

    @Test
    fun decodeFailsClosedForOversizedOrInvalidContent() {
        assertTrue(ObservedEntryCacheCodec.decode("not-json").isEmpty())
        assertTrue(
            ObservedEntryCacheCodec.decode("x".repeat(ObservedEntryCacheCodec.MAX_ENCODED_CHARS + 1)).isEmpty()
        )
    }
}
