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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt


@Composable
internal fun SettingsAppearanceControls(
    colorMode: UiColorMode,
    onColorModeChange: (UiColorMode) -> Unit,
) {
    val context = LocalContext.current
    val current = UiPreferencesRuntime.current
    SettingsArea(stringResource(R.string.lc_settings_theme_group)) {
        SettingsChoices(
            title = stringResource(R.string.ui_color_mode_title),
            value = colorMode,
            options = UiColorMode.entries.map { mode ->
                mode to stringResource(when (mode) {
                    UiColorMode.SYSTEM -> R.string.ui_color_mode_system
                    UiColorMode.LIGHT -> R.string.ui_color_mode_light
                    UiColorMode.DARK -> R.string.ui_color_mode_dark
                })
            },
            onSelected = onColorModeChange
        )
        AdaptiveText(
            stringResource(R.string.ui_color_mode_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        SettingsSlider(stringResource(R.string.settings_icon_size), current.appIconSize, 28, 56) {
            UiPreferencesRuntime.apply(UiPreferencesRuntime.current.copy(appIconSize = it))
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
        SettingsSwitchRow(
            title = stringResource(R.string.lc_show_app_icons),
            checked = current.showAppIcons,
            onCheckedChange = {
                UiPreferencesStore.write(context, current.copy(showAppIcons = it))
            }
        )
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
            supportingText = stringResource(R.string.settings_language_help),
            onClick = {
                val action = if (Build.VERSION.SDK_INT >= 33) Settings.ACTION_APP_LOCALE_SETTINGS
                    else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val intent = Intent(action, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
            }
        ) {
            AdaptiveIcon(
                androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                stringResource(R.string.settings_open_system),
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

