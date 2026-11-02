package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleEligibilityComparisonTest {
    private fun observed(
        kind: IntentKind = IntentKind.BROWSER,
        source: String,
        user: Int = 0,
        millis: Long = 120_000L,
        packages: List<String> = listOf("com.example.browser", "com.example.other"),
        truncated: Boolean = false,
    ) = RuntimeInterfaceSnapshot(
        kind = kind.name,
        source = source,
        callerUserId = user,
        callerPackage = "com.google.android.permissioncontroller",
        callerUid = 1000,
        requestAction = "android.app.role." + kind.name,
        capturedAtMillis = millis,
        packages = packages,
        totalPackages = packages.size,
        truncated = truncated,
    )

    @Test fun pairedSnapshotsShowOnlyActuallyRemovedPackages() {
        val before = observed(source = "ROLE_CONTROLLER_ORIGINAL")
        val after = observed(
            source = "ROLE_CONTROLLER_RETURN",
            millis = 120_040L,
            packages = listOf("com.example.browser")
        )
        val compare = compareRoleEligibility(IntentKind.BROWSER, 0, before, after)
        assertTrue(compare.sameQuery)
        assertEquals(listOf("com.example.other"), compare.removed)
        assertEquals(listOf("com.example.browser"), compare.after)
    }

    @Test fun mismatchedProfileAndRoleCannotBeShownAsLive() {
        val original = observed(source = "ROLE_CONTROLLER_ORIGINAL", user = 10)
        val filtered = observed(source = "ROLE_CONTROLLER_RETURN")
        val compare = compareRoleEligibility(IntentKind.BROWSER, 0, original, filtered)
        assertNull(compare.original)
        assertFalse(compare.sameQuery)
        assertTrue(compare.removed.isEmpty())
    }

    @Test fun differentInvocationCannotBeDiffed() {
        val before = observed(source = "ROLE_CONTROLLER_ORIGINAL", millis = 100_000L)
        val after = observed(source = "ROLE_CONTROLLER_RETURN", millis = 200_000L)
        val compare = compareRoleEligibility(IntentKind.BROWSER, 0, before, after)
        assertFalse(compare.sameQuery)
        assertTrue(compare.removed.isEmpty())
    }

    @Test fun truncatedObservationMustNotClaimACompleteList() {
        val before = observed(source = "ROLE_CONTROLLER_ORIGINAL", truncated = true)
        val after = observed(source = "ROLE_CONTROLLER_RETURN")
        val compare = compareRoleEligibility(IntentKind.BROWSER, 0, before, after)
        assertNull(compare.original)
        assertFalse(compare.sameQuery)
    }

    @Test fun snapshotKeysRemainIsolatedByRoleAndUser() {
        assertFalse(
            RuntimeInterfaceSnapshotCodec.keyForRoleOriginal(IntentKind.BROWSER, 0) ==
                RuntimeInterfaceSnapshotCodec.keyForRoleOriginal(IntentKind.BROWSER, 10)
        )
        assertFalse(
            RuntimeInterfaceSnapshotCodec.keyForRoleFiltered(IntentKind.BROWSER, 0) ==
                RuntimeInterfaceSnapshotCodec.keyForRoleFiltered(IntentKind.HOME, 0)
        )
        assertFalse(
            RuntimeInterfaceSnapshotCodec.keyForRoleOriginal(IntentKind.BROWSER, 0) ==
                RuntimeInterfaceSnapshotCodec.keyForRoleFiltered(IntentKind.BROWSER, 0)
        )
    }
}
