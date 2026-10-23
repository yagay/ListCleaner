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

    @Test fun ordinaryAppSelectionStagesImmediatelyWithoutPrompt() {
        val controller = SelectionConfirmationController()
        var drafts = 0
        controller.requestForSystemSelection(
            "User app", 0, SystemSelectionRisk.ENTRY
        ) { drafts++ }
        assertEquals(1, drafts)
        assertNull(controller.pending)
    }

    @Test fun selectingSystemAppRequiresConfirmationAndCancelIsSafe() {
        val controller = SelectionConfirmationController()
        var drafts = 0
        controller.requestForSystemSelection(
            "System UI", 1, SystemSelectionRisk.ROOT
        ) { drafts++ }
        assertEquals(0, drafts)
        assertEquals(1, controller.pending?.systemAppCount)
        assertEquals(SystemSelectionRisk.ROOT, controller.pending?.risk)
        controller.cancel()
        assertEquals(0, drafts)
        assertNull(controller.pending)
    }

    @Test fun oneConfirmationAppliesSystemAppDraftOnlyOnce() {
        val controller = SelectionConfirmationController()
        var drafts = 0
        controller.requestForSystemSelection(
            "Settings", 1, SystemSelectionRisk.ENTRY
        ) { drafts++ }
        controller.confirm()
        controller.confirm()
        assertEquals(1, drafts)
    }

    @Test fun bulkSystemSelectionKeepsSystemAppCountInExistingConfirmation() {
        val controller = SelectionConfirmationController()
        var drafts = 0
        controller.request(
            "Invert", systemAppCount = 3, risk = SystemSelectionRisk.PRIORITY
        ) { drafts++ }
        assertEquals(3, controller.pending?.systemAppCount)
        assertEquals(0, drafts)
        controller.confirm()
        assertEquals(1, drafts)
    }
}
