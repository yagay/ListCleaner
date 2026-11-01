package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInterfaceSnapshotCodecTest {
    @Test fun postFilterReturnSnapshotIsKindBoundAndContainsNoPrivateTargets() {
        val snapshot = RuntimeInterfaceSnapshot(
            kind = IntentKind.DIRECT_SHARE.name,
            source = "DIRECT_SHARE_SERVICE_RETURN",
            callerUserId = 0,
            callerPackage = "com.android.intentresolver",
            capturedAtMillis = 123_456L,
            packages = listOf("com.example.a", "com.example.a", "com.example.b", "not valid!"),
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(snapshot)
        val decoded = RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.DIRECT_SHARE)
        assertEquals(listOf("com.example.a", "com.example.b"), decoded?.packages)
        assertEquals("DIRECT_SHARE_SERVICE_RETURN", decoded?.source)
        assertFalse(encoded.contains("shortcutId", ignoreCase = true))
        assertNull(RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.BROWSER))
    }

    @Test fun roleEligibilityHasDedicatedSlotIndependentOfIntentAndAdapterReturns() {
        val kind = IntentKind.ASSISTANT
        val eligible = RuntimeInterfaceSnapshotCodec.keyForRoleEligibility(kind)
        assertTrue(eligible.contains("role_eligible_packages"))
        assertFalse(eligible == RuntimeInterfaceSnapshotCodec.key(kind))
        assertFalse(eligible == RuntimeInterfaceSnapshotCodec.keyForCaller(kind, 10001))
        assertFalse(eligible == RuntimeInterfaceSnapshotCodec.keyForResolverOrder(kind))
    }

    @Test fun nfcPaymentSnapshotsKeepProfileKeysAndServiceLabels() {
        val component = "com.paypal.android/com.paypal.nfc.PaymentService"
        val capture = RuntimeInterfaceSnapshot(
            kind = IntentKind.NFC_HCE.name, source = "NFC_PAYMENT_FILTERED_RETURN",
            callerUserId = 10, callerPackage = "com.android.nfc",
            capturedAtMillis = 4000L,
            packages = listOf("com.paypal.android"),
            components = listOf(component),
            componentLabels = mapOf(component to "PayPal",
                "com.unrelated/.Unknown" to "Should never survive"),
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(capture)
        val decoded = RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.NFC_HCE)!!
        assertEquals(mapOf(component to "PayPal"), decoded.componentLabels)
        assertFalse(RuntimeInterfaceSnapshotCodec.keyForNfcPayment(0) ==
            RuntimeInterfaceSnapshotCodec.keyForNfcPayment(10))
        assertFalse(RuntimeInterfaceSnapshotCodec.keyForNfcPayment(10) ==
            RuntimeInterfaceSnapshotCodec.key(IntentKind.NFC_HCE))
    }

    @Test fun emptyReturnedListIsDistinguishableFromMissingHookCapture() {
        val empty = RuntimeInterfaceSnapshot(
            kind = IntentKind.BROWSER.name, source = "ROLE_CONTROLLER_RETURN",
            callerUserId = 0, capturedAtMillis = 100L, packages = emptyList()
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(empty)
        assertTrue(RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.BROWSER)?.packages?.isEmpty() == true)
        assertNull(RuntimeInterfaceSnapshotCodec.decode(null, IntentKind.BROWSER))
    }

    @Test fun unboundedOrCorruptedInputCannotEnterUi() {
        val value = RuntimeInterfaceSnapshot(
            kind = IntentKind.PRINT.name, source = "SYSTEM_MANAGER_RETURN",
            callerUserId = 0, capturedAtMillis = 123L,
            packages = List(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES + 30) { "com.example.pkg" + it },
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(value)
        assertTrue(RuntimeInterfaceSnapshotCodec.decode(encoded, IntentKind.PRINT)!!.packages.size
            <= RuntimeInterfaceSnapshotCodec.MAX_PACKAGES)
        assertNull(RuntimeInterfaceSnapshotCodec.decode("{", IntentKind.PRINT))
        assertNull(RuntimeInterfaceSnapshotCodec.decode("x".repeat(RuntimeInterfaceSnapshotCodec.MAX_CHARS + 1),
            IntentKind.PRINT))
    }
}
