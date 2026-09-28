package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind

/**
 * Process-local authoritative component/entry policy shared by the List Cleaner Xposed entries.
 *
 * Runtime Probe v2 commits one immutable snapshot. Entry-rule construction is staged until the
 * verified runtime config publishes its manager identity, root policy and digest, avoiding a
 * transient mix of new entry rules with an old authoritative component snapshot.
 */
internal data class RuntimeComponentPolicySnapshot(
    val authoritative: Boolean = false,
    val managerAppId: Int = -1,
    val protectedComponents: Set<String> = emptySet(),
    val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val entryRules: Set<String> = emptySet(),
    val entryPriorities: Map<IntentKind, List<String>> = emptyMap(),
    val digest: String = "",
)

internal object RuntimeComponentPolicy {
    private data class PendingEntryPolicy(
        val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
        val entryRules: Set<String> = emptySet(),
        val entryPriorities: Map<IntentKind, List<String>> = emptyMap(),
    )

    @Volatile private var value = RuntimeComponentPolicySnapshot()
    @Volatile private var pendingEntryPolicy = PendingEntryPolicy()

    /** Resolver snapshot construction stages non-destructive entry rules for the next atomic commit. */
    fun publishEntryRules(
        displayMode: DisplayMode,
        entryRules: Set<String>,
        entryPriorities: Map<IntentKind, List<String>>,
    ) {
        pendingEntryPolicy = PendingEntryPolicy(
            displayMode = displayMode,
            entryRules = entryRules.toSet(),
            entryPriorities = entryPriorities.mapValues { it.value.toList() },
        )
    }

    /** Root/component identity and staged entry rules become authoritative in one volatile write. */
    fun publish(managerAppId: Int, protectedComponents: Set<String>, digest: String) {
        val pending = pendingEntryPolicy
        value = RuntimeComponentPolicySnapshot(
            authoritative = digest.isNotEmpty(),
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            displayMode = pending.displayMode,
            entryRules = pending.entryRules,
            entryPriorities = pending.entryPriorities,
            digest = digest,
        )
    }

    fun snapshot(): RuntimeComponentPolicySnapshot = value
}
