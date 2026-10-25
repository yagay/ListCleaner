package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.R

/** Exactly six nested settings areas. The bottom navigation still has only three destinations. */
internal enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(R.string.settings_appearance_section, R.string.settings_appearance_subtitle, Icons.Rounded.Palette),
    LISTS(R.string.settings_list_section, R.string.settings_list_subtitle, Icons.Rounded.FormatListBulleted),
    SCANNING(R.string.settings_scan_section, R.string.settings_scan_subtitle, Icons.Rounded.Refresh),
    COMPATIBILITY(R.string.settings_compat_section, R.string.settings_compat_subtitle, Icons.Rounded.Security),
    BACKUP(R.string.dashboard_data_backup, R.string.settings_backup_subtitle, Icons.Rounded.Backup),
    ADVANCED(R.string.settings_advanced_section, R.string.settings_advanced_subtitle, Icons.Rounded.BugReport)
}

@Composable
internal fun SettingsCenterHome(onSelect: (SettingsSection) -> Unit) {
    val headings = listOf(
        R.string.lc_settings_home_interface,
        R.string.lc_settings_home_functional,
        R.string.lc_settings_home_maintenance
    )
    MaterialPageColumn(fullBleed = true) {
        SettingsSection.entries.chunked(2).forEachIndexed { groupIndex, sections ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlatSectionHeader(
                    stringResource(headings[groupIndex]),
                    modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal)
                )
                Column(Modifier.fillMaxWidth()) {
                    sections.forEach { section ->
                        FlatCategoryRow(
                            icon = section.icon,
                            title = stringResource(section.titleRes),
                            description = stringResource(section.descriptionRes),
                            onClick = { onSelect(section) }
                        )
                    }
                }
            }
        }
        AdaptiveText(
            stringResource(R.string.app_version_format,
                stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
