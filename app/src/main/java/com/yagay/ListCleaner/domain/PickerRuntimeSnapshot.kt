package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A timestamped snapshot of RootsCache.getMatchingRootsBlocking() returned inside
 * DocumentsUI. These are returned roots, NOT a guarantee of pixels actually drawn.
 * Do not include document IDs, file names, URI grants or full document paths.
 */
@Serializable
data class PickerRuntimeRoot(
    val packageName: String,
    val label: String,
    val syntheticId: String,
    val systemOwned: Boolean,
)

@Serializable
data class PickerRuntimeSnapshot(
    val version: Int = 1,
    val capturedAt: Long,
    val userId: Int,
    val roots: List<PickerRuntimeRoot>,
    /** DocumentsUI State action, if readable from the matching-roots invocation. */
    val requestKind: String = "UNKNOWN",
)

object PickerRuntimeSnapshotCodec {
    const val REMOTE_KEY = "documentsui_matching_roots_preview_v1"
    /** Separate slots prevent successive Open/Save calls from overwriting one another. */
    fun keyForRequest(kind: String): String = REMOTE_KEY + "_" + kind
    const val MAX_ROOTS = 256
    const val MAX_CHARS = 90_000
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: PickerRuntimeSnapshot): String = json.encodeToString(
        PickerRuntimeSnapshot.serializer(), sanitize(snapshot)
    )

    fun decode(value: String?): PickerRuntimeSnapshot? {
        if (value.isNullOrEmpty() || value.length > MAX_CHARS) return null
        return runCatching {
            val decoded = json.decodeFromString(PickerRuntimeSnapshot.serializer(), value)
            if (decoded.version != 1 || decoded.capturedAt <= 0L ||
                decoded.userId < 0 || decoded.roots.size > MAX_ROOTS ||
                decoded.requestKind !in setOf("UNKNOWN", "GET_CONTENT", "OPEN_DOCUMENT", "CREATE_DOCUMENT")
            ) return@runCatching null
            sanitize(decoded)
        }.getOrNull()
    }

    private fun sanitize(value: PickerRuntimeSnapshot): PickerRuntimeSnapshot =
        value.copy(roots = value.roots
            .filter {
                it.packageName.length in 1..240 && it.label.isNotBlank() &&
                    it.syntheticId.length in 1..200 &&
                    !it.packageName.contains('\n') && !it.syntheticId.contains('\n')
            }
            .distinctBy(PickerRuntimeRoot::syntheticId)
            .take(MAX_ROOTS)
            .map {
                it.copy(label = it.label.trim().take(160))
            })
}
