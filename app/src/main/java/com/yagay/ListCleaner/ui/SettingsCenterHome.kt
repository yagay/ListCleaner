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
    MaterialPageColumn {
        SettingsSection.entries.forEachIndexed { index, section ->
            val position = when {
                SettingsSection.entries.size == 1 -> ListRowPosition.SINGLE
                index == 0 -> ListRowPosition.FIRST
                index == SettingsSection.entries.lastIndex -> ListRowPosition.LAST
                else -> ListRowPosition.MIDDLE
            }
            UiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = UiCardVariant.LIST_GROUP,
                position = position,
                horizontalInset = 0.dp,
                onClick = { onSelect(section) }
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .padding(horizontal = MaterialUiTokens.cardPadding, vertical = MaterialUiTokens.contentGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
                ) {
                    AdaptiveIcon(section.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AdaptiveText(
                            stringResource(section.titleRes),
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        AdaptiveText(
                            stringResource(section.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AdaptiveIcon(
                        Icons.Rounded.ChevronRight, null,
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AdaptiveText(
            stringResource(R.string.app_version_format, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
