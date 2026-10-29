package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.supportsPriorityEditing

/** One List First presentation; category choice lives in the editor filter. */
internal object HomeLayoutPolicy {
    val sortableKinds: Set<IntentKind> =
        IntentKind.entries.filter { it.supportsPriorityEditing() }.toSet()

    fun priorityKind(requested: IntentKind?): IntentKind? =
        if (requested == null) IntentKind.SHARE else requested.takeIf { it in sortableKinds }
}
