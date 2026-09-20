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

    @Volatile
    private var cachedEntries: Set<String> =
        migrateLegacyDeepLinkLocks(prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toSet()).also { migrated ->
            if (migrated != prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toSet()) {
                prefs.edit().putStringSet(KEY_ENTRIES, migrated).apply()
            }
        }

    private fun migrateLegacyDeepLinkLocks(source: Set<String>): Set<String> {
        var changed = false
        val migrated = source.mapTo(linkedSetOf()) { entry ->
            if (!entry.startsWith("rules:BROWSER:HOST:") || "$SEPARATORitem$SEPARATOR" !in entry) {
                entry
            } else {
                val marker = "$SEPARATORitem$SEPARATOR"
                val index = entry.indexOf(marker)
                val prefix = entry.substring(0, index + marker.length)
                val id = entry.substring(index + marker.length)
                val parsed = com.yagay.ListCleaner.domain.ComponentRule.fromId(id)
                if (parsed?.kind == IntentKind.BROWSER) {
                    changed = true
                    prefix + parsed.copy(kind = IntentKind.DEEP_LINK).id
                } else entry
            }
        }
        return if (changed) migrated else source
    }

    private fun entries(): Set<String> = cachedEntries
    private fun appKey(scope: String, appId: String) = listOf(scope, "app", appId).joinToString(SEPARATOR)
    private fun itemKey(scope: String, itemId: String) = listOf(scope, "item", itemId).joinToString(SEPARATOR)

    fun isAppLocked(scope: String, appId: String): Boolean = appKey(scope, appId) in entries()

    fun isItemLocked(scope: String, itemId: String): Boolean = itemKey(scope, itemId) in entries()

    fun isProtected(scope: String, appId: String, itemId: String): Boolean {
        val current = entries()
        return appKey(scope, appId) in current || itemKey(scope, itemId) in current
    }

    fun state(scope: String, appId: String, itemIds: Collection<String>): BulkLockState {
        val current = entries()
        if (appKey(scope, appId) in current) return BulkLockState.FULL
        val ids = itemIds.distinct()
        val lockedCount = ids.count { itemKey(scope, it) in current }
        return when {
            lockedCount == 0 -> BulkLockState.NONE
            ids.isNotEmpty() && lockedCount == ids.size -> BulkLockState.FULL
            else -> BulkLockState.PARTIAL
        }
    }

    @Synchronized
    fun setAppLocked(scope: String, appId: String, itemIds: Collection<String>, locked: Boolean) {
        val current = entries().toMutableSet()
        val app = appKey(scope, appId)
        val ids = itemIds.distinct()
        if (locked) {
            current.add(app)
        } else {
            current.remove(app)
            ids.forEach { current.remove(itemKey(scope, it)) }
        }
        persist(current)
    }

    @Synchronized
    fun setItemLocked(scope: String, itemId: String, locked: Boolean) {
        val current = entries().toMutableSet()
        val key = itemKey(scope, itemId)
        if (locked) current.add(key) else current.remove(key)
        persist(current)
    }

    @Synchronized
    fun toggleApp(scope: String, appId: String, itemIds: Collection<String>) {
        val current = entries()
        val app = appKey(scope, appId)
        val ids = itemIds.distinct()
        val fullyLocked = app in current ||
            (ids.isNotEmpty() && ids.all { itemKey(scope, it) in current })
        setAppLocked(scope, appId, ids, !fullyLocked)
    }

    @Synchronized
    fun toggleItem(scope: String, itemId: String) {
        setItemLocked(scope, itemId, !isItemLocked(scope, itemId))
    }

    private fun persist(next: Set<String>) {
        val snapshot = next.toSet()
        cachedEntries = snapshot
        prefs.edit().putStringSet(KEY_ENTRIES, snapshot).apply()
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


internal fun browserRuleBulkLockScope(host: String): String =
    "rules:BROWSER:HOST:" + (com.yagay.ListCleaner.domain.normalizeBrowserHost(host) ?: host)

internal fun browserPriorityBulkLockScope(host: String): String =
    "priority:BROWSER:HOST:" + (com.yagay.ListCleaner.domain.normalizeBrowserHost(host) ?: host)
