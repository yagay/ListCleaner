package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/** Two visible execution groups only: Root and LSPosed. Internal rule identities stay distinct. */
internal enum class CleanupExecutionGroup { ROOT, LSPOSED }
internal sealed interface CleanupCategoryId {
    data class Entry(val kind: IntentKind) : CleanupCategoryId
    data class Root(val kind: CleanupKind) : CleanupCategoryId
    data class Managed(val surface: ManagedComponentSurface) : CleanupCategoryId
}

internal fun CleanupCategoryId.executionGroup(): CleanupExecutionGroup = when (this) {
    is CleanupCategoryId.Root -> CleanupExecutionGroup.ROOT
    is CleanupCategoryId.Entry, is CleanupCategoryId.Managed -> CleanupExecutionGroup.LSPOSED
}

internal fun CleanupCategoryId.titleResource(): Int = when (this) {
    is CleanupCategoryId.Entry -> kind.titleRes()
    is CleanupCategoryId.Root -> kind.titleRes()
    is CleanupCategoryId.Managed -> surface.titleRes
}

internal object CleanupCategoryRegistry {
    fun lsposedCategories(): List<CleanupCategoryId> =
        IntentKind.entries.filter { it.isSelectableEntryKind() }.map { CleanupCategoryId.Entry(it) } +
            ManagedComponentSurface.entries.map { CleanupCategoryId.Managed(it) }

    /** Compatibility with existing Entry navigation, which retains its underlying editors. */
    fun entries(): List<CleanupCategoryId> = lsposedCategories()

    fun rootCategories(): List<CleanupCategoryId> =
        CleanupKind.entries.map { CleanupCategoryId.Root(it) }

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
