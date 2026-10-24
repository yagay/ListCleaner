package com.yagay.ListCleaner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
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
                UiAccent.PURPLE to stringResource(R.string.settings_color_purple),
                UiAccent.CUSTOM to stringResource(R.string.settings_color_custom)
            )
        ) { UiPreferencesStore.write(context, current.copy(accent = it)) }
        if (current.accent == UiAccent.CUSTOM) {
            var customHex by remember(current.customAccentHex) {
                mutableStateOf(current.customAccentHex)
            }
            androidx.compose.material3.OutlinedTextField(
                value = customHex,
                onValueChange = { value ->
                    if (value.length <= 7) customHex = value
                },
                label = { AdaptiveText(stringResource(R.string.settings_custom_color_hex)) },
                supportingText = { AdaptiveText(stringResource(R.string.settings_custom_color_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AdaptiveButton(
                onClick = {
                    UiPreferencesStore.write(
                        context,
                        UiPreferencesRuntime.current.copy(customAccentHex = customHex)
                    )
                },
                enabled = customHex.matches(Regex("#[0-9a-fA-F]{6}"))
            ) {
                AdaptiveText(stringResource(R.string.settings_custom_color_apply))
            }
        }
        SettingsChoices(
            stringResource(R.string.settings_density), current.density,
            listOf(
                UiDensity.COMPACT to stringResource(R.string.settings_density_compact),
                UiDensity.STANDARD to stringResource(R.string.settings_density_standard),
                UiDensity.COMFORTABLE to stringResource(R.string.settings_density_comfortable)
            )
        ) { UiPreferencesStore.write(context, current.copy(density = it)) }
    }
    SettingsArea(stringResource(R.string.settings_color_details)) {
            ThemeColorRoleEditor(
                role = "buttonBackground",
                title = stringResource(R.string.color_role_buttonBackground)
            )
            ThemeColorRoleEditor(
                role = "buttonText",
                title = stringResource(R.string.color_role_buttonText)
            )
            ThemeColorRoleEditor(
                role = "buttonOutline",
                title = stringResource(R.string.color_role_buttonOutline)
            )
            ThemeColorRoleEditor(
                role = "selectedBackground",
                title = stringResource(R.string.color_role_selectedBackground)
            )
            ThemeColorRoleEditor(
                role = "selectedText",
                title = stringResource(R.string.color_role_selectedText)
            )
            ThemeColorRoleEditor(
                role = "categoryBackground",
                title = stringResource(R.string.color_role_categoryBackground)
            )
            ThemeColorRoleEditor(
                role = "filterBackground",
                title = stringResource(R.string.color_role_filterBackground)
            )
            ThemeColorRoleEditor(
                role = "navigationBackground",
                title = stringResource(R.string.color_role_navigationBackground)
            )
            ThemeColorRoleEditor(
                role = "navigationText",
                title = stringResource(R.string.color_role_navigationText)
            )
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
                Icons.Rounded.ChevronRight,
                stringResource(R.string.settings_open_system),
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
private fun ThemeColorRoleEditor(role: String, title: String) {
    val context = LocalContext.current
    val preferences = UiPreferencesRuntime.current
    val currentHex = preferences.colorOverrides[role].orEmpty()
    var typedHex by remember(role, currentHex) { mutableStateOf(currentHex) }
    val valid = typedHex.matches(Regex("#[0-9a-fA-F]{6}"))
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdaptiveText(title, style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = typedHex,
                onValueChange = { if (it.length <= 7) typedHex = it },
                singleLine = true,
                placeholder = { AdaptiveText("#RRGGBB") },
                modifier = Modifier.weight(1f)
            )
            AdaptiveTextButton(
                onClick = {
                    UiPreferencesStore.write(context, UiPreferencesRuntime.current.copy(
                        colorOverrides = UiPreferencesRuntime.current.colorOverrides - role
                    ))
                },
                enabled = currentHex.isNotBlank()
            ) { AdaptiveText(stringResource(R.string.settings_color_reset)) }
            AdaptiveTextButton(
                onClick = {
                    UiPreferencesStore.write(context, UiPreferencesRuntime.current.copy(
                        colorOverrides = UiPreferencesRuntime.current.colorOverrides + (role to typedHex)
                    ))
                },
                enabled = valid && typedHex != currentHex
            ) { AdaptiveText(stringResource(R.string.settings_custom_color_apply)) }
        }
    }
}
