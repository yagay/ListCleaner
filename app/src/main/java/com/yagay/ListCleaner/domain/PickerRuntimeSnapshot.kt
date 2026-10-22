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
    /** Diagnostics only; no document ID, URI, or user file contents are stored. */
    val actionCode: Int? = null,
    val stateClassName: String = "",
    val knownActionConstants: String = "",
    /** Counts from the original DocumentsUI return, even if storage is bounded. */
    val totalRoots: Int = 0,
    val truncated: Boolean = false,
)

object PickerRuntimeSnapshotCodec {
    const val REMOTE_KEY = "documentsui_matching_roots_preview_v1"
    /** Separate slots prevent successive Open/Save calls from overwriting one another. */
    fun keyForRequest(kind: String): String = REMOTE_KEY + "_" + kind
    const val MAX_ROOTS = 1_024
    const val MAX_CHARS = 180_000
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: PickerRuntimeSnapshot): String {
        val total = snapshot.totalRoots.takeIf { it > 0 } ?: snapshot.roots.size
        var value = sanitize(snapshot).copy(
            totalRoots = total,
            truncated = snapshot.truncated || total > snapshot.roots.size ||
                snapshot.roots.size > MAX_ROOTS ||
                total > sanitize(snapshot).roots.size,
        )
        while (true) {
            val encoded = json.encodeToString(PickerRuntimeSnapshot.serializer(), value)
            if (encoded.length <= MAX_CHARS) return encoded
            if (value.roots.isEmpty()) return ""
            value = value.copy(roots = value.roots.take(value.roots.size / 2),
                truncated = true)
        }
    }

    fun decode(value: String?): PickerRuntimeSnapshot? {
        if (value.isNullOrEmpty() || value.length > MAX_CHARS) return null
        return runCatching {
            val decoded = json.decodeFromString(PickerRuntimeSnapshot.serializer(), value)
            if (decoded.version != 1 || decoded.capturedAt <= 0L ||
                decoded.userId < 0 || decoded.totalRoots < 0 ||
                decoded.roots.size > MAX_ROOTS ||
                decoded.requestKind !in setOf("UNKNOWN", "GET_CONTENT", "OPEN_DOCUMENT", "CREATE_DOCUMENT", "DOCUMENT_ROOT")
            ) return@runCatching null
            sanitize(decoded)
        }.getOrNull()
    }

    private fun sanitize(value: PickerRuntimeSnapshot): PickerRuntimeSnapshot =
        value.copy(
            stateClassName = value.stateClassName.take(160),
            knownActionConstants = value.knownActionConstants.take(240),
            roots = value.roots
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
