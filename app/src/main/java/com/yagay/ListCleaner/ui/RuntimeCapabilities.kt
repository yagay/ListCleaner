package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.RuntimeStatus

enum class RuntimeCapability {
    FILTERING,
    ORDERING,
    PACKAGE_VISIBILITY
}

enum class CapabilityState {
    OBSERVED,
    LOADED_UNOBSERVED,
    MISSING_SCOPE,
    OUTDATED,
    DISCONNECTED,
    NOT_READY
}

data class CapabilityStatus(
    val capability: RuntimeCapability,
    val state: CapabilityState,
    val hits: Long
)

/**
 * Conservative capability projection for UI/diagnostics.
 *
 * A zero hit count is deliberately reported as LOADED_UNOBSERVED rather than unsupported: the
 * feature may simply not have been exercised since the hooked process started.
 */
fun runtimeCapabilities(module: ModuleStatus, runtime: RuntimeStatus): List<CapabilityStatus> {
    fun stateFor(hits: Long): CapabilityState = when {
        !module.connected -> CapabilityState.DISCONNECTED
        module.outdated -> CapabilityState.OUTDATED
        module.missingScope.isNotEmpty() -> CapabilityState.MISSING_SCOPE
        !runtime.ready -> CapabilityState.NOT_READY
        hits > 0 -> CapabilityState.OBSERVED
        else -> CapabilityState.LOADED_UNOBSERVED
    }

    return listOf(
        CapabilityStatus(RuntimeCapability.FILTERING, stateFor(runtime.queryHits), runtime.queryHits),
        CapabilityStatus(RuntimeCapability.ORDERING, stateFor(runtime.orderingHits), runtime.orderingHits),
        CapabilityStatus(RuntimeCapability.PACKAGE_VISIBILITY, stateFor(runtime.visibilityHits), runtime.visibilityHits)
    )
}
