package com.yagay.ListCleaner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUserAvailabilityTest {
    @Test fun unlockedUserCanBeScanned() {
        assertEquals(ProfileUnlockState.UNLOCKED,
            ProfileUserAvailability.parse("RUNNING_UNLOCKED"))
    }

    @Test fun runningButLockedProfileMustNotOverwriteCache() {
        assertEquals(ProfileUnlockState.LOCKED,
            ProfileUserAvailability.parse("RUNNING_LOCKED"))
        assertEquals(ProfileUnlockState.LOCKED,
            ProfileUserAvailability.parse("RUNNING_UNLOCKING"))
        assertEquals(ProfileUnlockState.LOCKED,
            ProfileUserAvailability.parse("SHUTDOWN"))
    }

    @Test fun unsupportedOrUnexpectedUserStateIsUnknownRatherThanLocked() {
        assertEquals(ProfileUnlockState.UNKNOWN,
            ProfileUserAvailability.parse("Error: unknown command"))
    }
}
