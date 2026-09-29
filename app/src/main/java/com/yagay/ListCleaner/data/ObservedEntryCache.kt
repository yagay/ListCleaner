package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.domain.ObservedEntryRecord

/** Manager-side persistent cache for runtime-observed shortcut surfaces. */
internal class ObservedEntryCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun snapshot(): List<ObservedEntryRecord> =
        ObservedEntryCacheCodec.decode(prefs.getString(KEY_ENTRIES, null))

    @Synchronized
    fun merge(entries: Collection<ObservedEntryRecord>): List<ObservedEntryRecord> {
        if (entries.isEmpty()) return snapshot()
        val current = snapshot()
        val merged = ObservedEntryCacheCodec.merge(current, entries)
        persist(merged)
        return merged
    }

    @Synchronized
    fun mergeEncoded(encoded: String?): List<ObservedEntryRecord> {
        val remote = ObservedEntryCacheCodec.decode(encoded)
        return if (remote.isEmpty()) snapshot() else merge(remote)
    }

    @Synchronized
    fun prune(validKeys: Set<String>) {
        if (validKeys.isEmpty()) return
        val current = snapshot()
        val retained = current.filter { it.key in validKeys }
        if (retained.size != current.size) persist(retained)
    }

    private fun persist(entries: Collection<ObservedEntryRecord>) {
        val encoded = ObservedEntryCacheCodec.encode(entries)
        if (encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS) {
            prefs.edit().putString(KEY_ENTRIES, encoded).apply()
        }
    }

    companion object {
        const val REMOTE_KEY = "observed_entries_v1"
        private const val PREFS = "observed_entries_cache"
        private const val KEY_ENTRIES = "entries_v1"
    }
}
