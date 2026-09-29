package com.yagay.ListCleaner.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookCallIdentityTest {
    @Test
    fun modernShortcutCallUsesExplicitLauncherCallerUidAndFlags() {
        val types = listOf(
            "int",
            "java.lang.String",
            "long",
            "java.lang.String",
            "java.util.List",
            "java.util.List",
            "android.content.ComponentName",
            "int",
            "int",
            "int",
            "int",
        )
        val args = listOf<Any?>(
            0,
            "com.example.launcher",
            0L,
            null,
            null,
            null,
            null,
            9,
            0,
            2222,
            10123,
        )

        val call = HookCallIdentity.shortcutCall(types, args, binderUid = 1000)

        assertEquals(10123, call.callerUid)
        assertEquals("com.example.launcher", call.callingPackage)
        assertEquals(9, call.queryFlags)
        assertTrue(call.explicitCallerUid)
    }

    @Test
    fun legacyShortcutCallFallsBackToBinderUid() {
        val types = listOf(
            "int",
            "java.lang.String",
            "long",
            "java.lang.String",
            "java.util.List",
            "android.content.ComponentName",
            "android.content.Intent",
            "int",
            "int",
        )
        val args = listOf<Any?>(0, "com.example.launcher", 0L, null, null, null, null, 8, 0)

        val call = HookCallIdentity.shortcutCall(types, args, binderUid = 10234)

        assertEquals(10234, call.callerUid)
        assertEquals(8, call.queryFlags)
        assertFalse(call.explicitCallerUid)
    }

    @Test
    fun serviceInternalUsesExplicitCallingUidInsteadOfBinderIdentity() {
        val types = listOf(
            "android.content.Intent",
            "java.lang.String",
            "long",
            "int",
            "int",
            "int",
            "boolean",
            "boolean",
        )
        val args = listOf<Any?>(null, null, 0L, 0, 10456, 3344, false, false)

        assertEquals(
            10456,
            HookCallIdentity.serviceCallerUid(
                "queryIntentServicesInternal",
                types,
                args,
                binderUid = 1000,
            )
        )
    }

    @Test
    fun publicServiceQueryKeepsBinderCaller() {
        assertEquals(
            10999,
            HookCallIdentity.serviceCallerUid(
                "queryIntentServices",
                listOf("android.content.Intent", "java.lang.String", "long", "int"),
                listOf(null, null, 0L, 0),
                binderUid = 10999,
            )
        )
    }
}
