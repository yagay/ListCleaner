package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind

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
    val selectionState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == group.components.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val kindTitles = mutableListOf<String>()
    for (kind in IntentKind.entries) {
        if (group.components.any { it.rule.kind == kind }) kindTitles += stringResource(kind.titleRes())
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .bulkLockSwipe(onLock = onLock, onUnlock = onUnlock)
            .clickable(onClickLabel = expandLabel, onClick = onExpand)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(state = selectionState, onClick = { onSelect(selectionState != ToggleableState.On) })
        AppIcon(bitmap = group.appIcon, appLabel = group.appLabel)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(group.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.app_row_selection_summary,
                    selectedCount,
                    group.components.size,
                    kindTitles.joinToString(" · ")
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val missing = group.components.count { it.unavailable || it.restricted }
            if (missing > 0) {
                Text(
                    stringResource(R.string.app_row_unavailable_rules, missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (lockState != BulkLockState.NONE) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(
                    if (lockState == BulkLockState.PARTIAL) R.string.bulk_lock_partial
                    else R.string.bulk_lock_full
                ),
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                tint = if (lockState == BulkLockState.PARTIAL) MaterialTheme.colorScheme.tertiary
                else LocalContentColor.current
            )
        }
        IconButton(onClick = onExpand) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, expandLabel)
        }
    }
    HorizontalDivider()
}

@Composable
internal fun ComponentRow(
    item: ComponentCandidate,
    checked: Boolean,
    customTitle: String?,
    selectionNote: String? = null,
    locked: Boolean = false,
    lockToggleEnabled: Boolean = true,
    onToggle: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onEditTitle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .bulkLockSwipe(
                enabled = lockToggleEnabled,
                onLock = onLock,
                onUnlock = onUnlock
            )
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp).padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComponentSelectionMark(checked)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.activityLabel,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(item.rule.kind.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            selectionNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!customTitle.isNullOrBlank()) {
                Text(
                    stringResource(R.string.component_shown_as, customTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                item.rule.className,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.unavailable) {
                Text(
                    stringResource(R.string.component_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (item.restricted) {
                Text(stringResource(R.string.component_restricted), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.bulk_lock_full),
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
            )
        }
        IconButton(onClick = onEditTitle) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.component_edit_display_name))
        }
    }
}

@Composable
internal fun Modifier.bulkLockSwipe(
    enabled: Boolean = true,
    onLock: () -> Unit,
    onUnlock: () -> Unit
): Modifier {
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    return pointerInput(enabled, threshold, onLock, onUnlock) {
        if (!enabled) return@pointerInput
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { change, amount ->
                totalDrag += amount
                change.consume()
            },
            onDragCancel = { totalDrag = 0f },
            onDragEnd = {
                when {
                    totalDrag >= threshold -> onLock()
                    totalDrag <= -threshold -> onUnlock()
                }
                totalDrag = 0f
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
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
    if (bitmap == null) {
        Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp))
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.app_icon_description, appLabel),
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
        contentScale = ContentScale.Fit
    )
}
