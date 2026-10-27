package com.yagay.ListCleaner.domain

/**
 * Pure selection of effective Root component policies. A mode change never
 * removes a desired rule; it only controls which Android user's rules are
 * currently eligible for enforcement. Identifiers are already sanitized by
 * PersistentComponentStore before reaching this helper.
 */
internal object RootGlobalModePolicy {
    fun effectiveKeys(saved: Set<String>, pausedUsers: Set<Int>): Set<String> =
        saved.filterTo(linkedSetOf()) { key ->
            key.substringBefore('|').toIntOrNull() !in pausedUsers
        }

    fun userKeys(saved: Set<String>, userId: Int): Set<String> =
        saved.filterTo(linkedSetOf()) { key ->
            key.substringBefore('|').toIntOrNull() == userId
        }
}
