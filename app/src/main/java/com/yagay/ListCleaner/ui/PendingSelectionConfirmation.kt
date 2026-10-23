package com.yagay.ListCleaner.ui

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/**
 * A selection gesture proposes a mutation. Nothing reaches Hook/Root/rule storage
 * until the user confirms. Cancellation drops the pending action unchanged.
 * Filters and navigation are view-only and intentionally remain immediate.
 */
/** The risks differ: Root components can be disabled, Entry cleanup hides resolver targets. */
internal enum class SystemSelectionRisk(val descriptionRes: Int) {
    ENTRY(R.string.lc_system_app_warning_entry),
    ROOT(R.string.lc_system_app_warning_root),
    PRIORITY(R.string.lc_system_app_warning_priority)
}

internal class SelectionConfirmationController {
    internal data class Pending(
        val description: String,
        val perform: () -> Unit,
        val systemAppCount: Int = 0,
        val risk: SystemSelectionRisk? = null
    )
    var pending by mutableStateOf<Pending?>(null)
        private set

    fun request(
        description: String,
        systemAppCount: Int = 0,
        risk: SystemSelectionRisk? = null,
        perform: () -> Unit,
    ) {
        pending = Pending(description, perform, systemAppCount.coerceAtLeast(0), risk)
    }

    /** Ordinary app selection remains immediate (draft-only). System apps require consent. */
    fun requestForSystemSelection(
        description: String,
        systemAppCount: Int,
        risk: SystemSelectionRisk,
        perform: () -> Unit,
    ) {
        if (systemAppCount > 0) {
            request(description, systemAppCount, risk, perform)
        } else {
            perform()
        }
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
            val ordinary = stringResource(R.string.lc_selection_confirm_body, pending.description)
            val warning = pending.risk?.takeIf { pending.systemAppCount > 0 }?.let { risk ->
                "\n\n" + stringResource(R.string.lc_system_app_warning_count, pending.systemAppCount) +
                    "\n" + stringResource(risk.descriptionRes)
            }.orEmpty()
            AdaptiveText(ordinary + warning)
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
