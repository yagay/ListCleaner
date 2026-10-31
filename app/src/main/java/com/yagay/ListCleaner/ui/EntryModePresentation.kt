package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/**
 * Effective mode for an Entry view. Null means that All contains several
 * different effective modes and must not be labelled with the global default.
 * Only currently selectable Entry kinds participate in the All comparison.
 */
internal fun currentEntryDisplayMode(
    kind: IntentKind?,
    globalMode: DisplayMode,
    overrides: Map<IntentKind, DisplayMode>,
): DisplayMode? {
    if (kind != null) return overrides[kind] ?: globalMode
    val modes = IntentKind.entries.asSequence()
        .filter { it.isSelectableEntryKind() }
        .map { overrides[it] ?: globalMode }
        .distinct()
        .take(2)
        .toList()
    return modes.singleOrNull() ?: if (modes.isEmpty()) globalMode else null
}

// Popup selection is a local draft; the caller applies it only after
// explicit confirmation. Dismiss/back discards this value without persisting.
internal data class EntryModeChangeDraft(
    val original: DisplayMode?,
    val pending: DisplayMode? = original,
) {
    val hasChanges: Boolean get() = pending != original

    fun select(mode: DisplayMode?): EntryModeChangeDraft = copy(pending = mode)
}
