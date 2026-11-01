package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/** Preview only active Entry categories; disabled rules remain saved in Modes. */
internal fun entryHeaderPreviewKinds(
    kind: IntentKind?,
    disabledKinds: Set<IntentKind> = emptySet(),
    disabledGroups: Set<EntryGroup> = emptySet(),
): List<IntentKind> {
    val candidates = if (kind != null) listOf(kind)
        else IntentKind.entries.filter { it.isSelectableEntryKind() }
    return candidates.filter { it !in disabledKinds && it.entryGroup() !in disabledGroups }
}
