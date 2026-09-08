package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.data.ResolverHost
import com.yagay.ListCleaner.data.ScopeDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeCapabilitiesTest {
    private val systemHost = ResolverHost("system", "PackageManagerService", "system", setOf("global"))
    private val resolverHost = ResolverHost(
        "com.android.intentresolver",
        "com.android.intentresolver.ResolverActivity",
        "com.android.intentresolver",
        setOf("share")
    )

    private fun currentTarget(process: String) = RunningTargetStatus(
        process,
        "UP_TO_DATE",
        BuildConfig.VERSION_CODE.toLong()
    )

    private fun healthyModule(): ModuleStatus = ModuleStatus(
        connected = true,
        scopeKnown = true,
        grantedScope = setOf("system", "com.android.intentresolver"),
        runningTargets = listOf(currentTarget("system"), currentTarget("com.android.intentresolver")),
        detection = ScopeDetection(hosts = listOf(systemHost, resolverHost))
    )

    @Test
    fun zeroHitsAreUnobservedNotUnsupported() {
        val states = runtimeCapabilities(healthyModule(), RuntimeStatus(ready = true))
            .associate { it.capability to it.state }

        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun hitsPromoteOnlyObservedCapability() {
        val runtime = RuntimeStatus(ready = true, queryHits = 4, orderingHits = 0, visibilityHits = 2)
        val states = runtimeCapabilities(healthyModule(), runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun missingResolverScopeOnlyAffectsOrdering() {
        val module = healthyModule().copy(grantedScope = setOf("system"))
        val runtime = RuntimeStatus(ready = true, queryHits = 9, orderingHits = 9, visibilityHits = 9)
        val states = runtimeCapabilities(module, runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.MISSING_SCOPE, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun missingSystemScopeDoesNotPoisonResolverOrdering() {
        val module = healthyModule().copy(grantedScope = setOf("com.android.intentresolver"))
        val runtime = RuntimeStatus(ready = true, queryHits = 9, orderingHits = 9, visibilityHits = 9)
        val states = runtimeCapabilities(module, runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.MISSING_SCOPE, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.MISSING_SCOPE, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun staleResolverOnlyAffectsOrdering() {
        val staleResolver = RunningTargetStatus(
            "com.android.intentresolver",
            "STALE",
            BuildConfig.VERSION_CODE.toLong() - 1
        )
        val module = healthyModule().copy(
            runningTargets = listOf(currentTarget("system"), staleResolver)
        )
        val runtime = RuntimeStatus(ready = true, queryHits = 3, orderingHits = 3, visibilityHits = 3)
        val states = runtimeCapabilities(module, runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.OUTDATED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun absentResolverHostIsNotReportedAsLoaded() {
        val module = healthyModule().copy(
            grantedScope = setOf("system"),
            runningTargets = listOf(currentTarget("system")),
            detection = ScopeDetection(hosts = listOf(systemHost))
        )
        val states = runtimeCapabilities(module, RuntimeStatus(ready = true))
            .associate { it.capability to it.state }

        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.NOT_READY, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun systemTargetAloneDoesNotCountAsResolverLoaded() {
        val module = healthyModule().copy(
            runningTargets = listOf(currentTarget("system"))
        )

        assertFalse(module.resolverLoaded)
    }
}
