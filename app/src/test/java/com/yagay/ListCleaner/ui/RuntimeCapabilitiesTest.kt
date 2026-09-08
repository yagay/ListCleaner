package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.RuntimeStatus
import com.yagay.ListCleaner.data.ResolverHost
import com.yagay.ListCleaner.data.ScopeDetection
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeCapabilitiesTest {
    @Test
    fun zeroHitsAreUnobservedNotUnsupported() {
        val module = ModuleStatus(connected = true, scopeKnown = true)
        val runtime = RuntimeStatus(ready = true)

        val states = runtimeCapabilities(module, runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun hitsPromoteOnlyObservedCapability() {
        val module = ModuleStatus(connected = true, scopeKnown = true)
        val runtime = RuntimeStatus(ready = true, queryHits = 4, orderingHits = 0, visibilityHits = 2)

        val states = runtimeCapabilities(module, runtime).associate { it.capability to it.state }

        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.FILTERING])
        assertEquals(CapabilityState.LOADED_UNOBSERVED, states[RuntimeCapability.ORDERING])
        assertEquals(CapabilityState.OBSERVED, states[RuntimeCapability.PACKAGE_VISIBILITY])
    }

    @Test
    fun missingScopeTakesPrecedenceOverHitCounts() {
        val module = ModuleStatus(
            connected = true,
            scopeKnown = true,
            grantedScope = emptySet(),
            detection = ScopeDetection(
                hosts = listOf(ResolverHost("system", "PackageManagerService", "system", setOf("global")))
            )
        )
        val runtime = RuntimeStatus(ready = true, queryHits = 9, orderingHits = 9, visibilityHits = 9)

        runtimeCapabilities(module, runtime).forEach {
            assertEquals(CapabilityState.MISSING_SCOPE, it.state)
        }
    }
}
