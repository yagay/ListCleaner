package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yagay.ListCleaner.R

/** Shared interaction contract for rule, priority and Root-component app rows.
 * Selection, expand, and lock remain separate actions on a shared Material 3 row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceAppRow(
    icon: Bitmap?,
    title: String,
    subtitle: String,
    expanded: Boolean,
    lockState: BulkLockState,
    onExpand: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    selection: @Composable () -> Unit,
    packageName: String? = null,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    AdaptiveGroupSurface(
        Modifier.fillMaxWidth(),
        position = if (expanded) ListRowPosition.FIRST else ListRowPosition.SINGLE
    ) {
        MaterialAppRowBody(
            icon = icon,
            title = title,
            subtitle = subtitle,
            packageName = packageName,
            modifier = modifier.combinedClickable(
                onClickLabel = expandLabel,
                onClick = onExpand,
                onLongClick = { menuExpanded = true }
            ),
            selection = { selection() },
            details = details,
            trailing = {
                trailing()
                AdaptiveIconButton(onClick = { menuExpanded = true }) {
                    AdaptiveIcon(
                        if (lockState == BulkLockState.NONE) Icons.Rounded.MoreVert else Icons.Rounded.Lock,
                        stringResource(
                            when (lockState) {
                                BulkLockState.NONE -> R.string.common_more
                                BulkLockState.PARTIAL -> R.string.bulk_lock_partial
                                BulkLockState.FULL -> R.string.bulk_lock_full
                            }
                        ),
                        modifier = Modifier.size(19.dp),
                        tint = if (lockState == BulkLockState.PARTIAL)
                            MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                    )
                }
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    expandLabel, modifier = Modifier.size(18.dp)
                )
            }
        )
        BulkLockContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            canLock = lockState != BulkLockState.FULL,
            canUnlock = lockState != BulkLockState.NONE,
            onLock = onLock,
            onUnlock = onUnlock
        )
    }
}

/**
 * A single visual app-row structure shared by rules, priority, Root and visibility.
 * Selection and trailing controls are intentionally slots: their behavior differs by feature.
 */
@Composable
internal fun MaterialAppRowBody(
    icon: Bitmap?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    selection: @Composable RowScope.() -> Unit,
    packageName: String? = null,
    details: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    // Avoid ListItem's fixed 1/2/3-line minimum heights and large built-in
    // padding. Content remains naturally sized for larger font settings.
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = MaterialUiTokens.listRowHeight)
            .padding(
                horizontal = MaterialUiTokens.appRowHorizontal,
                vertical = MaterialUiTokens.appRowVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.appRowGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            selection()
            AppIcon(icon, title)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AdaptiveText(
                title,
                style = if (UiPreferencesRuntime.current.componentGrouping == UiComponentGrouping.CONNECTED)
                    MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                    else MaterialTheme.typography.bodyLarge,
                fontWeight = if (UiPreferencesRuntime.current.componentGrouping == UiComponentGrouping.CONNECTED)
                    FontWeight.SemiBold else FontWeight.Medium,
                maxLines = UiPreferencesRuntime.current.appTitleLines,
                overflow = TextOverflow.Ellipsis
            )
            AdaptiveText(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = UiPreferencesRuntime.current.appSubtitleLines,
                overflow = TextOverflow.Ellipsis
            )
            if (UiPreferencesRuntime.current.showPackageNames && !packageName.isNullOrBlank()) {
                AdaptiveText(
                    packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            details()
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}
