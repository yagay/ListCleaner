package com.yagay.ListCleaner.ui

import android.content.Context
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BulkLockState {
    NONE,
    PARTIAL,
    FULL
}

/**
 * UI-only protection for bulk actions.
 *
 * Locks are intentionally independent from rule/priority ordering data and from the LSPosed runtime
 * configuration. Each logical page/category uses its own scope, so locking an app on one page never
 * changes another page.
 */
internal class BulkLockStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    private fun entries(): Set<String> = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toSet()
    private fun appKey(scope: String, appId: String) = listOf(scope, "app", appId).joinToString(SEPARATOR)
    private fun itemKey(scope: String, itemId: String) = listOf(scope, "item", itemId).joinToString(SEPARATOR)

    fun isAppLocked(scope: String, appId: String): Boolean = appKey(scope, appId) in entries()

    fun isItemLocked(scope: String, itemId: String): Boolean = itemKey(scope, itemId) in entries()

    fun isProtected(scope: String, appId: String, itemId: String): Boolean =
        isAppLocked(scope, appId) || isItemLocked(scope, itemId)

    fun state(scope: String, appId: String, itemIds: Collection<String>): BulkLockState {
        if (isAppLocked(scope, appId)) return BulkLockState.FULL
        val ids = itemIds.distinct()
        val lockedCount = ids.count { isItemLocked(scope, it) }
        return when {
            lockedCount == 0 -> BulkLockState.NONE
            ids.isNotEmpty() && lockedCount == ids.size -> BulkLockState.FULL
            else -> BulkLockState.PARTIAL
        }
    }

    @Synchronized
    fun toggleApp(scope: String, appId: String) {
        toggle(appKey(scope, appId))
    }

    @Synchronized
    fun toggleItem(scope: String, itemId: String) {
        toggle(itemKey(scope, itemId))
    }

    private fun toggle(key: String) {
        val next = entries().toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        prefs.edit().putStringSet(KEY_ENTRIES, next).apply()
        mutableRevision.value++
    }

    private companion object {
        const val PREFS = "bulk_action_locks"
        const val KEY_ENTRIES = "entries_v1"
        const val SEPARATOR = "\u001f"
    }
}

internal fun ruleBulkLockScope(kind: IntentKind?, preset: OpenPreset?): String =
    "rules:${kind?.name ?: "ALL"}:${preset?.name ?: "ALL"}"

internal fun priorityBulkLockScope(kind: IntentKind, preset: OpenPreset?): String =
    "priority:${kind.name}:${preset?.name ?: "ALL"}"

internal fun componentBulkLockScope(kind: CleanupKind?): String =
    "components:${kind?.name ?: "ALL"}"

internal fun componentBulkLockAppId(item: RootComponent): String =
    "${item.user}|${item.component.packageName}"
