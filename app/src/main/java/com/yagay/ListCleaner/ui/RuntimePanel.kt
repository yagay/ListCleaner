package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R

@Composable
internal fun RuntimePanel(
    state: MainState,
    vm: MainViewModel,
    showUpdateTools: Boolean = true,
    horizontalInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val updating by vm.updating.collectAsStateWithLifecycle()
    val result by vm.updateMessage.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AdaptiveText(stringResource(R.string.runtime_reset_title)) },
            text = { AdaptiveText(stringResource(R.string.runtime_reset_help)) },
            confirmButton = {
                AdaptiveTextButton(onClick = { confirmReset = false; vm.resolveRecovery(false) }) {
                    AdaptiveText(stringResource(R.string.runtime_confirm_reset))
                }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirmReset = false }) { AdaptiveText(stringResource(R.string.common_cancel)) }
            }
        )
    }
    UiCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UiCardVariant.STATUS,
        warning = !state.runtime.ready,
        horizontalInset = horizontalInset
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MaterialUiTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
        ) {
        if (!state.runtime.ready) {
            AdaptiveText(state.runtime.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showUpdateTools) {
                AdaptiveText(stringResource(R.string.runtime_validation_failed_help), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (state.runtime.needsDecision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveButton(onClick = { vm.resolveRecovery(true) }, enabled = !state.runtime.recoveryCorrupt) {
                    AdaptiveText(stringResource(R.string.runtime_restore_remote))
                }
                AdaptiveOutlinedButton(onClick = { confirmReset = true }) {
                    AdaptiveText(stringResource(R.string.runtime_reset_pause))
                }
            }
        }

        AdaptiveText(stringResource(R.string.capability_title), style = MaterialTheme.typography.titleSmall)
        runtimeCapabilities(state.module, state.runtime).forEach { capability ->
            val name = stringResource(
                when (capability.capability) {
                    RuntimeCapability.FILTERING -> R.string.capability_filtering
                    RuntimeCapability.ORDERING -> R.string.capability_ordering
                    RuntimeCapability.PACKAGE_VISIBILITY -> R.string.capability_visibility
                }
            )
            val status = when (capability.state) {
                CapabilityState.OBSERVED -> stringResource(R.string.capability_observed, capability.hits)
                CapabilityState.LOADED_UNOBSERVED -> stringResource(R.string.capability_loaded_unobserved)
                CapabilityState.MISSING_SCOPE -> stringResource(R.string.capability_missing_scope)
                CapabilityState.OUTDATED -> stringResource(R.string.capability_outdated)
                CapabilityState.DISCONNECTED -> stringResource(R.string.capability_disconnected)
                CapabilityState.NOT_READY -> stringResource(R.string.capability_not_ready)
            }
            AdaptiveText(stringResource(R.string.capability_row, name, status), style = MaterialTheme.typography.bodySmall)
        }

        if (showUpdateTools) {
            AdaptiveOutlinedButton(onClick = vm::applyModuleUpdate, enabled = state.module.connected && !updating) {
                AdaptiveText(stringResource(if (updating) R.string.runtime_checking_update else R.string.runtime_apply_update))
            }
        }
        if (showUpdateTools) result?.let { AdaptiveText(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
