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
    @Synchronized
    fun merge(entries: Collection<ObservedEntryRecord>): Boolean {
        if (entries.isEmpty()) return true
        return update { previous -> ObservedEntryCacheCodec.merge(previous, entries) }
    }

    /**
     * Replace one authority surface atomically instead of accumulating stale rows forever.
     * Empty snapshots are meaningful: they clear the previous rows for that kind.
     */
    @Synchronized
    fun replaceKind(kind: IntentKind, entries: Collection<ObservedEntryRecord>): Boolean = update { previous ->
        val retained = previous.filterNot { it.kind == kind.name }
        ObservedEntryCacheCodec.merge(retained, entries)
    }

    private fun update(transform: (List<ObservedEntryRecord>) -> List<ObservedEntryRecord>): Boolean = runCatching {
        val previousEncoded = preferences.getString(ObservedEntryCache.REMOTE_KEY, null)
        val previous = ObservedEntryCacheCodec.decode(previousEncoded)
        val encoded = ObservedEntryCacheCodec.encode(transform(previous))
        if (encoded == previousEncoded) return@runCatching true
        check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        preferences.edit().putString(ObservedEntryCache.REMOTE_KEY, encoded).commit()
    }.onFailure {
        record("OBSERVED_CACHE_WRITE_FAILED error=${it.javaClass.name}")
    }.getOrDefault(false)
}
