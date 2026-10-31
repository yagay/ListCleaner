package com.yagay.ListCleaner.domain

/** Bounded chunks carried by the authenticated Runtime Probe. */
object DirectShareScanTransfer {
    const val CHUNK_CHARS = 8000
    const val MAX_CHUNKS = 96

    fun split(encoded: String): List<String> {
        require(encoded.isNotEmpty() && encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        val chunks = encoded.chunked(CHUNK_CHARS)
        require(chunks.size in 1..MAX_CHUNKS)
        return chunks
    }

    fun join(chunks: List<String>, expected: Int): String? {
        if (expected !in 1..MAX_CHUNKS || chunks.size != expected ||
            chunks.any { it.isEmpty() || it.length > CHUNK_CHARS }) return null
        if (chunks.sumOf { it.length } > ObservedEntryCacheCodec.MAX_ENCODED_CHARS) return null
        return chunks.joinToString("")
    }
}
