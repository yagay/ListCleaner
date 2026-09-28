package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind

/**
 * Process-local authoritative component/entry policy shared by the List Cleaner Xposed entries.
 *
 * Runtime Probe v2 updates this snapshot atomically with the main resolver configuration, avoiding
 * stale RemotePreferences reads in ComponentStateGuardModule and ComponentDiscoveryFilterModule.
 * Before the manager has pushed a verified runtime config, those modules keep their cold-start
 * RemotePreferences fallback.
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
    @Volatile
    private var value = RuntimeComponentPolicySnapshot()

    /** Root component state is published after a verified atomic config is applied. */
    fun publish(managerAppId: Int, protectedComponents: Set<String>, digest: String) {
        val current = value
        value = current.copy(
            authoritative = digest.isNotEmpty(),
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            digest = digest,
        )
    }

    /** Resolver snapshot construction publishes non-destructive entry rules from the same config. */
    fun publishEntryRules(
        displayMode: DisplayMode,
        entryRules: Set<String>,
        entryPriorities: Map<IntentKind, List<String>>,
    ) {
        val current = value
        value = current.copy(
            displayMode = displayMode,
            entryRules = entryRules.toSet(),
            entryPriorities = entryPriorities.mapValues { it.value.toList() },
        )
    }

    fun snapshot(): RuntimeComponentPolicySnapshot = value
}
