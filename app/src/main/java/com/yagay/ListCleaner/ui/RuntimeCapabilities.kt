package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.domain.RuntimeProtocol

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
 * Requirements are evaluated per capability. Resolver ordering must not be marked broken merely
 * because an unrelated target is stale, and system-server filtering/visibility must not inherit a
 * missing Resolver scope. A zero hit count remains LOADED_UNOBSERVED rather than unsupported.
 */
fun runtimeCapabilities(module: ModuleStatus, runtime: RuntimeStatus): List<CapabilityStatus> {
    val resolverHosts = module.detection.hosts.filter { it.packageName != "system" }

    fun stateFor(capability: RuntimeCapability, hits: Long): CapabilityState {
        if (!module.connected) return CapabilityState.DISCONNECTED
        if (!module.scopeKnown) return CapabilityState.NOT_READY

        val requiredPackages: Set<String>
        val requiredProcesses: Set<String>
        when (capability) {
            RuntimeCapability.FILTERING,
            RuntimeCapability.PACKAGE_VISIBILITY -> {
                requiredPackages = setOf("system")
                requiredProcesses = setOf("system")
            }
            RuntimeCapability.ORDERING -> {
                if (resolverHosts.isEmpty()) return CapabilityState.NOT_READY
                requiredPackages = resolverHosts.map { it.packageName }.toSet()
                requiredProcesses = resolverHosts.map { it.processName }.toSet()
            }
        }

        if ((requiredPackages - module.grantedScope).isNotEmpty()) return CapabilityState.MISSING_SCOPE

        val relevantTargets = module.runningTargets.filter { it.processName in requiredProcesses }
        if (relevantTargets.any {
                !RuntimeProtocol.current(it.state, it.version, BuildConfig.VERSION_CODE.toLong())
            }) {
            return CapabilityState.OUTDATED
        }
        if (relevantTargets.isEmpty()) return CapabilityState.NOT_READY
        if (!runtime.ready) return CapabilityState.NOT_READY
        return if (hits > 0) CapabilityState.OBSERVED else CapabilityState.LOADED_UNOBSERVED
    }

    return listOf(
        CapabilityStatus(
            RuntimeCapability.FILTERING,
            stateFor(RuntimeCapability.FILTERING, runtime.queryHits),
            runtime.queryHits
        ),
        CapabilityStatus(
            RuntimeCapability.ORDERING,
            stateFor(RuntimeCapability.ORDERING, runtime.orderingHits),
            runtime.orderingHits
        ),
        CapabilityStatus(
            RuntimeCapability.PACKAGE_VISIBILITY,
            stateFor(RuntimeCapability.PACKAGE_VISIBILITY, runtime.visibilityHits),
            runtime.visibilityHits
        )
    )
}
