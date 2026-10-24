package com.yagay.ListCleaner.data


/** A published ShortcutInfo is not a disableable Android component. */
internal data class PublishedShortcut(
    val userId: Int,
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val enabled: Boolean,
    val manifest: Boolean,
    val dynamic: Boolean,
    val pinned: Boolean,
    val cached: Boolean
)
internal enum class ShortcutDiscoveryStatus {
    AVAILABLE, HOST_PERMISSION_REQUIRED, SERVICE_UNAVAILABLE, QUERY_FAILED
}
internal data class ShortcutDiscoveryResult(
    val status: ShortcutDiscoveryStatus,
    val shortcuts: List<PublishedShortcut> = emptyList(),
    val detail: String = "",
    val observedAt: Long = System.currentTimeMillis()
)
internal fun interface ShortcutQueryTransport {
    fun query(): ShortcutDiscoveryResult
}

/**
 * Public API transport. Does not claim launcher identity or change the HOME role.
 * Privileged transports must enforce caller authorization before returning data.
 */
