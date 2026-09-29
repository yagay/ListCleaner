package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Kinds whose exact runtime/system-manager result can be mirrored back to the manager catalog. */
val OBSERVABLE_ENTRY_KINDS: Set<IntentKind> = setOf(
    IntentKind.SHORTCUT_ITEM,
    IntentKind.DIRECT_SHARE,
    IntentKind.ACCESSIBILITY,
    IntentKind.INPUT_METHOD,
    IntentKind.PRINT,
    IntentKind.CREDENTIAL_PROVIDER,
    IntentKind.VPN,
    IntentKind.NFC_HCE,
)

@Serializable
data class ObservedEntryRecord(
    val kind: String,
    val packageName: String,
    val syntheticClass: String,
    val label: String = "",
    val activityClass: String? = null,
    val observedAt: Long = 0L,
) {
    val key: String get() = "$kind|$packageName|$syntheticClass"

    fun validatedOrNull(): ObservedEntryRecord? {
        val parsedKind = runCatching { IntentKind.valueOf(kind) }.getOrNull()
            ?.takeIf { it in OBSERVABLE_ENTRY_KINDS }
            ?: return null
        if (!PackageIdentity.valid(packageName)) return null
        if (syntheticClass.isBlank() || syntheticClass.length > 512 ||
            syntheticClass.any { it.isWhitespace() || it.isISOControl() || it == '|' }
        ) return null
        val normalizedActivity = activityClass?.takeIf {
            it.isNotBlank() && it.length <= 512 && it.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '|' }
        }
        return copy(
            kind = parsedKind.name,
            label = label.trim().take(MAX_LABEL_CHARS),
            activityClass = normalizedActivity,
            observedAt = observedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    companion object {
        const val MAX_LABEL_CHARS = 160
    }
}

@Serializable
data class ObservedEntrySnapshot(
    val version: Int = 1,
    val entries: List<ObservedEntryRecord> = emptyList(),
)

object ObservedEntryCacheCodec {
    const val MAX_ENTRIES = 1024
    const val MAX_ENCODED_CHARS = 750_000
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(encoded: String?): List<ObservedEntryRecord> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) return emptyList()
        return runCatching {
            val snapshot = json.decodeFromString(ObservedEntrySnapshot.serializer(), encoded)
            if (snapshot.version != 1) return@runCatching emptyList()
            normalize(snapshot.entries)
        }.getOrDefault(emptyList())
    }

    fun encode(entries: Collection<ObservedEntryRecord>): String =
        json.encodeToString(
            ObservedEntrySnapshot.serializer(),
            ObservedEntrySnapshot(entries = normalize(entries)),
        )

    fun merge(
        existing: Collection<ObservedEntryRecord>,
        incoming: Collection<ObservedEntryRecord>,
    ): List<ObservedEntryRecord> {
        val byKey = LinkedHashMap<String, ObservedEntryRecord>()
        normalize(existing).forEach { byKey[it.key] = it }
        normalize(incoming).forEach { candidate ->
            val previous = byKey[candidate.key]
            if (previous == null || candidate.observedAt >= previous.observedAt) {
                byKey[candidate.key] = candidate
            }
        }
        return byKey.values
            .sortedByDescending { it.observedAt }
            .take(MAX_ENTRIES)
    }

    private fun normalize(entries: Collection<ObservedEntryRecord>): List<ObservedEntryRecord> =
        entries.asSequence()
            .mapNotNull(ObservedEntryRecord::validatedOrNull)
            .distinctBy { it.key }
            .sortedByDescending { it.observedAt }
            .take(MAX_ENTRIES)
            .toList()
}
