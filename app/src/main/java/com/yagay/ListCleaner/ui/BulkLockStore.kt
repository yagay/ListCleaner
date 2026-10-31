package com.yagay.ListCleaner.ui

import android.content.Context
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.domain.ComponentRule
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
    private val selectedSpace = (context.applicationContext as? ListCleanerApp)?.spaces?.selected
    fun notifySpaceChanged() { mutableRevision.value++ }

    private fun scoped(scope: String): String {
        val user = selectedSpace?.value ?: 0
        if (user == 0) return scope // Existing user 0 lock keys are unchanged.
        val parts = scope.split(':', limit = 2)
        return if (parts.size == 2) "${parts[0]}:SPACE_$user:${parts[1]}" else scope
    }
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    @Volatile
    private var cachedEntries: Set<String> = run {
        val existing = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toSet()
        val deepLinkMigrated = migrateLegacyDeepLinkLocks(existing)
        val purgeLegacy = !prefs.getBoolean(KEY_DIRECT_SHARE_LEGACY_LOCKS_PURGED_V1, false)
        val cleaned = if (purgeLegacy) removeLegacyDirectShareLocks(deepLinkMigrated) else deepLinkMigrated
        if (cleaned != existing || purgeLegacy) {
            val editor = prefs.edit().putStringSet(KEY_ENTRIES, cleaned)
            if (purgeLegacy) editor.putBoolean(KEY_DIRECT_SHARE_LEGACY_LOCKS_PURGED_V1, true)
            check(editor.commit()) { "Unable to commit Direct Share legacy lock cleanup" }
        }
        cleaned
    }

    /**
     * Only remove Direct Share-specific UI locks left from old rules.
     * Locks in generic app groups may also protect unrelated entry kinds, so
     * only their explicitly identified DIRECT_SHARE item keys are removed.
     */
    private fun removeLegacyDirectShareLocks(source: Set<String>): Set<String> =
        source.filterTo(linkedSetOf()) { entry ->
            val parts = entry.split(SEPARATOR, limit = 3)
            if (parts.size != 3) return@filterTo true
            val scope = parts[0].split(':')
            val directShareScope = scope.firstOrNull() in setOf("rules", "priority") &&
                (scope.getOrNull(1) == IntentKind.DIRECT_SHARE.name ||
                    (scope.getOrNull(1)?.startsWith("SPACE_") == true &&
                        scope.getOrNull(2) == IntentKind.DIRECT_SHARE.name))
            val directShareItem = parts[0].startsWith("rules:") &&
                parts[1] == "item" &&
                ComponentRule.fromId(parts[2])?.kind == IntentKind.DIRECT_SHARE
            !directShareScope && !directShareItem
        }

    private fun migrateLegacyDeepLinkLocks(source: Set<String>): Set<String> {
        var changed = false
        val migrated = source.mapTo(linkedSetOf()) { entry ->
            if (!entry.startsWith("rules:BROWSER:HOST:") || "${SEPARATOR}item${SEPARATOR}" !in entry) {
                entry
            } else {
                val marker = "${SEPARATOR}item${SEPARATOR}"
                val index = entry.indexOf(marker)
                val prefix = entry.substring(0, index + marker.length)
                val id = entry.substring(index + marker.length)
                val parsed = ComponentRule.fromId(id)
                if (parsed?.kind == IntentKind.BROWSER) {
                    changed = true
                    prefix + parsed.copy(kind = IntentKind.DEEP_LINK).id
                } else entry
            }
        }
        return if (changed) migrated else source
    }

    private fun entries(): Set<String> = cachedEntries

    @Synchronized fun reloadFromDisk() {
        cachedEntries = migrateLegacyDeepLinkLocks(prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toSet())
        mutableRevision.value++
    }

    fun exportSnapshot(): Set<String> = entries().toSet()

    /** Validate backup keys before updating the live cache or affecting any bulk operation. */
    fun validateSnapshot(values: Collection<String>) {
        require(values.size <= 20_000)
        require(values.all { key ->
            key.length in 3..1024 &&
                key.split(SEPARATOR).let { parts ->
                    parts.size == 3 &&
                        (parts[0].startsWith("rules:") || parts[0].startsWith("priority:") ||
                            parts[0].startsWith("components:")) &&
                        parts[1] in setOf("app", "item") &&
                        parts[2].isNotBlank()
                }
        })
    }

    fun restoreSnapshot(values: Collection<String>) {
        validateSnapshot(values)
        persist(values.toSet())
    }
    private fun appKey(scope: String, appId: String) = listOf(scoped(scope), "app", appId).joinToString(SEPARATOR)
    private fun itemKey(scope: String, itemId: String) = listOf(scoped(scope), "item", itemId).joinToString(SEPARATOR)

    private fun itemBelongsToApp(appId: String, itemId: String): Boolean {
        if (itemId == appId) return true
        ComponentRule.fromId(itemId)?.let { return it.packageName == appId }

        // Root component app IDs are "user|package" and item IDs are
        // "user|KIND|package/class". Parse only enough to identify the owner package.
        val appParts = appId.split('|', limit = 2)
        val itemParts = itemId.split('|', limit = 3)
        if (appParts.size == 2 && itemParts.size == 3 && appParts[0] == itemParts[0]) {
            return itemParts[2].substringBefore('/') == appParts[1]
        }
        return false
    }

    private fun removeChildLocksForApp(current: MutableSet<String>, scope: String, appId: String) {
        val prefix = listOf(scoped(scope), "item").joinToString(SEPARATOR) + SEPARATOR
        current.removeAll { entry ->
            entry.startsWith(prefix) && itemBelongsToApp(appId, entry.removePrefix(prefix))
        }
    }

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
            // Clear hidden/stale child locks too, not only rows visible in the current scan.
            removeChildLocksForApp(current, scope, appId)
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
        const val KEY_DIRECT_SHARE_LEGACY_LOCKS_PURGED_V1 = "direct_share_legacy_locks_purged_v1"
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
