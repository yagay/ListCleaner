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
import androidx.compose.material.icons.rounded.Visibility
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
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenWithTypeSummary
import com.yagay.ListCleaner.domain.summarizeOpenWithTypes

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
    onUnlock: () -> Unit,
    openWithDefinitions: Map<OpenPreset, CustomOpenDefinition>? = null,
) {
    val openTypeSummary = if (openWithDefinitions != null) {
        remember(group.components, openWithDefinitions) {
            summarizeOpenWithTypes(group.components, openWithDefinitions)
        }
    } else null
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
        packageName = group.packageName,
        subtitle = WorkspaceAppSummaryText(
            R.string.app_row_selection_summary,
            selectedCount, group.components.size, kindTitles
        ),
        expanded = expanded,
        lockState = lockState,
        onExpand = onExpand,
        onLock = onLock,
        onUnlock = onUnlock,
        selection = {
            WorkspaceAppSelectionCheckbox(
                state = selectionState,
                onClick = { onSelect(selectionState != ToggleableState.On) }
            )
        },
        details = {
            if (openTypeSummary != null && !openTypeSummary.isEmpty) {
                OpenWithTypeIndicator(openTypeSummary, openWithDefinitions.orEmpty(), expanded)
            }
            if (group.components.any { it.unavailable || it.restricted }) {
                WorkspaceAppNote(
                    stringResource(R.string.app_row_unavailable_warning),
                    warning = true
                )
            }
        }
    )
}

/** A single read-only label for all detected formats; no extra selection controls. */
@Composable
private fun OpenWithTypeIndicator(
    summary: OpenWithTypeSummary,
    customDefinitions: Map<OpenPreset, CustomOpenDefinition>,
    expanded: Boolean,
) {
    val labels = buildList {
        if (summary.universal) add(stringResource(R.string.open_type_universal))
        summary.presets.forEach { preset ->
            add(customDefinitions[preset]?.title ?: stringResource(preset.titleRes()))
        }
    }
    // Compact app rows stay a single line; expanding reveals every detected type.
    val compactCount = 3
    val label = if (!expanded && labels.size > compactCount) {
        labels.take(compactCount).joinToString(" · ") + " · +${labels.size - compactCount}"
    } else labels.joinToString(" · ")
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(MaterialUiTokens.groupedInnerRadius),
    ) {
        AdaptiveText(
            label,
            modifier = Modifier.padding(horizontal = MaterialUiTokens.contentGap, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    onEditTitle: () -> Unit,
    onPreview: (() -> Unit)? = null
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
            WorkspaceDetailHeading(
                item.activityLabel,
                stringResource(item.rule.kind.titleRes())
            )
            selectionNote?.let {
                WorkspaceDetailCaption(it, emphasized = true)
            }
            if (!customTitle.isNullOrBlank()) {
                WorkspaceDetailCaption(
                    stringResource(R.string.component_shown_as, customTitle),
                    emphasized = true
                )
            }
            WorkspaceDetailCaption(item.rule.className)
            if (item.unavailable) WorkspaceDetailStatus(
                stringResource(R.string.component_unavailable), warning = true
            ) else if (item.restricted) WorkspaceDetailStatus(
                stringResource(R.string.component_restricted)
            )
        },
        trailing = {
            onPreview?.let { launch ->
                WorkspaceDetailIconAction(
                    Icons.Rounded.Visibility,
                    stringResource(R.string.system_preview_action),
                    launch
                )
            }
            WorkspaceDetailIconAction(
                Icons.Rounded.Edit,
                stringResource(R.string.component_edit_display_name),
                onEditTitle
            )
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
internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
    if (!UiPreferencesRuntime.current.showAppIcons) return
    Box(
        modifier = Modifier.size(MaterialUiTokens.appIconSize)
            .clip(MaterialUiTokens.appIconShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            AdaptiveIcon(
                Icons.Rounded.Apps,
                stringResource(R.string.app_icon_description, appLabel),
                modifier = Modifier.size(22.dp),
                tint = MaterialSelectionPalette.text()
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.app_icon_description, appLabel),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
