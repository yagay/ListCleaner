package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset

/** Shared rule/priority selection context; changing kind clears incompatible sub-contexts once. */
internal data class EntryContext(
    val kind: IntentKind? = null,
    val openPreset: OpenPreset? = null,
    val browserHost: String? = null,
) {
    fun withKind(value: IntentKind?): EntryContext = copy(
        kind = value,
        openPreset = openPreset.takeIf { value == IntentKind.OPEN },
        browserHost = browserHost.takeIf { value == IntentKind.DEEP_LINK },
    )
}
