package com.yagay.ListCleaner.xposed

/**
 * Process-local authoritative component policy shared by the List Cleaner Xposed entries.
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
    val digest: String = "",
)

internal object RuntimeComponentPolicy {
    @Volatile
    private var value = RuntimeComponentPolicySnapshot()

    fun publish(managerAppId: Int, protectedComponents: Set<String>, digest: String) {
        value = RuntimeComponentPolicySnapshot(
            authoritative = digest.isNotEmpty(),
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            digest = digest,
        )
    }

    fun snapshot(): RuntimeComponentPolicySnapshot = value
}
