package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.kinds

/** Maps a selected sub-kind back to exactly one top-level category chip. */
internal object EntryCategorySelectionPolicy {
    fun selectedKind(
        group: EntryGroup,
        activeKind: IntentKind?,
        availableKinds: Set<IntentKind>? = null
    ): IntentKind? = activeKind?.takeIf {
        it.entryGroup() == group && it in group.kinds() &&
            (availableKinds == null || it in availableKinds)
    }

    fun allSelected(activeKind: IntentKind?): Boolean = activeKind == null
}
