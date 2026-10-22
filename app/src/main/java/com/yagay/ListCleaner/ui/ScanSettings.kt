package com.yagay.ListCleaner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt


@Composable
internal fun SettingsScanControls(vm: MainViewModel) {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    var showScanDetails by remember { mutableStateOf(false) }
    SettingsArea(stringResource(R.string.lc_root_scan_group)) {
        SettingsChoices(
            stringResource(R.string.settings_scan_mode), current.scanMode,
            listOf(
                UiScanMode.SMART to stringResource(R.string.settings_scan_smart),
                UiScanMode.MANUAL to stringResource(R.string.settings_scan_manual),
                UiScanMode.EVERY_ENTRY to stringResource(R.string.settings_scan_always),
            )
        ) { UiPreferencesStore.write(context, current.copy(scanMode = it)) }
        if (current.scanMode == UiScanMode.SMART) {
            SettingsSlider(
                stringResource(R.string.settings_cache_minutes), current.scanCacheMinutes, 1, 60
            ) { UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(scanCacheMinutes = it)) }
        }
        SettingsSlider(stringResource(R.string.lc_retry_seconds), current.scanRetrySeconds, 10, 180, suffix = "s") {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(scanRetrySeconds = it))
        }
        SettingsChoices(
            stringResource(R.string.lc_root_after_change), current.componentRefresh,
            listOf(
                UiComponentRefresh.CHANGED_ONLY to stringResource(R.string.lc_root_changed_only),
                UiComponentRefresh.FULL to stringResource(R.string.lc_root_full_refresh)
            )
        ) { UiPreferencesStore.write(context, current.copy(componentRefresh = it)) }
        AdaptiveText(stringResource(R.string.lc_root_scan_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(), enabled = !busy,
            onClick = vm::refreshComponents
        ) { AdaptiveText(stringResource(R.string.lc_root_scan_now)) }
    }
    SettingsArea(stringResource(R.string.lc_proactive_scan_title)) {
        SettingsChoices(
            stringResource(R.string.lc_proactive_scan_mode),
            current.proactiveScanMode,
            listOf(
                UiProactiveScanMode.ON_SCAN to stringResource(R.string.lc_proactive_mode_on_scan),
                UiProactiveScanMode.MANUAL_ONLY to stringResource(R.string.lc_proactive_mode_manual),
                UiProactiveScanMode.OFF to stringResource(R.string.lc_proactive_mode_off)
            )
        ) {
            UiPreferencesStore.write(context, current.copy(proactiveScanMode = it))
            vm.refresh()
        }
        if (current.proactiveScanMode != UiProactiveScanMode.OFF) {
            MaterialSettingsRow(
                title = stringResource(R.string.lc_proactive_direct_share),
                supportingText = stringResource(R.string.lc_proactive_direct_share_help)
            ) {
                AdaptiveSwitch(checked = current.proactiveDirectShare, onCheckedChange = {
                    UiPreferencesStore.write(context, current.copy(proactiveDirectShare = it))
                    vm.refresh()
                })
            }
            MaterialSettingsRow(
                title = stringResource(R.string.lc_proactive_shortcuts),
                supportingText = stringResource(R.string.lc_proactive_shortcuts_help)
            ) {
                AdaptiveSwitch(checked = current.proactiveShortcutItems, onCheckedChange = {
                    UiPreferencesStore.write(context, current.copy(proactiveShortcutItems = it))
                    vm.refresh()
                })
            }
            if (current.proactiveScanMode == UiProactiveScanMode.ON_SCAN) {
                SettingsSlider(
                    stringResource(R.string.lc_proactive_cache_minutes),
                    current.proactiveCacheMinutes, 1, 60,
                    suffix = stringResource(R.string.lc_minutes_suffix)
                ) {
                    UiPreferencesRuntime.apply(
                        UiPreferencesRuntime.current.copy(proactiveCacheMinutes = it)
                    )
                }
            }
            AdaptiveOutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.refresh(forceCatalog = true, manualProactive = true) }
            ) { AdaptiveText(stringResource(R.string.lc_proactive_scan_now)) }
        }
        AdaptiveText(
            stringResource(R.string.lc_proactive_scan_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SettingsArea(stringResource(R.string.lc_settings_other_scan_group)) {
        MaterialSettingsRow(title = stringResource(R.string.lc_app_link_enrichment),
            supportingText = stringResource(R.string.lc_app_link_enrichment_help)) {
            AdaptiveSwitch(checked = current.appLinkEnrichment, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(appLinkEnrichment = it))
            })
        }
        AdaptiveText(stringResource(R.string.settings_scan_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(), enabled = !busy,
            onClick = vm::refreshAllCategories
        ) { AdaptiveText(stringResource(R.string.lc_scan_all_now)) }
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showScanDetails = !showScanDetails }
        ) { AdaptiveText(stringResource(R.string.lc_scan_details)) }
        if (showScanDetails) {
            AdaptiveText(
                vm.lastScanDetails(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
