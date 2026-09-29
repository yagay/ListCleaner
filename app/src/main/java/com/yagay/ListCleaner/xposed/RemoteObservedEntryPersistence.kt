package com.yagay.ListCleaner.xposed

import android.content.SharedPreferences
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.domain.ObservedEntryRecord

/** Persists chooser-observed entries through libxposed RemotePreferences. */
internal class RemoteObservedEntryPersistence(
    private val preferences: SharedPreferences,
    private val record: (String) -> Unit,
) {
    @Synchronized
    fun merge(entries: Collection<ObservedEntryRecord>): Boolean {
        if (entries.isEmpty()) return true
        return runCatching {
            val previousEncoded = preferences.getString(ObservedEntryCache.REMOTE_KEY, null)
            val previous = ObservedEntryCacheCodec.decode(previousEncoded)
            val merged = ObservedEntryCacheCodec.merge(previous, entries)
            val encoded = ObservedEntryCacheCodec.encode(merged)
            if (encoded == previousEncoded) return@runCatching true
            check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
            preferences.edit().putString(ObservedEntryCache.REMOTE_KEY, encoded).commit()
        }.onFailure {
            record("OBSERVED_CACHE_WRITE_FAILED error=${it.javaClass.name}")
        }.getOrDefault(false)
    }
}
