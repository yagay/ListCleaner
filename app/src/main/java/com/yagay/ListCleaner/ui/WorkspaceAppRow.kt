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
import com.yagay.ListCleaner.R

/** Shared interaction contract for rule, priority and Root-component app rows.
 * Selection, expand, and lock remain separate actions with one theme-native surface.
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
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    AdaptiveGroupSurface(
        Modifier.fillMaxWidth(),
        position = if (expanded) MiuixRowPosition.FIRST else MiuixRowPosition.SINGLE
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().then(modifier)
                .combinedClickable(onClickLabel = expandLabel, onClick = onExpand,
                    onLongClick = { menuExpanded = true })
                .heightIn(min = 60.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            selection()
            AppIcon(icon, title)
            Column(
                Modifier.weight(1f).padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AdaptiveText(title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AdaptiveText(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                details()
            }
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
