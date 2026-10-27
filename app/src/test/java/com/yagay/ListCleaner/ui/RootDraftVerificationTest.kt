package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootDraftVerificationTest {
    @Test fun onlyConfirmedComponentsAreAcknowledged() {
        val edits = mapOf("u0|one" to true, "u0|two" to false, "u0|three" to true)
        val actual = mapOf<String, Boolean?>(
            "u0|one" to true,
            "u0|two" to true, // partial failure: user requested enabled
            "u0|three" to null // transient scan failure
        )
        assertEquals(setOf("u0|one"), verifiedComponentDraftKeys(edits, actual))
    }

    @Test fun unknownAndMissingStatesDoNotCountAsSuccess() {
        assertEquals(emptySet<String>(), verifiedComponentDraftKeys(
            mapOf("unknown" to false, "missing" to true),
            mapOf("unknown" to null)
        ))
    }

    @Test fun successfullyEnabledComponentsAreAcknowledged() {
        assertEquals(setOf("enabled"), verifiedComponentDraftKeys(
            mapOf("enabled" to false), mapOf("enabled" to false)
        ))
    }
}
