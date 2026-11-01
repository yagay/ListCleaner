package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.entryGroup
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import com.yagay.ListCleaner.domain.DOCUMENTS_UI_PREVIEW_KINDS
import com.yagay.ListCleaner.domain.isLegacyDocumentsUiCategory

/** Preview only active Entry categories; disabled rules remain saved in Modes. */
internal fun entryHeaderPreviewKinds(
    kind: IntentKind?,
    disabledKinds: Set<IntentKind> = emptySet(),
    disabledGroups: Set<EntryGroup> = emptySet(),
): List<IntentKind> {
    val candidates = when (kind) {
        IntentKind.DOCUMENT_PROVIDER -> DOCUMENTS_UI_PREVIEW_KINDS
        null -> IntentKind.entries.filter { it.isSelectableEntryKind() && !it.isLegacyDocumentsUiCategory() }
        else -> listOf(kind)
    }
    return candidates.filter { it !in disabledKinds && it.entryGroup() !in disabledGroups }
}
