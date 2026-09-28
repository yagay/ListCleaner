package com.yagay.ListCleaner.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceSessionRegistryTest {
    @Test
    fun rebindingSameObjectCreatesNewGeneration() {
        val registry = GenerationRegistry<Any>()
        val service = Any()

        val first = registry.bind(service)
        val second = registry.bind(service)

        assertNotEquals(first.generation, second.generation)
        assertFalse(registry.isCurrent(first))
        assertTrue(registry.isCurrent(second))
    }

    @Test
    fun staleClearCannotRemoveNewBinding() {
        val registry = GenerationRegistry<Any>()
        val firstService = Any()
        val secondService = Any()

        registry.bind(firstService)
        val second = registry.bind(secondService)

        assertNull(registry.clear(firstService))
        assertTrue(registry.isCurrent(second))
        assertSame(secondService, registry.snapshot()?.value)
    }

    @Test
    fun clearedTokenNeverBecomesCurrentAgain() {
        val registry = GenerationRegistry<Any>()
        val service = Any()

        val first = registry.bind(service)
        registry.clear(service)
        val second = registry.bind(service)

        assertFalse(registry.isCurrent(first))
        assertTrue(registry.isCurrent(second))
    }

    @Test
    fun nullRepresentsOnlyNoActiveBinding() {
        val registry = GenerationRegistry<Any>()

        assertTrue(registry.isCurrent(null))
        registry.bind(Any())
        assertFalse(registry.isCurrent(null))
    }
}
