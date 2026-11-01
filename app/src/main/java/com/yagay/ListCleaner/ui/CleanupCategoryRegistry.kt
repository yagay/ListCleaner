package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
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
        IntentKind.entries.filter { it.isSelectableEntryKind() }.map { CleanupCategoryId.Entry(it) }

    fun rootCategories(): List<CleanupCategoryId> =
        CleanupKind.entries.map { CleanupCategoryId.Root(it) } +
            ManagedComponentSurface.entries.map { CleanupCategoryId.Managed(it) }

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

/** Presentation metadata shared by category picker, Modes and preview. */
internal fun CleanupCategoryId.icon(): ImageVector = when (this) {
    is CleanupCategoryId.Entry -> kind.hierarchyIcon()
    is CleanupCategoryId.Root -> kind.hierarchyIcon()
    is CleanupCategoryId.Managed -> when (surface) {
        ManagedComponentSurface.SETTINGS_APPS -> Icons.Rounded.Settings
        ManagedComponentSurface.NOTIFICATIONS -> Icons.Rounded.Notifications
    }
}
