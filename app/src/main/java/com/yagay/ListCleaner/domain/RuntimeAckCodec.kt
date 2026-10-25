package com.yagay.ListCleaner.domain

/** Pure codec for the resolver-label runtime ACK wire format. */
object RuntimeAckCodec {
    data class Ack(
        val digest: String,
        val queryHits: Long,
        val visibilityHits: Long,
        val orderingHits: Long,
        val componentDiscoveryProtocol: Int,
        val runtimeProtocol: Int,
        val revision: Long,
    )

    fun encode(
        hookCompatVersion: Long,
        ack: Ack,
    ): String = buildString {
        append(hookCompatVersion)
        append(':').append(ack.digest)
        append(':').append(ack.queryHits.coerceAtLeast(0L))
        append(':').append(ack.visibilityHits.coerceAtLeast(0L))
        append(':').append(ack.orderingHits.coerceAtLeast(0L))
        append(':').append(ack.componentDiscoveryProtocol.coerceAtLeast(0))
        append(':').append(ack.runtimeProtocol.coerceAtLeast(1))
        append(':').append(ack.revision)
    }

    /**
     * Parses both the current ACK and older short ACKs. Missing or malformed optional counters keep
     * the historical defaults so manager upgrades remain compatible with an older loaded hook.
     */
    fun parse(label: String?, hookCompatVersion: Long, expectedDigest: String): Ack? {
        if (label.isNullOrBlank()) return null
        val parts = label.split(':')
        if (parts.size < 2) return null
        if (parts[0].toLongOrNull() != hookCompatVersion) return null
        if (parts[1] != expectedDigest) return null

        return Ack(
            digest = expectedDigest,
            queryHits = parts.getOrNull(2)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            visibilityHits = parts.getOrNull(3)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            orderingHits = parts.getOrNull(4)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            componentDiscoveryProtocol = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            runtimeProtocol = parts.getOrNull(6)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            revision = parts.getOrNull(7)?.toLongOrNull() ?: -1L,
        )
    }
}
