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
    title: String, value: Int, low: Int, high: Int, onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            AdaptiveText(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            AdaptiveText("$value dp", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        AdaptiveText(stringResource(R.string.settings_defaults_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsScanControls(vm: MainViewModel) {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    MaterialSectionCard {
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
        AdaptiveText(stringResource(R.string.settings_scan_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(), enabled = !busy,
            onClick = vm::refreshComponents
        ) { AdaptiveText(stringResource(R.string.settings_scan_now)) }
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
