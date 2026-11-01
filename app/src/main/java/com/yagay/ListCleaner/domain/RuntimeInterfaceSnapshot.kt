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
    /** MIME and host are required to match a resolver capture to the tested Intent. */
    val requestMimeType: String = "",
    val requestHost: String = "",
    val capturedAtMillis: Long,
    val packages: List<String>,
    /** Exact flattened ComponentName identifiers, when the captured API exposes them. */
    val components: List<String> = emptyList(),
    /** Optional display labels keyed by component, supplied by the owning system manager. */
    val componentLabels: Map<String, String> = emptyMap(),
    /** Counts from the original Hook result before storage limits are applied. */
    val totalPackages: Int = 0,
    val totalComponents: Int = 0,
    val truncated: Boolean = false,
)

object RuntimeInterfaceSnapshotCodec {
    private const val PREFIX = "runtime_interface_post_filter_v1_"
    const val MAX_PACKAGES = 1_000
    const val MAX_CHARS = 160_000
    private val json = Json { ignoreUnknownKeys = true }

    fun key(kind: IntentKind): String = PREFIX + kind.name
    fun keyForCaller(kind: IntentKind, uid: Int): String = key(kind) + "_uid_" + uid
    /** Separate namespace: PackageManager observations must never overwrite adapter order. */
    fun keyForResolverOrder(kind: IntentKind): String = "resolver_adapter_order_v1_" + kind.name
    /** RoleController eligibility is independent of ACTION_ASSIST resolver and current holder. */
    fun keyForRoleEligibility(kind: IntentKind): String = "role_eligible_packages_v1_" + kind.name
    /** An NFC payment surface can contain the same component in multiple Android profiles. */
    fun keyForNfcPayment(userId: Int): String = "nfc_payment_services_v1_user_" + userId

    fun encode(snapshot: RuntimeInterfaceSnapshot): String {
        val originalPackages = snapshot.totalPackages.takeIf { it > 0 } ?: snapshot.packages.size
        val originalComponents = snapshot.totalComponents.takeIf { it > 0 } ?: snapshot.components.size
        var value = sanitize(snapshot).copy(
            totalPackages = originalPackages,
            totalComponents = originalComponents,
            truncated = snapshot.truncated ||
                originalPackages > snapshot.packages.size ||
                originalComponents > snapshot.components.size ||
                originalPackages > MAX_PACKAGES || originalComponents > MAX_PACKAGES ||
                originalPackages > sanitize(snapshot).packages.size ||
                originalComponents > sanitize(snapshot).components.size,
        )
        // Keep a bounded, explicit partial observation instead of dropping
        // the entire capture when many long component names are returned.
        while (true) {
            val encoded = json.encodeToString(RuntimeInterfaceSnapshot.serializer(), value)
            if (encoded.length <= MAX_CHARS) return encoded
            if (value.packages.isEmpty() && value.components.isEmpty()) return ""
            value = value.copy(
                packages = value.packages.take(value.packages.size / 2),
                components = value.components.take(value.components.size / 2),
                componentLabels = value.componentLabels.filterKeys {
                    it in value.components.take(value.components.size / 2)
                },
                truncated = true,
            )
        }
    }

    fun decode(encoded: String?, expectedKind: IntentKind): RuntimeInterfaceSnapshot? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CHARS) return null
        return runCatching {
            val parsed = json.decodeFromString(RuntimeInterfaceSnapshot.serializer(), encoded)
            if (parsed.version != 1 || parsed.kind != expectedKind.name ||
                parsed.capturedAtMillis <= 0L || parsed.callerUserId < 0 ||
                parsed.callerUid < -1 ||
                parsed.totalPackages < 0 || parsed.totalComponents < 0 ||
                parsed.packages.size > MAX_PACKAGES || parsed.components.size > MAX_PACKAGES ||
                parsed.componentLabels.size > MAX_PACKAGES
            ) return@runCatching null
            sanitize(parsed)
        }.getOrNull()
    }

    private fun sanitize(snapshot: RuntimeInterfaceSnapshot) = snapshot.copy(
        source = snapshot.source.take(64),
        callerPackage = snapshot.callerPackage.take(240),
        requestAction = snapshot.requestAction.take(240),
        requestMimeType = snapshot.requestMimeType.take(127),
        requestHost = snapshot.requestHost.take(253),
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
            .distinct().take(MAX_PACKAGES).toList(),
        componentLabels = snapshot.componentLabels.asSequence()
            .filter { (component, _) -> component in snapshot.components }
            .take(MAX_PACKAGES)
            .associate { (component, label) ->
                component to label.take(120).filter { it != '\n' && it != '\r' }
            },
    )
}
