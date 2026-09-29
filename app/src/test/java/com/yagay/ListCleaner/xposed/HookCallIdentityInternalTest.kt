package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class HookCallIdentityInternalTest {
    @Test
    fun `generic package manager internal query prefers explicit caller uid`() {
        val types = listOf("android.content.Intent", "java.lang.String", "long", "int", "int", "int")
        val args = listOf<Any?>(null, null, 0L, 10, 12345, 999)
        assertEquals(
            12345,
            HookCallIdentity.packageManagerCallerUid(
                "queryIntentContentProvidersInternal",
                types,
                args,
                1000,
            )
        )
    }

    @Test
    fun `public package manager query keeps binder caller uid`() {
        assertEquals(
            23456,
            HookCallIdentity.packageManagerCallerUid(
                "queryIntentServices",
                listOf("android.content.Intent", "long", "int"),
                listOf(null, 0L, 10),
                23456,
            )
        )
    }
}
