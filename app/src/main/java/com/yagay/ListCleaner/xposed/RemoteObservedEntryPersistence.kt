package com.yagay.ListCleaner.xposed

import android.content.SharedPreferences
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.domain.ObservedEntryRecord

/** Persists runtime-observed entries through libxposed RemotePreferences. */
internal class RemoteObservedEntryPersistence(
    private val preferences: SharedPreferences,
    private val record: (String) -> Unit,
) {
    /**
     * Delta-style observations are isolated by kind so independent Xposed processes never race on
     * one aggregate JSON value. The read/merge/write cycle is now scoped to one logical surface.
     */
    fun merge(entries: Collection<ObservedEntryRecord>): Boolean {
        if (entries.isEmpty()) return true
        val grouped = entries.mapNotNull { candidate ->
            val validated = candidate.validatedOrNull() ?: return@mapNotNull null
            val kind = runCatching { IntentKind.valueOf(validated.kind) }.getOrNull()
                ?: return@mapNotNull null
            kind to validated
        }.groupBy({ it.first }, { it.second })
        return grouped.all { (kind, values) ->
            update(ObservedEntryCache.remoteKey(kind)) { previous ->
                ObservedEntryCacheCodec.merge(previous, values)
            }
        }
    }

    /**
     * Replace one authority surface without reading or rewriting other kinds. Empty snapshots are
     * meaningful and therefore still write an encoded empty list for this kind.
     */
    fun replaceKind(kind: IntentKind, entries: Collection<ObservedEntryRecord>): Boolean = runCatching {
        val encoded = ObservedEntryCacheCodec.encode(
            entries.mapNotNull(ObservedEntryRecord::validatedOrNull)
                .filter { it.kind == kind.name }
        )
        check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        preferences.edit()
            .putString(ObservedEntryCache.remoteKey(kind), encoded)
            .commit()
    }.onFailure {
        record("OBSERVED_CACHE_WRITE_FAILED kind=${kind.name} error=${it.javaClass.name}")
    }.getOrDefault(false)

    @Synchronized
    private fun update(
        key: String,
        transform: (List<ObservedEntryRecord>) -> List<ObservedEntryRecord>,
    ): Boolean = runCatching {
        val previousEncoded = preferences.getString(key, null)
        val previous = ObservedEntryCacheCodec.decode(previousEncoded)
        val encoded = ObservedEntryCacheCodec.encode(transform(previous))
        if (encoded == previousEncoded) return@runCatching true
        check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        preferences.edit().putString(key, encoded).commit()
    }.onFailure {
        record("OBSERVED_CACHE_WRITE_FAILED key=$key error=${it.javaClass.name}")
    }.getOrDefault(false)
}
