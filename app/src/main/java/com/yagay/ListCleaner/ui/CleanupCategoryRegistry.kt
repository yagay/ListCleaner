package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.annotation.StringRes
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/**
 * One registry for all Cleanup families. Identity remains namespaced, because
 * a Root component is not an Entry rule or a managed Surface rule.
 */
internal sealed interface CleanupCategoryId {
    data class Entry(val kind: IntentKind) : CleanupCategoryId
    data class Root(val kind: CleanupKind) : CleanupCategoryId
    data class Managed(val surface: ManagedComponentSurface) : CleanupCategoryId
}

internal fun CleanupCategoryId.titleResource(): Int = when (this) {
    is CleanupCategoryId.Entry -> kind.titleRes()
    is CleanupCategoryId.Root -> kind.titleRes()
    is CleanupCategoryId.Managed -> surface.titleRes
}

internal object CleanupCategoryRegistry {
    fun entries(): List<CleanupCategoryId.Entry> =
        IntentKind.entries.filter { it.isSelectableEntryKind() }.map(CleanupCategoryId::Entry)

    fun rootCategories(): List<CleanupCategoryId> =
        CleanupKind.entries.map(CleanupCategoryId::Root) +
            ManagedComponentSurface.entries.map(CleanupCategoryId::Managed)

    fun <T : CleanupCategoryId> sorted(context: Context, categories: List<T>): List<T> {
        val comparator = displayNameComparator()
        return categories.sortedWith { first, second ->
            comparator.compare(
                context.getString(first.titleResource()),
                context.getString(second.titleResource())
            ).takeIf { it != 0 } ?: first.toString().compareTo(second.toString())
        }
    }
}
