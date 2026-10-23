package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionConfirmationControllerTest {
    @Test fun requestingSelectionNeverAppliesChangeBeforeConfirmation() {
        val controller = SelectionConfirmationController()
        var changes = 0
        controller.request("Enable component") { changes++ }
        assertEquals(0, changes)
        assertEquals("Enable component", controller.pending?.description)
        controller.confirm()
        assertEquals(1, changes)
        assertNull(controller.pending)
    }

    @Test fun cancellationDiscardsPendingModification() {
        val controller = SelectionConfirmationController()
        var changes = 0
        controller.request("Deselect rule") { changes++ }
        controller.cancel()
        controller.confirm()
        assertEquals(0, changes)
        assertNull(controller.pending)
    }

    @Test fun doubleConfirmationDoesNotRepeatMutation() {
        val controller = SelectionConfirmationController()
        var changes = 0
        controller.request("Pin priority") { changes++ }
        controller.confirm()
        controller.confirm()
        assertEquals(1, changes)
    }

    @Test fun newestPendingRequestReplacesPreviousIntent() {
        val controller = SelectionConfirmationController()
        var result = ""
        controller.request("First") { result = "first" }
        controller.request("Second") { result = "second" }
        controller.confirm()
        assertEquals("second", result)
    }
}
