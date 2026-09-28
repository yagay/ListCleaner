package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.DisplayMode

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
    val digest: String = "",
)

internal object RuntimeComponentPolicy {
    @Volatile
    private var value = RuntimeComponentPolicySnapshot()

    fun publish(
        managerAppId: Int,
        protectedComponents: Set<String>,
        digest: String,
        displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
        entryRules: Set<String> = emptySet(),
    ) {
        value = RuntimeComponentPolicySnapshot(
            authoritative = digest.isNotEmpty(),
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            displayMode = displayMode,
            entryRules = entryRules.toSet(),
            digest = digest,
        )
    }

    fun snapshot(): RuntimeComponentPolicySnapshot = value
}
