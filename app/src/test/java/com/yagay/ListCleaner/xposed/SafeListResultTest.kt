package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeListResultTest {
    private class FakeParceledListSlice(private val values: List<*>) {
        fun getList(): List<*> = values
    }

    private class BrokenParceledListSlice(private val value: String)

    @Test
    fun listPassesThroughWithoutReflection() {
        val extractor = SafeListResultExtractor {}
        val original = listOf("a", "b")
        val result = extractor.extract(original)!!

        assertEquals(original, result.values)
        assertEquals(listOf("x"), result.rebuild(listOf("x")))
    }

    @Test
    fun parceledListLikeObjectCanBeReadAndRebuilt() {
        val extractor = SafeListResultExtractor {}
        val result = extractor.extract(FakeParceledListSlice(listOf("a", "b")))!!

        assertEquals(listOf("a", "b"), result.values)
        val rebuilt = result.rebuild(listOf("c")) as FakeParceledListSlice
        assertEquals(listOf("c"), rebuilt.getList())
    }

    @Test
    fun unsupportedOemShapeFailsOpenWithoutThrowing() {
        val messages = mutableListOf<String>()
        val extractor = SafeListResultExtractor(messages::add)

        assertNull(extractor.extract(BrokenParceledListSlice("x")))
        assertEquals(true, messages.any { it.startsWith("LIST_RESULT_ACCESSOR_UNAVAILABLE") })
    }
}
