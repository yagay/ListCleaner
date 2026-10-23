package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode

/** Operational state belongs to compatibility; log collection belongs to diagnostics. */
@Composable
internal fun CompatibilitySettings(
    state: MainState,
    vm: MainViewModel,
    onScope: () -> Unit,
    onAppPicker: () -> Unit,
) {
    val currentMode = state.displayMode
    SettingsArea(stringResource(R.string.lc_settings_runtime_group)) {
        AdaptiveText(stringResource(R.string.dashboard_sync_status),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        AdaptiveText(state.syncStatus, style = MaterialTheme.typography.bodySmall)
        SettingsActionButton(onClick = vm::refreshModuleStatus, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_refresh_runtime))
        }
    }
    RuntimePanel(state, vm)
    SettingsArea(stringResource(R.string.dashboard_global_mode)) {
        SettingsChoices(
            stringResource(R.string.dashboard_global_mode),
            currentMode,
            DisplayMode.entries.map { it to stringResource(it.titleRes()) }
        ) { vm.setDisplayMode(it) }
        AdaptiveText(stringResource(R.string.dashboard_global_mode_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsArea(stringResource(R.string.dashboard_app_visibility)) {
        AdaptiveText(stringResource(R.string.dashboard_app_visibility_help),
            style = MaterialTheme.typography.bodySmall)
        SettingsActionButton(onClick = onAppPicker, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_manage_app_visibility_plain))
        }
        AdaptiveText(stringResource(R.string.dashboard_app_visibility_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsArea(stringResource(R.string.dashboard_module_status)) {
        ModuleStatusRow(state, onClick = { onScope() })
    }
}

@Composable
internal fun BackupSettings(
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onColorModeChange: (UiColorMode) -> Unit,
) {
    SettingsArea(stringResource(R.string.dashboard_data_backup)) {
        AdaptiveText(stringResource(R.string.settings_backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsActionButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_export_json))
        }
        SettingsActionButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_restore_json))
        }
    }
    SettingsArea(stringResource(R.string.lc_settings_reset_group)) {
        AdaptiveText(stringResource(R.string.settings_reset_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsResetControls(onColorModeChange)
    }
}

@Composable
internal fun DiagnosticsSettings(
    state: MainState,
    vm: MainViewModel,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit,
) {
    val context = LocalContext.current
    val fileCheckStatus by vm.fileCheckStatus.collectAsStateWithLifecycle()
    val checkingFile by vm.checkingFile.collectAsStateWithLifecycle()
    var showTechnical by rememberSaveable { mutableStateOf(false) }

    SettingsArea(stringResource(R.string.lc_diagnostics_export)) {
        SettingsSwitchRow(
            title = stringResource(R.string.lc_diagnostics_full),
            supportingText = stringResource(R.string.lc_diagnostics_full_help),
            checked = UiPreferencesRuntime.current.diagnosticsLevel == UiDiagnosticsLevel.FULL,
            onCheckedChange = { full ->
                    UiPreferencesStore.write(context, UiPreferencesRuntime.current.copy(
                        diagnosticsLevel = if (full) UiDiagnosticsLevel.FULL
                            else UiDiagnosticsLevel.SUMMARY
                    ))
                }
        )
        AdaptiveText(stringResource(R.string.lc_diagnostics_summary_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsActionButton(style = SettingsActionStyle.PRIMARY, 
            onClick = onCollectDiagnostics,
            enabled = !collectingDiagnostics,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (collectingDiagnostics) {
                AdaptiveCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            AdaptiveText(stringResource(if (collectingDiagnostics)
                R.string.dashboard_collecting else R.string.dashboard_export_diagnostics))
        }
    }
    SettingsArea(stringResource(R.string.diagnostic_mode)) {
        SettingsSwitchRow(
            title = stringResource(R.string.diagnostic_mode),
            supportingText = stringResource(R.string.diagnostic_mode_help),
            checked = state.diagnosticMode,
            onCheckedChange = vm::setDiagnosticMode
        )
        if (state.diagnosticMode) {
            AdaptiveText(stringResource(R.string.diagnostic_enabled_help),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    SettingsArea(stringResource(R.string.dashboard_diagnostics)) {
        AdaptiveText(stringResource(R.string.dashboard_scan_disclaimer),
            style = MaterialTheme.typography.bodySmall)
        SettingsActionButton(
            onClick = onInspectFile,
            enabled = !checkingFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            AdaptiveText(stringResource(if (checkingFile)
                R.string.dashboard_inspecting_file else R.string.dashboard_inspect_file))
        }
        fileCheckStatus?.let { AdaptiveText(it, style = MaterialTheme.typography.bodySmall) }
    }
    SettingsArea(stringResource(R.string.lc_settings_technical_group)) {
        SettingsActionButton(onClick = { showTechnical = !showTechnical },
            modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(if (showTechnical)
                R.string.lc_settings_hide_technical else R.string.lc_settings_show_technical))
        }
        if (showTechnical) {
            AdaptiveText(stringResource(if (state.runtime.ready)
                R.string.dashboard_ack_confirmed else R.string.dashboard_ack_missing),
                fontWeight = FontWeight.Medium)
            AdaptiveText(stringResource(R.string.dashboard_query_hits, state.runtime.queryHits),
                style = MaterialTheme.typography.bodySmall)
            AdaptiveText(stringResource(R.string.dashboard_visibility_hits, state.runtime.visibilityHits),
                style = MaterialTheme.typography.bodySmall)
            AdaptiveText(stringResource(R.string.dashboard_ordering_hits, state.runtime.orderingHits),
                style = MaterialTheme.typography.bodySmall)
            AdaptiveText(stringResource(R.string.dashboard_hits_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsActionButton(onClick = vm::refreshModuleStatus,
                modifier = Modifier.fillMaxWidth()) {
                AdaptiveText(stringResource(R.string.dashboard_refresh_runtime))
            }
        }
    }
}
