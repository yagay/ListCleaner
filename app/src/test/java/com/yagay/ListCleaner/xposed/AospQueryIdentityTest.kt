package com.yagay.ListCleaner.xposed

import org.junit.Assert.*
import org.junit.Test

class AospQueryIdentityTest {
    private val prefix = listOf("android.content.Intent", "java.lang.String", "long")

    @Test fun activityUidBeforeUserIsNotAServiceLayout() {
        val types = prefix + listOf("int", "int")
        val args = listOf<Any?>(null, null, 0L, 10345, 10)
        assertEquals(10, HookCallIdentity.packageManagerTargetUserId("queryIntentActivitiesInternal", types, args))
        assertEquals(10345, HookCallIdentity.packageManagerCallerUid("queryIntentActivitiesInternal", types, args, 1000))
    }

    @Test fun android16ActivityPidDoesNotBecomeCallerOrUser() {
        val types = prefix + listOf("long", "int", "int", "int", "boolean", "boolean")
        val args = listOf<Any?>(null, null, 0L, 0L, 10345, 2345, 10, false, true)
        assertEquals(10, HookCallIdentity.packageManagerTargetUserId("queryIntentActivitiesInternal", types, args))
        assertEquals(10345, HookCallIdentity.packageManagerCallerUid("queryIntentActivitiesInternal", types, args, 1000))
    }

    @Test fun singleUserOverloadPreservesBinderCaller() {
        val types = prefix + "int"
        val args = listOf<Any?>(null, null, 0L, 10)
        assertEquals(10, HookCallIdentity.packageManagerTargetUserId("queryIntentActivitiesInternal", types, args))
        assertEquals(10345, HookCallIdentity.packageManagerCallerUid("queryIntentActivitiesInternal", types, args, 10345))
    }

    @Test fun legacyIntFlagsAreNotUserIds() {
        val types = listOf("android.content.Intent", "java.lang.String", "int", "int", "int", "boolean", "boolean")
        val args = listOf<Any?>(null, null, 65536, 10345, 10, false, true)
        assertEquals(10, HookCallIdentity.packageManagerTargetUserId("queryIntentActivitiesInternal", types, args))
    }

    @Test fun managerSelectionFlagsAreNotPrintUserId() {
        assertEquals(10, HookCallIdentity.managerTargetUserId("getPrintServices", listOf("int", "int"), listOf(3, 10)))
        assertEquals(10, HookCallIdentity.managerTargetUserId("getCredentialProviderServices", listOf("int", "int"), listOf(10, 3)))
        assertNull(HookCallIdentity.managerTargetUserId("unknown", listOf("int"), listOf(10)))
        assertNull(HookCallIdentity.managerTargetUserId("getInputMethodList", listOf("int"), listOf(-2)))
    }
}

