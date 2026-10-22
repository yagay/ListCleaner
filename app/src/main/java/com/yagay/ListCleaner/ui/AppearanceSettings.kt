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
internal fun SettingsAppearanceControls() {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    SettingsArea(stringResource(R.string.lc_settings_theme_group)) {
        SettingsChoices(
            stringResource(R.string.lc_home_layout_title), current.homeLayout,
            listOf(
                UiHomeLayout.CATEGORY_CARDS to stringResource(R.string.lc_home_layout_cards),
                UiHomeLayout.CLASSIC_LIST to stringResource(R.string.lc_home_layout_classic)
            )
        ) { UiPreferencesStore.write(context, current.copy(homeLayout = it)) }
        AdaptiveText(
            stringResource(R.string.lc_home_layout_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    }
    SettingsArea(stringResource(R.string.lc_settings_layout_group)) {
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
    }
    SettingsArea(stringResource(R.string.lc_settings_typography_group)) {
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
internal fun SettingsLanguageControls() {
    val context = LocalContext.current
    SettingsArea(stringResource(R.string.settings_language)) {
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

