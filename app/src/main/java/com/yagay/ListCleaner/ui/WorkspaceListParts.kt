package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.yagay.ListCleaner.R

/**
 * Small, shared render-only primitives for Entry (LSPosed) and Component (Root).
 * Callers own selection eligibility, confirmations, drafts and backend actions.
 *
 * Keeping type, title, status and action geometry here prevents the two
 * workspaces from drifting while preserving their different semantics.
 */
/**
 * Shared borderless three-state application selection control.
 *
 * Off = muted plus, Indeterminate = primary minus, On = primary check.
 * Keep the native 48dp touch target and Checkbox semantics; callers retain
 * the existing bulk-selection, lock and system-confirmation behaviour.
 */
@Composable
internal fun WorkspaceAppSelectionCheckbox(
    state: ToggleableState,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val icon = when (state) {
        ToggleableState.Off -> Icons.Rounded.Add
        ToggleableState.Indeterminate -> Icons.Rounded.Remove
        ToggleableState.On -> Icons.Rounded.Check
    }
    val baseTint = if (state == ToggleableState.Off) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier.size(MaterialUiTokens.iconTouchTarget).triStateToggleable(
            state = state,
            enabled = enabled,
            role = Role.Checkbox,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveIcon(
            icon,
            null,
            // Balance visible space on both sides of the mark:
            // the leading 16dp page inset is twice the 8dp label inset.
            // The 48dp checkbox hit area remains unchanged.
            modifier = Modifier.size(18.dp).offset(x = -MaterialUiTokens.appLabelInset),
            tint = if (enabled) baseTint else baseTint.copy(alpha = 0.38f)
        )
    }
}

@Composable
internal fun WorkspaceAppSummaryText(
    @StringRes formatRes: Int,
    selectedCount: Int,
    totalCount: Int,
    kindLabels: List<String>
): String = stringResource(
    formatRes,
    selectedCount,
    totalCount,
    kindLabels.joinToString(" · ")
)

@Composable
internal fun WorkspaceAppNote(
    text: String,
    warning: Boolean = false,
    informational: Boolean = false,
) {
    AdaptiveText(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            warning -> MaterialTheme.colorScheme.error
            informational -> MaterialStatusPalette.informationalText()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Shared title + kind row. Both engines permit two title lines. */
@Composable
internal fun WorkspaceDetailHeading(
    title: String,
    kind: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
    ) {
        AdaptiveText(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        AdaptiveText(
            kind,
            modifier = Modifier.widthIn(max = MaterialUiTokens.detailKindMaxWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Uniform explanatory/class line; a longer component class can wrap twice. */
@Composable
internal fun WorkspaceDetailCaption(
    text: String,
    emphasized: Boolean = false,
    warning: Boolean = false
) {
    AdaptiveText(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            warning -> MaterialTheme.colorScheme.error
            emphasized -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** Same status typography for an unavailable entry and a Root component. */
@Composable
internal fun WorkspaceDetailStatus(
    text: String,
    warning: Boolean = false,
    informational: Boolean = false,
) {
    AdaptiveText(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            warning -> MaterialTheme.colorScheme.error
            informational -> MaterialStatusPalette.informationalText()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * One anchored, square overflow menu for application rows and component details.
 * All actions use the same flat list choices, including disabled lock actions.
 * The caller retains its expanded state to preserve long-press behaviour.
 */
@Composable
internal fun WorkspaceFlatOverflowMenu(
    actions: List<WorkspaceMenuAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentDescription: String,
    iconTint: Color = LocalContentColor.current
) {
    if (actions.isEmpty()) return
    Box {
        AdaptiveIconButton(
            onClick = { onExpandedChange(true) },
            enabled = actions.any { it.enabled }
        ) {
            AdaptiveIcon(
                Icons.Rounded.MoreVert,
                contentDescription,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
        }
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            menuWidth = 224.dp
        ) {
            actions.forEach { action ->
                WorkspaceFlatMenuRow(
                    title = action.label,
                    selected = false,
                    enabled = action.enabled,
                    onClick = {
                        onExpandedChange(false)
                        if (action.enabled) action.onClick()
                    }
                )
            }
        }
    }
}

/**
 * All expanded Entry/Root item actions live behind one overflow affordance.
 * Preview remains item-scoped; clicking the row still controls selection.
 * The existing long-press lock menu is preserved as an alternative.
 */
@Composable
internal fun WorkspaceDetailActionsMenu(
    onPreview: (() -> Unit)?,
    locked: Boolean,
    lockToggleEnabled: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val actions = buildList {
        if (onPreview != null) {
            add(WorkspaceMenuAction(
                label = stringResource(R.string.system_preview_action),
                onClick = onPreview
            ))
        }
        if (onEdit != null) {
            add(WorkspaceMenuAction(
                label = stringResource(R.string.component_edit_display_name),
                onClick = onEdit
            ))
        }
        if (lockToggleEnabled) {
            add(WorkspaceMenuAction(
                label = stringResource(
                    if (locked) R.string.bulk_lock_action_unlock
                    else R.string.bulk_lock_action_lock
                ),
                onClick = if (locked) onUnlock else onLock
            ))
        }
    }
    WorkspaceFlatOverflowMenu(
        actions = actions,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        contentDescription = stringResource(R.string.common_more)
    )
}
