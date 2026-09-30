package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeAckCodecTest {
    private val digest = "a".repeat(64)

    @Test fun encodesCurrentAckAndRoundTrips() {
        val expected = RuntimeAckCodec.Ack(
            digest = digest,
            queryHits = 12,
            visibilityHits = 34,
            orderingHits = 56,
            componentDiscoveryProtocol = 2,
            runtimeProtocol = 2,
            revision = 99,
        )
        val encoded = RuntimeAckCodec.encode(59, expected)

        assertEquals("59:$digest:12:34:56:2:2:99", encoded)
        assertEquals(expected, RuntimeAckCodec.parse(encoded, 59, digest))
    }

    @Test fun encodeNormalizesNonNegativeMetricsAndProtocols() {
        val encoded = RuntimeAckCodec.encode(
            59,
            RuntimeAckCodec.Ack(digest, -1, -2, -3, -1, 0, -1),
        )
        assertEquals("59:$digest:0:0:0:0:1:-1", encoded)
    }

    @Test fun parsesCurrentAck() {
        val ack = RuntimeAckCodec.parse(
            "59:$digest:12:34:56:2:2:99",
            hookCompatVersion = 59,
            expectedDigest = digest,
        ) ?: error("ack missing")

        assertEquals(digest, ack.digest)
        assertEquals(12L, ack.queryHits)
        assertEquals(34L, ack.visibilityHits)
        assertEquals(56L, ack.orderingHits)
        assertEquals(2, ack.componentDiscoveryProtocol)
        assertEquals(2, ack.runtimeProtocol)
        assertEquals(99L, ack.revision)
    }

    @Test fun olderShortAckKeepsCompatibilityDefaults() {
        val ack = RuntimeAckCodec.parse("59:$digest", 59, digest) ?: error("ack missing")

        assertEquals(0L, ack.queryHits)
        assertEquals(0L, ack.visibilityHits)
        assertEquals(0L, ack.orderingHits)
        assertEquals(0, ack.componentDiscoveryProtocol)
        assertEquals(1, ack.runtimeProtocol)
        assertEquals(-1L, ack.revision)
    }

    @Test fun malformedOptionalMetricsFailOpenToDefaults() {
        val ack = RuntimeAckCodec.parse(
            "59:$digest:-2:not-a-number:7:-1:0:not-a-number",
            59,
            digest,
        ) ?: error("ack missing")

        assertEquals(0L, ack.queryHits)
        assertEquals(0L, ack.visibilityHits)
        assertEquals(7L, ack.orderingHits)
        assertEquals(0, ack.componentDiscoveryProtocol)
        assertEquals(1, ack.runtimeProtocol)
        assertEquals(-1L, ack.revision)
    }

    @Test fun rejectsWrongGenerationOrDigest() {
        assertNull(RuntimeAckCodec.parse("58:$digest:1", 59, digest))
        assertNull(RuntimeAckCodec.parse("59:${"b".repeat(64)}:1", 59, digest))
        assertNull(RuntimeAckCodec.parse("59", 59, digest))
        assertNull(RuntimeAckCodec.parse(null, 59, digest))
    }
}
