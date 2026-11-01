package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcPaymentPreviewTest {
    private val now = 200_000L
    private val kind = IntentKind.NFC_HCE
    private val paypal = "com.paypal.android.p2pmobile/com.paypal.nfc.PaymentService"
    private val wallet = "com.google.android.apps.walletnfcrel/.HceService"

    private fun capture(user: Int, components: List<String>,
                        at: Long = now - 100L): RuntimeInterfaceSnapshot =
        RuntimeInterfaceSnapshot(
            kind = kind.name, source = "NFC_PAYMENT_FILTERED_RETURN",
            callerUserId = user, callerPackage = "com.android.nfc",
            callerUid = 1027, requestAction = "android.nfc.cardemulation.payment",
            capturedAtMillis = at,
            packages = components.map { it.substringBefore('/') },
            components = components,
            componentLabels = components.associateWith {
                if ("paypal" in it) "PayPal" else "Google Wallet"
            },
        )

    private fun result(
        captures: List<RuntimeInterfaceSnapshot>,
        selected: Set<ComponentRule> = emptySet(),
        user: Int = 0,
        pending: Boolean = false,
        mode: DisplayMode = DisplayMode.HIDE_SELECTED,
    ) = SystemPreviewV2.resolve(
        SystemProbeRequest(entryKind = kind, userId = user),
        SystemProbeResult(
            0, 0,
            items = listOf(SystemProbeItem("Google Play services",
                "com.google.android.gms", "com.google.android.gms.Hce")),
            nfcPaymentReturns = captures,
        ), now, selected = selected, pendingDraft = pending, globalMode = mode,
    )

    @Test fun registeredGooglePlayServicesIsNotPresentedAsAnObservedPaymentApp() {
        val empty = result(emptyList())
        assertEquals(PreviewV2Source.NFC_CAPTURE_REQUIRED, empty.source)
        assertTrue(empty.items.isEmpty())
        val verified = result(listOf(capture(0, listOf(wallet))))
        assertEquals(PreviewV2Source.NFC_PAYMENT_CAPTURED, verified.source)
        assertEquals(listOf("com.google.android.apps.walletnfcrel"),
            verified.items.map { it.packageName })
        assertFalse(verified.items.any { it.packageName == "com.google.android.gms" })
        assertEquals("Google Wallet", verified.items.single().appLabel)
    }

    @Test fun identicalPaypalPaymentServiceRemainsSeparateAcrossProfiles() {
        val output = result(listOf(capture(0, listOf(paypal)),
            capture(10, listOf(paypal))))
        assertEquals(2, output.items.size)
        assertEquals(listOf(0, 10), output.items.map { it.userId })
        assertEquals(2, output.items.map { it.displayIdentity }.distinct().size)
        assertEquals(listOf("PayPal", "PayPal"), output.items.map { it.appLabel })
    }

    @Test fun personalCleanupNeverSilentlyRemovesWorkProfilePaypal() {
        val component = paypal.substringAfter('/')
        val rule = ComponentRule(kind, paypal.substringBefore('/'), component)
        val output = result(listOf(capture(0, listOf(paypal, wallet)),
            capture(10, listOf(paypal))), selected = setOf(rule))
        assertEquals(PreviewV2Source.POLICY_PROJECTED, output.source)
        assertTrue(output.items.any { it.userId == 10 && it.packageName == rule.packageName })
        assertFalse(output.items.any { it.userId == 0 && it.packageName == rule.packageName })
        assertTrue(output.items.any { it.userId == 0 && it.packageName.contains("wallet") })
    }

    @Test fun savedRulesForWorkUserDoNotHidePersonalService() {
        val rule = ComponentRule(kind, paypal.substringBefore('/'), paypal.substringAfter('/'))
        val output = result(listOf(capture(0, listOf(paypal)),
            capture(10, listOf(paypal))), selected = setOf(rule), user = 10)
        assertEquals(listOf(0), output.items.map { it.userId })
    }

    @Test fun emptyAuthoritativeReturnDoesNotRepopulateFromScannedRegistrations() {
        val empty = result(listOf(capture(0, emptyList())))
        assertEquals(PreviewV2Source.NFC_PAYMENT_CAPTURED, empty.source)
        assertTrue(empty.items.isEmpty())
    }

    @Test fun staleTruncatedOrUntrustedCaptureCannotBePresentedAsActualPaymentResult() {
        val good = capture(0, listOf(paypal))
        for (bad in listOf(
            good.copy(truncated = true),
            good.copy(capturedAtMillis = now - 120_001L),
            good.copy(callerPackage = "com.example.app"),
            good.copy(requestAction = "android.intent.action.VIEW"),
        )) {
            val output = result(listOf(bad))
            assertEquals(PreviewV2Source.NFC_CAPTURE_REQUIRED, output.source)
            assertTrue(output.items.isEmpty())
        }
    }

    @Test fun draftAndShowAllAreDistinguishedFromActiveNfcManagerReturn() {
        val rule = ComponentRule(kind, paypal.substringBefore('/'), paypal.substringAfter('/'))
        val active = listOf(capture(0, listOf(paypal, wallet)))
        assertEquals(PreviewV2Source.DRAFT_SIMULATION,
            result(active, selected = setOf(rule), pending = true).source)
        val shown = result(active, selected = setOf(rule), mode = DisplayMode.SHOW_ALL)
        assertEquals(PreviewV2Source.NFC_PAYMENT_CAPTURED, shown.source)
        assertEquals(2, shown.items.size)
    }
}
