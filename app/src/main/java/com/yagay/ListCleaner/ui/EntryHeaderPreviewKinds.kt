package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/** All must choose a type; a selected Entry previews that exact system surface. */
internal fun entryHeaderPreviewKinds(kind: IntentKind?): List<IntentKind> =
    if (kind != null) listOf(kind)
    else IntentKind.entries.filter { it.isSelectableEntryKind() }
