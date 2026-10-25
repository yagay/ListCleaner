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
        SettingsHint(stringResource(R.string.dashboard_sync_status))
        SettingsHint(state.syncStatus)
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
        SettingsHint(stringResource(R.string.dashboard_global_mode_help))
    }
    SettingsArea(stringResource(R.string.dashboard_app_visibility)) {
        SettingsHint(stringResource(R.string.dashboard_app_visibility_help))
        SettingsActionButton(onClick = onAppPicker, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_manage_app_visibility_plain))
        }
        SettingsHint(stringResource(R.string.dashboard_app_visibility_warning), warning = true)
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
        SettingsHint(stringResource(R.string.settings_backup_note))
        SettingsActionButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_export_json))
        }
        SettingsActionButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(R.string.dashboard_restore_json))
        }
    }
    SettingsArea(stringResource(R.string.lc_settings_reset_group)) {
        SettingsHint(stringResource(R.string.settings_reset_description))
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
        SettingsHint(stringResource(R.string.lc_diagnostics_summary_help))
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
            SettingsHint(stringResource(R.string.diagnostic_enabled_help))
        }
    }
    SettingsArea(stringResource(R.string.dashboard_diagnostics)) {
        SettingsHint(stringResource(R.string.dashboard_scan_disclaimer))
        SettingsActionButton(
            onClick = onInspectFile,
            enabled = !checkingFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            AdaptiveText(stringResource(if (checkingFile)
                R.string.dashboard_inspecting_file else R.string.dashboard_inspect_file))
        }
        fileCheckStatus?.let { SettingsHint(it) }
    }
    SettingsArea(stringResource(R.string.lc_settings_technical_group)) {
        SettingsActionButton(onClick = { showTechnical = !showTechnical },
            modifier = Modifier.fillMaxWidth()) {
            AdaptiveText(stringResource(if (showTechnical)
                R.string.lc_settings_hide_technical else R.string.lc_settings_show_technical))
        }
        if (showTechnical) {
            SettingsHint(stringResource(if (state.runtime.ready)
                R.string.dashboard_ack_confirmed else R.string.dashboard_ack_missing))
            SettingsHint(stringResource(R.string.dashboard_query_hits, state.runtime.queryHits))
            SettingsHint(stringResource(R.string.dashboard_visibility_hits, state.runtime.visibilityHits))
            SettingsHint(stringResource(R.string.dashboard_ordering_hits, state.runtime.orderingHits))
            SettingsHint(stringResource(R.string.dashboard_hits_help))
            SettingsActionButton(onClick = vm::refreshModuleStatus,
                modifier = Modifier.fillMaxWidth()) {
                AdaptiveText(stringResource(R.string.dashboard_refresh_runtime))
            }
        }
    }
}
