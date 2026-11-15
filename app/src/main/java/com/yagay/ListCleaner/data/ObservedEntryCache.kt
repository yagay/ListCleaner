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

    /** Stable token for catalog cache invalidation when observed authority content changes. */
    fun revisionToken(): Long {
        val encoded = prefs.getString(KEY_ENTRIES, null).orEmpty()
        return (encoded.length.toLong() shl 32) xor (encoded.hashCode().toLong() and 0xffffffffL)
    }

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
        userId: Int = 0,
    ): List<ObservedEntryRecord> {
        var reconciled = snapshot()
        if (legacyEncoded != null && userId == 0) {
            reconciled = ObservedEntryCacheCodec.reconcileRemote(
                reconciled,
                ObservedEntryCacheCodec.decode(legacyEncoded),
            )
        }
        segmentedEncoded.forEach { (kind, encoded) ->
            // A successfully published Launcher snapshot is authoritative. Older read-only
            // LSPosed preference values must never erase the new provider-owned inventory.
            if (kind == IntentKind.SHORTCUT_ITEM &&
                prefs.getBoolean(LAUNCHER_AUTHORITY_PREFIX + userId, false)) return@forEach
            val remote = ObservedEntryCacheCodec.decode(encoded)
                .filter { it.kind == kind.name && it.userId == userId }
            // Launcher process owns a complete Shortcut Host inventory, while
            // older legacy aggregate observations stay delta-based.
            reconciled = if (kind in REMOTE_AUTHORITY_SNAPSHOT_KINDS ||
                kind == IntentKind.SHORTCUT_ITEM) {
                ObservedEntryCacheCodec.reconcileRemote(reconciled, remote, setOf(kind), userId)
            } else {
                ObservedEntryCacheCodec.merge(reconciled, remote)
            }
        }
        persist(reconciled)
        return reconciled
    }

    /**
     * The manager is the only writer for launcher inventories. An empty list is accepted
     * only after the trusted host confirms a completed query for this user.
     * Commit both the inventory and its authority marker atomically.
     */
    @Synchronized
    fun replaceLauncherSnapshot(userId: Int, entries: List<ObservedEntryRecord>): Boolean {
        if (userId !in 0..21474 || entries.size > ObservedEntryCacheCodec.MAX_ENTRIES) return false
        if (entries.any { it.kind != IntentKind.SHORTCUT_ITEM.name ||
                it.userId != userId || it.validatedOrNull() == null }) return false
        val updated = ObservedEntryCacheCodec.reconcileRemote(
            snapshot(), entries, setOf(IntentKind.SHORTCUT_ITEM), userId
        )
        val encoded = ObservedEntryCacheCodec.encode(updated)
        if (encoded.length > ObservedEntryCacheCodec.MAX_ENCODED_CHARS) return false
        return prefs.edit().putString(KEY_ENTRIES, encoded)
            .putBoolean(LAUNCHER_AUTHORITY_PREFIX + userId, true).commit()
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
        if (encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS &&
            prefs.getString(KEY_ENTRIES, null) != encoded
        ) {
            prefs.edit().putString(KEY_ENTRIES, encoded).apply()
        }
    }

    companion object {
        /** Legacy aggregate key retained for upgrade compatibility. */
        const val REMOTE_KEY = "observed_entries_v1"
        private const val REMOTE_KIND_PREFIX = "observed_entries_v2_kind_"
        private const val PREFS = "observed_entries_cache"
        private const val KEY_ENTRIES = "entries_v1"
        private const val LAUNCHER_AUTHORITY_PREFIX = "launcher_authority_user_"

        fun remoteKey(kind: IntentKind, userId: Int = 0): String =
            REMOTE_KIND_PREFIX + kind.name + if (userId == 0) "" else "_user_$userId"

        fun isRemoteKey(key: String?): Boolean =
            key == REMOTE_KEY || key?.startsWith(REMOTE_KIND_PREFIX) == true
    }
}

