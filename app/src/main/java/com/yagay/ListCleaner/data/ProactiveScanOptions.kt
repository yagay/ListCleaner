package com.yagay.ListCleaner.data

import com.yagay.ListCleaner.domain.IntentKind

/** Options apply only to proactive read-only discovery, not LSPosed observations. */
enum class ProactiveTrigger { ON_SCAN, MANUAL_ONLY, OFF }

data class ProactiveScanOptions(
    val trigger: ProactiveTrigger = ProactiveTrigger.ON_SCAN,
    val includeDirectShare: Boolean = true,
    val includeShortcutItems: Boolean = false,
    val cacheMinutes: Int = 5,
) {
    val requestedKinds: Set<IntentKind>
        get() = buildSet {
            if (includeDirectShare) add(IntentKind.DIRECT_SHARE)
            if (includeShortcutItems) add(IntentKind.SHORTCUT_ITEM)
        }

    fun mayQuery(manualRequest: Boolean): Boolean = requestedKinds.isNotEmpty() && when (trigger) {
        ProactiveTrigger.ON_SCAN -> true
        ProactiveTrigger.MANUAL_ONLY -> manualRequest
        ProactiveTrigger.OFF -> false
    }

    val cacheMillis: Long get() = cacheMinutes.coerceIn(1, 60) * 60_000L
}
