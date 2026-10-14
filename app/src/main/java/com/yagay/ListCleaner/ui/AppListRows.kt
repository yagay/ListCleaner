package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    group: AppGroup,
    selected: Set<com.yagay.ListCleaner.domain.ComponentRule>,
    expanded: Boolean,
    lockState: BulkLockState,
    onExpand: () -> Unit,
    onSelect: (Boolean) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit
) {
    val selectedCount = group.components.count { it.rule in selected }
    val selectionState = listSelectionState(selectedCount, group.components.size)
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val kindTitles = mutableListOf<String>()
    for (kind in IntentKind.entries) {
        if (group.components.any { it.rule.kind == kind }) kindTitles += stringResource(kind.titleRes())
    }
    WorkspaceAppRow(
        icon = group.appIcon,
        title = group.appLabel,
        subtitle = stringResource(
            R.string.app_row_selection_summary, selectedCount,
            group.components.size, kindTitles.joinToString(" · ")
        ),
        expanded = expanded,
        lockState = lockState,
        onExpand = onExpand,
        onLock = onLock,
        onUnlock = onUnlock,
        selection = {
            AdaptiveTriStateCheckbox(
                state = selectionState,
                onClick = { onSelect(selectionState != ToggleableState.On) }
            )
        },
        details = {
            if (group.components.any { it.unavailable || it.restricted }) {
                AdaptiveText(
                    stringResource(R.string.app_row_unavailable_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComponentRow(
    item: ComponentCandidate,
    checked: Boolean,
    customTitle: String?,
    selectionNote: String? = null,
    position: ListRowPosition = ListRowPosition.SINGLE,
    locked: Boolean = false,
    lockToggleEnabled: Boolean = true,
    onToggle: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onEditTitle: () -> Unit
) {
    WorkspaceDetailRow(
        position = position,
        checked = checked,
        enabled = !locked,
        locked = locked,
        lockToggleEnabled = lockToggleEnabled,
        onToggle = onToggle,
        onLock = onLock,
        onUnlock = onUnlock,
        content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AdaptiveText(
                    item.activityLabel,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                AdaptiveText(
                    stringResource(item.rule.kind.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            selectionNote?.let {
                AdaptiveText(
                    it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (!customTitle.isNullOrBlank()) {
                AdaptiveText(
                    stringResource(R.string.component_shown_as, customTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            AdaptiveText(
                item.rule.className, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (item.unavailable) AdaptiveText(
                stringResource(R.string.component_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            ) else if (item.restricted) AdaptiveText(
                stringResource(R.string.component_restricted),
                style = MaterialTheme.typography.labelSmall
            )
        },
        trailing = {
            AdaptiveIconButton(onClick = onEditTitle) {
                AdaptiveIcon(Icons.Rounded.Edit, stringResource(R.string.component_edit_display_name))
            }
        }
    )
}

@Composable
internal fun BulkLockContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canLock: Boolean,
    canUnlock: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AdaptiveDropdownMenuItem(
            text = { AdaptiveText(stringResource(R.string.bulk_lock_action_lock)) },
            enabled = canLock,
            onClick = {
                onDismiss()
                onLock()
            }
        )
        AdaptiveDropdownMenuItem(
            text = { AdaptiveText(stringResource(R.string.bulk_lock_action_unlock)) },
            enabled = canUnlock,
            onClick = {
                onDismiss()
                onUnlock()
            }
        )
    }
}

@Composable
private fun ComponentSelectionMark(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .background(if (checked) colors.primary else Color.Transparent, CircleShape)
                .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                AdaptiveIcon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
    if (bitmap == null) {
        AdaptiveIcon(Icons.Rounded.Apps, null, Modifier.size(36.dp))
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.app_icon_description, appLabel),
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
        contentScale = ContentScale.Fit
    )
}