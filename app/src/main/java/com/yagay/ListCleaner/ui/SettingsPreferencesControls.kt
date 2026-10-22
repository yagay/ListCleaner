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

/** Settings screens share the same Material cards, chips and geometry as every other page. */
@Composable
private fun <T> SettingsChoices(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)) {
        AdaptiveText(title, fontWeight = FontWeight.Medium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
        ) {
            options.forEach { (item, label) ->
                AdaptiveFilterChip(selected = item == value, onClick = { onSelected(item) },
                    label = { AdaptiveText(label) })
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    title: String, value: Int, low: Int, high: Int, suffix: String = "dp", onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            AdaptiveText(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            AdaptiveText("$value $suffix", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = {
                // Commit once on gesture completion; UI preview stays live during the drag.
                UiPreferencesStore.write(context, UiPreferencesRuntime.current)
            },
            valueRange = low.toFloat()..high.toFloat()
        )
    }
}

@Composable
internal fun SettingsAppearanceControls() {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    MaterialSectionCard {
        SettingsChoices(
            stringResource(R.string.settings_theme_color), current.accent,
            listOf(
                UiAccent.DYNAMIC to stringResource(R.string.settings_color_dynamic),
                UiAccent.BLUE to stringResource(R.string.settings_color_blue),
                UiAccent.GREEN to stringResource(R.string.settings_color_green),
                UiAccent.PURPLE to stringResource(R.string.settings_color_purple)
            )
        ) { UiPreferencesStore.write(context, current.copy(accent = it)) }
        SettingsChoices(
            stringResource(R.string.settings_density), current.density,
            listOf(
                UiDensity.COMPACT to stringResource(R.string.settings_density_compact),
                UiDensity.STANDARD to stringResource(R.string.settings_density_standard),
                UiDensity.COMFORTABLE to stringResource(R.string.settings_density_comfortable)
            )
        ) { UiPreferencesStore.write(context, current.copy(density = it)) }
        SettingsSlider(stringResource(R.string.settings_card_radius), current.cardRadius, 0, 32) {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(cardRadius = it))
        }
        SettingsSlider(stringResource(R.string.settings_button_height), current.buttonHeight, 36, 64) {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(buttonHeight = it))
        }
        SettingsSlider(stringResource(R.string.settings_row_height), current.listRowHeight, 48, 96) {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(listRowHeight = it))
        }
        SettingsSlider(stringResource(R.string.settings_icon_size), current.appIconSize, 28, 56) {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(appIconSize = it))
        }
        SettingsSlider(stringResource(R.string.lc_font_scale), current.fontScalePercent, 80, 140, suffix = "%") {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(fontScalePercent = it))
        }
        SettingsSlider(stringResource(R.string.lc_layout_spacing), current.layoutSpacingPercent, 70, 150, suffix = "%") {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(layoutSpacingPercent = it))
        }
        SettingsChoices(stringResource(R.string.lc_title_lines), current.appTitleLines,
            listOf(1 to "1", 2 to "2", 3 to "3")) {
            UiPreferencesStore.write(context, current.copy(appTitleLines = it))
        }
        SettingsChoices(stringResource(R.string.lc_subtitle_lines), current.appSubtitleLines,
            listOf(1 to "1", 2 to "2", 3 to "3")) {
            UiPreferencesStore.write(context, current.copy(appSubtitleLines = it))
        }
        MaterialSettingsRow(title = stringResource(R.string.lc_show_app_icons)) {
            AdaptiveSwitch(checked = current.showAppIcons, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showAppIcons = it))
            })
        }
        AdaptiveText(stringResource(R.string.settings_appearance_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsListControls() {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    MaterialSectionCard {
        SettingsChoices(
            stringResource(R.string.settings_start_page), current.startPage,
            listOf(
                UiStartPage.CLEANUP to stringResource(R.string.nav_rules),
                UiStartPage.ORDERING to stringResource(R.string.nav_priority),
                UiStartPage.SETTINGS to stringResource(R.string.nav_settings),
            )
        ) { UiPreferencesStore.write(context, current.copy(startPage = it)) }
        SettingsChoices(
            stringResource(R.string.settings_sort), current.appSort,
            listOf(
                UiAppSort.SELECTED_FIRST to stringResource(R.string.settings_sort_selected),
                UiAppSort.NAME to stringResource(R.string.settings_sort_name),
                UiAppSort.PACKAGE to stringResource(R.string.settings_sort_package)
            )
        ) { UiPreferencesStore.write(context, current.copy(appSort = it)) }
        SettingsChoices(
            stringResource(R.string.settings_default_filter), current.defaultFilter,
            listOf(
                UiFilter.ALL to stringResource(R.string.settings_filter_all),
                UiFilter.HIDE_SELECTED to stringResource(R.string.settings_filter_unselected),
                UiFilter.SHOW_SELECTED to stringResource(R.string.settings_filter_selected),
                UiFilter.LOCKED to stringResource(R.string.settings_filter_locked)
            )
        ) { UiPreferencesStore.write(context, current.copy(defaultFilter = it)) }
        SettingsChoices(
            stringResource(R.string.settings_app_scope), current.defaultAppType,
            listOf(
                AppTypeFilter.ALL to stringResource(R.string.settings_filter_all),
                AppTypeFilter.USER to stringResource(R.string.settings_app_user),
                AppTypeFilter.SYSTEM to stringResource(R.string.settings_app_system)
            )
        ) { UiPreferencesStore.write(context, current.copy(defaultAppType = it)) }
        SettingsChoices(
            stringResource(R.string.lc_candidate_source), current.candidateSource,
            listOf(
                UiCandidateSource.ALL to stringResource(R.string.lc_source_all),
                UiCandidateSource.OBSERVED to stringResource(R.string.lc_source_observed),
                UiCandidateSource.PROACTIVE to stringResource(R.string.lc_source_proactive),
                UiCandidateSource.HISTORY to stringResource(R.string.lc_source_history)
            )
        ) { UiPreferencesStore.write(context, current.copy(candidateSource = it)) }
        MaterialSettingsRow(
            title = stringResource(R.string.lc_history_show),
            supportingText = stringResource(R.string.lc_history_help)
        ) {
            AdaptiveSwitch(checked = current.showShortcutHistory, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showShortcutHistory = it))
            })
        }
        if (current.showShortcutHistory) {
            SettingsChoices(
                stringResource(R.string.lc_history_age), current.historyVisibilityDays,
                listOf(0 to stringResource(R.string.lc_history_forever),
                    7 to "7", 30 to "30", 90 to "90")
            ) { UiPreferencesStore.write(context, current.copy(historyVisibilityDays = it)) }
        }
        SettingsChoices(
            stringResource(R.string.lc_component_start), current.componentStart,
            listOf(
                UiComponentStart.CATEGORIES to stringResource(R.string.lc_component_categories),
                UiComponentStart.ALL_COMPONENTS to stringResource(R.string.lc_component_all)
            )
        ) { UiPreferencesStore.write(context, current.copy(componentStart = it)) }
        SettingsChoices(
            stringResource(R.string.lc_preview_default), current.previewDefault,
            listOf(
                UiPreviewDefault.CURRENT to stringResource(R.string.lc_preview_current),
                UiPreviewDefault.CANDIDATES to stringResource(R.string.lc_preview_candidates),
                UiPreviewDefault.ENABLED to stringResource(R.string.lc_preview_enabled),
                UiPreviewDefault.HISTORY to stringResource(R.string.lc_preview_history)
            )
        ) { UiPreferencesStore.write(context, current.copy(previewDefault = it)) }
        SettingsChoices(
            stringResource(R.string.lc_preview_scenario), current.probeDefaultScenario,
            listOf(
                UiProbeDefaultScenario.SYSTEM to stringResource(R.string.lc_scenario_system),
                UiProbeDefaultScenario.TEXT to stringResource(R.string.lc_scenario_text),
                UiProbeDefaultScenario.IMAGE to stringResource(R.string.lc_scenario_image),
                UiProbeDefaultScenario.PDF to "PDF",
                UiProbeDefaultScenario.LAST_USED to stringResource(R.string.lc_scenario_last)
            )
        ) { UiPreferencesStore.write(context, current.copy(probeDefaultScenario = it)) }
        SettingsChoices(
            stringResource(R.string.settings_home_columns), current.homeColumns,
            listOf(
                1 to stringResource(R.string.settings_one_column),
                2 to stringResource(R.string.settings_two_columns)
            )
        ) { UiPreferencesStore.write(context, current.copy(homeColumns = it)) }
        MaterialSettingsRow(title = stringResource(R.string.lc_show_package_names)) {
            AdaptiveSwitch(checked = current.showPackageNames, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showPackageNames = it))
            })
        }
        MaterialSettingsRow(title = stringResource(R.string.lc_expand_first_app)) {
            AdaptiveSwitch(checked = current.expandFirstApp, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(expandFirstApp = it))
            })
        }
        MaterialSettingsRow(title = stringResource(R.string.lc_remember_search)) {
            AdaptiveSwitch(checked = current.rememberPageSearch, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(rememberPageSearch = it))
            })
        }
        MaterialSettingsRow(title = stringResource(R.string.lc_drag_haptics)) {
            AdaptiveSwitch(checked = current.dragHaptics, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(dragHaptics = it))
            })
        }
        MaterialSettingsRow(
            title = stringResource(R.string.settings_remember_filters),
            supportingText = stringResource(R.string.settings_remember_filters_help)
        ) {
            AdaptiveSwitch(checked = current.rememberFilters, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(rememberFilters = it))
            })
        }
        MaterialSettingsRow(
            title = stringResource(R.string.settings_help_expanded),
            supportingText = stringResource(R.string.settings_help_expanded_help)
        ) {
            AdaptiveSwitch(checked = current.openHelpByDefault, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(openHelpByDefault = it))
            })
        }
        AdaptiveText(stringResource(R.string.settings_defaults_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsScanControls(vm: MainViewModel) {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    var showScanDetails by remember { mutableStateOf(false) }
    MaterialSectionCard {
        AdaptiveText(
            stringResource(R.string.lc_root_scan_group),
            fontWeight = FontWeight.Medium
        )
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
        MaterialSettingsRow(title = stringResource(R.string.lc_app_link_enrichment),
            supportingText = stringResource(R.string.lc_app_link_enrichment_help)) {
            AdaptiveSwitch(checked = current.appLinkEnrichment, onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(appLinkEnrichment = it))
            })
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
        AdaptiveText(
            stringResource(R.string.lc_proactive_scan_title),
            fontWeight = FontWeight.Medium
        )
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
        AdaptiveText(stringResource(R.string.settings_scan_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(), enabled = !busy,
            onClick = vm::refreshComponents
        ) { AdaptiveText(stringResource(R.string.lc_root_scan_now)) }
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

@Composable
internal fun SettingsLanguageControls() {
    val context = LocalContext.current
    MaterialSectionCard {
        MaterialSettingsRow(
            title = stringResource(R.string.settings_language),
            supportingText = stringResource(R.string.settings_language_help)
        ) {
            AdaptiveOutlinedButton(onClick = {
                val action = if (Build.VERSION.SDK_INT >= 33) Settings.ACTION_APP_LOCALE_SETTINGS
                    else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val intent = Intent(action, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
            }) { AdaptiveText(stringResource(R.string.settings_open_system)) }
        }
    }
}

@Composable
internal fun SettingsResetControls(onColorModeChange: (UiColorMode) -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirm = false },
            title = { AdaptiveText(stringResource(R.string.settings_reset_title)) },
            text = { AdaptiveText(stringResource(R.string.settings_reset_description)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirm = false
                    UiPreferencesStore.reset(context)
                    onColorModeChange(UiColorMode.SYSTEM)
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirm = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    AdaptiveOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { confirm = true }) {
        AdaptiveText(stringResource(R.string.settings_reset_title))
    }
}
