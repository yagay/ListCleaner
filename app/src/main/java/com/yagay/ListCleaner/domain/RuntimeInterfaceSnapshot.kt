package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only, explicitly captured framework return values. Not proof of rendered UI. */
@Serializable
data class RuntimeInterfaceSnapshot(
    val version: Int = 1,
    val kind: String,
    val source: String,
    val callerUserId: Int,
    val callerPackage: String = "",
    val callerUid: Int = -1,
    val requestAction: String = "",
    val capturedAtMillis: Long,
    val packages: List<String>,
    /** Exact flattened ComponentName identifiers, when the captured API exposes them. */
    val components: List<String> = emptyList(),
)

object RuntimeInterfaceSnapshotCodec {
    private const val PREFIX = "runtime_interface_post_filter_v1_"
    const val MAX_PACKAGES = 300
    const val MAX_CHARS = 36_000
    private val json = Json { ignoreUnknownKeys = true }

    fun key(kind: IntentKind): String = PREFIX + kind.name

    fun encode(snapshot: RuntimeInterfaceSnapshot): String =
        json.encodeToString(RuntimeInterfaceSnapshot.serializer(), sanitize(snapshot))

    fun decode(encoded: String?, expectedKind: IntentKind): RuntimeInterfaceSnapshot? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CHARS) return null
        return runCatching {
            val parsed = json.decodeFromString(RuntimeInterfaceSnapshot.serializer(), encoded)
            if (parsed.version != 1 || parsed.kind != expectedKind.name ||
                parsed.capturedAtMillis <= 0L || parsed.callerUserId < 0 ||
                parsed.callerUid < -1 ||
                parsed.packages.size > MAX_PACKAGES || parsed.components.size > MAX_PACKAGES
            ) return@runCatching null
            sanitize(parsed)
        }.getOrNull()
    }

    private fun sanitize(snapshot: RuntimeInterfaceSnapshot) = snapshot.copy(
        source = snapshot.source.take(64),
        callerPackage = snapshot.callerPackage.take(240),
        requestAction = snapshot.requestAction.take(240),
        packages = snapshot.packages.asSequence()
            .filter { PackageIdentity.valid(it) }
            .distinct()
            .take(MAX_PACKAGES)
            .toList(),
        components = snapshot.components.asSequence()
            .filter { component ->
                val parts = component.split('/', limit = 2)
                parts.size == 2 && PackageIdentity.valid(parts[0]) &&
                    parts[1].isNotBlank() && parts[1].length <= 250 &&
                    parts[1].none { it == '\n' || it == '\r' }
            }
            .distinct().take(MAX_PACKAGES).toList()
    )
}
