package com.yagay.ListCleaner.xposed

import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DirectShareActiveScanBridgeTest {
    private class AospSignature {
        @Suppress("UNUSED_PARAMETER")
        fun getShareTargets(callingPackage: String, filter: IntentFilter, userId: Int): List<Any> =
            emptyList()
    }

    private class IncompatibleSignature {
        @Suppress("UNUSED_PARAMETER")
        fun getShareTargets(callingPackage: String, filter: String, userId: Int): List<Any> =
            emptyList()
    }

    @Test fun resolvesTheFrameworkLocalServiceClassNotTheServerImplementation() {
        assertEquals("android.content.pm.ShortcutServiceInternal",
            DirectShareActiveScanBridge.SHORTCUT_SERVICE_INTERNAL_CLASS)
    }

    @Test fun findsOnlyTheThreeArgumentAospShareTargetMethod() {
        assertNotNull(DirectShareActiveScanBridge.findGetShareTargets(AospSignature::class.java))
        assertNull(DirectShareActiveScanBridge.findGetShareTargets(IncompatibleSignature::class.java))
    }
}
