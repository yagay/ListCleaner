package com.yagay.ListCleaner.ui

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/**
 * A selection gesture proposes a mutation. Nothing reaches Hook/Root/rule storage
 * until the user confirms. Cancellation drops the pending action unchanged.
 * Filters and navigation are view-only and intentionally remain immediate.
 */
internal class SelectionConfirmationController {
    internal data class Pending(val description: String, val perform: () -> Unit)
    var pending by mutableStateOf<Pending?>(null)
        private set

    fun request(description: String, perform: () -> Unit) {
        pending = Pending(description, perform)
    }

    fun cancel() { pending = null }

    fun confirm() {
        val action = pending ?: return
        pending = null // Prevent duplicate submissions.
        action.perform()
    }
}

@Composable
internal fun rememberSelectionConfirmationController(): SelectionConfirmationController =
    remember { SelectionConfirmationController() }

@Composable
internal fun SelectionConfirmationDialog(controller: SelectionConfirmationController) {
    val pending = controller.pending ?: return
    AdaptiveAlertDialog(
        onDismissRequest = controller::cancel,
        title = { AdaptiveText(stringResource(R.string.lc_selection_confirm_title)) },
        text = {
            AdaptiveText(
                stringResource(R.string.lc_selection_confirm_body, pending.description)
            )
        },
        confirmButton = {
            AdaptiveTextButton(onClick = controller::confirm) {
                AdaptiveText(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = controller::cancel) {
                AdaptiveText(stringResource(R.string.common_cancel))
            }
        }
    )
}
