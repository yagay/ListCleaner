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
internal fun SettingsListControls() {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    SettingsArea(stringResource(R.string.lc_settings_list_defaults_group)) {
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
    }
    SettingsArea(stringResource(R.string.lc_settings_history_group)) {
        SettingsChoices(
            stringResource(R.string.lc_candidate_source), current.candidateSource,
            listOf(
                UiCandidateSource.ALL to stringResource(R.string.lc_source_all),
                UiCandidateSource.OBSERVED to stringResource(R.string.lc_source_observed),
                UiCandidateSource.PROACTIVE to stringResource(R.string.lc_source_proactive),
                UiCandidateSource.HISTORY to stringResource(R.string.lc_source_history)
            )
        ) { UiPreferencesStore.write(context, current.copy(candidateSource = it)) }
        SettingsSwitchRow(
            title = stringResource(R.string.lc_history_show),
            supportingText = stringResource(R.string.lc_history_help),
            checked = current.showShortcutHistory,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showShortcutHistory = it))
            }
        )
        if (current.showShortcutHistory) {
            SettingsChoices(
                stringResource(R.string.lc_history_age), current.historyVisibilityDays,
                listOf(0 to stringResource(R.string.lc_history_forever),
                    7 to "7", 30 to "30", 90 to "90")
            ) { UiPreferencesStore.write(context, current.copy(historyVisibilityDays = it)) }
        }
    }
    SettingsArea(stringResource(R.string.lc_settings_preview_group)) {
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
    }
    SettingsArea(stringResource(R.string.lc_settings_list_behaviour_group)) {
        // Home categories are always a single list. Preserve the previously
        // saved homeColumns preference for a future optional card presentation.
        SettingsSwitchRow(
            title = stringResource(R.string.lc_show_package_names),
            checked = current.showPackageNames,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showPackageNames = it))
            }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.lc_expand_first_app),
            checked = current.expandFirstApp,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(expandFirstApp = it))
            }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.lc_remember_search),
            checked = current.rememberPageSearch,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(rememberPageSearch = it))
            }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.lc_drag_haptics),
            checked = current.dragHaptics,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(dragHaptics = it))
            }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_remember_filters),
            supportingText = stringResource(R.string.settings_remember_filters_help),
            checked = current.rememberFilters,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(rememberFilters = it))
            }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_help_expanded),
            supportingText = stringResource(R.string.settings_help_expanded_help),
            checked = current.openHelpByDefault,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(openHelpByDefault = it))
            }
        )
        AdaptiveText(stringResource(R.string.settings_defaults_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

