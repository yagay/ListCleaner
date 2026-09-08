package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

@Composable
internal fun RuntimePanel(state: MainState, vm: MainViewModel, showUpdateTools: Boolean = true) {
    val updating by vm.updating.collectAsState()
    val result by vm.updateMessage.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.runtime_reset_title)) },
            text = { Text(stringResource(R.string.runtime_reset_help)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resolveRecovery(false) }) {
                    Text(stringResource(R.string.runtime_confirm_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.runtime.ready) {
            Text(state.runtime.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showUpdateTools) {
                Text(stringResource(R.string.runtime_validation_failed_help), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (state.runtime.needsDecision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.resolveRecovery(true) }, enabled = !state.runtime.recoveryCorrupt) {
                    Text(stringResource(R.string.runtime_restore_remote))
                }
                OutlinedButton(onClick = { confirmReset = true }) {
                    Text(stringResource(R.string.runtime_reset_pause))
                }
            }
        }
        if (showUpdateTools) {
            OutlinedButton(onClick = vm::applyModuleUpdate, enabled = state.module.connected && !updating) {
                Text(stringResource(if (updating) R.string.runtime_checking_update else R.string.runtime_apply_update))
            }
        }
        if (showUpdateTools) result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
