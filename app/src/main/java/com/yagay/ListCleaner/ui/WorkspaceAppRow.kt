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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/**
 * Single interaction contract for Rules, Ordering and Root component apps.
 * The themed surface is provided by AdaptiveGroupSurface, not a second
 * Material layout hidden inside the MIUIX tree.
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
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expandLabel = stringResource(
        if (expanded) R.string.common_collapse else R.string.common_expand
    )
    AdaptiveGroupSurface(
        Modifier.fillMaxWidth(),
        position = if (expanded) MiuixRowPosition.FIRST else MiuixRowPosition.SINGLE
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(modifier)
                .combinedClickable(
                    onClickLabel = expandLabel,
                    onClick = onExpand,
                    onLongClick = { menuExpanded = true }
                )
                .heightIn(min = 68.dp)
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            selection()
            SharedAppIdentity(icon, title) {
                SharedAppSupportingText(subtitle)
                details()
            }
            trailing()
            AdaptiveIconButton(onClick = { menuExpanded = true }) {
                AdaptiveIcon(
                    if (lockState == BulkLockState.NONE) Icons.Rounded.MoreVert else Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.bulk_lock_options),
                    tint = if (lockState == BulkLockState.PARTIAL)
                        MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                )
            }
            AdaptiveIconButton(onClick = onExpand) {
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = expandLabel,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
