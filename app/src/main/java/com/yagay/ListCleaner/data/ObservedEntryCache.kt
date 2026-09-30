package com.yagay.ListCleaner.data

import android.content.Context
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.REMOTE_AUTHORITY_SNAPSHOT_KINDS

/** Manager-side persistent cache for runtime-observed surfaces. */
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

    /**
     * Read the legacy aggregate snapshot plus independently persisted per-kind snapshots.
     * Per-kind keys replace only their own authority surface, preventing one Xposed process from
     * erasing another process' observation while remaining compatible with pre-segmentation data.
     */
    @Synchronized
    fun synchronizeRemoteEncoded(
        legacyEncoded: String?,
        segmentedEncoded: Map<IntentKind, String?> = emptyMap(),
    ): List<ObservedEntryRecord> {
        var reconciled = snapshot()
        if (legacyEncoded != null) {
            reconciled = ObservedEntryCacheCodec.reconcileRemote(
                reconciled,
                ObservedEntryCacheCodec.decode(legacyEncoded),
            )
        }
        segmentedEncoded.forEach { (kind, encoded) ->
            val remote = ObservedEntryCacheCodec.decode(encoded)
                .filter { it.kind == kind.name }
            reconciled = if (kind in REMOTE_AUTHORITY_SNAPSHOT_KINDS) {
                ObservedEntryCacheCodec.reconcileRemote(reconciled, remote, setOf(kind))
            } else {
                ObservedEntryCacheCodec.merge(reconciled, remote)
            }
        }
        persist(reconciled)
        return reconciled
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
        /** Legacy aggregate key retained for upgrade compatibility. */
        const val REMOTE_KEY = "observed_entries_v1"
        private const val REMOTE_KIND_PREFIX = "observed_entries_v2_kind_"
        private const val PREFS = "observed_entries_cache"
        private const val KEY_ENTRIES = "entries_v1"

        fun remoteKey(kind: IntentKind): String = REMOTE_KIND_PREFIX + kind.name

        fun isRemoteKey(key: String?): Boolean =
            key == REMOTE_KEY || key?.startsWith(REMOTE_KIND_PREFIX) == true
    }
}
