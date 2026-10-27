package com.yagay.ListCleaner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRootNavigationTest {
    @Test fun successfulVerifiedRootBatchCanLeave() {
        val nav = PendingNavigationGuard()
        var left = false
        nav.register(Any(), 1, { true }, {}, rootEditor = true,
            rootVerification = { true })
        nav.request { left = true }
        nav.applyAndLeave()
        nav.observeRootBusy(true)
        nav.observeRootBusy(false)
        assertTrue(left)
    }

    @Test fun partialRootFailureKeepsEditorOpen() {
        val nav = PendingNavigationGuard()
        var left = false
        nav.register(Any(), 2, { true }, {}, rootEditor = true,
            rootVerification = { false })
        nav.request { left = true }
        nav.applyAndLeave()
        nav.observeRootBusy(true)
        nav.observeRootBusy(false)
        assertFalse(left)
    }

    @Test fun noNavigationBeforeRootFinishes() {
        val nav = PendingNavigationGuard()
        var left = false
        nav.register(Any(), 1, { true }, {}, rootEditor = true,
            rootVerification = { true })
        nav.request { left = true }
        nav.applyAndLeave()
        nav.observeRootBusy(true)
        assertFalse(left)
    }
}
