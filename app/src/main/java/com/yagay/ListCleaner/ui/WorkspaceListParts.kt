package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.yagay.ListCleaner.R

/**
 * Small, shared render-only primitives for Entry (LSPosed) and Component (Root).
 * Callers own selection eligibility, confirmations, drafts and backend actions.
 *
 * Keeping type, title, status and action geometry here prevents the two
 * workspaces from drifting while preserving their different semantics.
 */
@Composable
internal fun WorkspaceAppSelectionCheckbox(
    state: ToggleableState,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    AdaptiveTriStateCheckbox(state = state, enabled = enabled, onClick = onClick)
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
            style = if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED)
                MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
                else MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        AdaptiveText(
            kind,
            modifier = Modifier.widthIn(max = MaterialUiTokens.detailKindMaxWidth),
            style = if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED)
                MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                else MaterialTheme.typography.labelMedium,
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
        maxLines = if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) Int.MAX_VALUE else 2,
        overflow = if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED) TextOverflow.Clip else TextOverflow.Ellipsis
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

/** Common 48dp Material 3 touch target and 18dp icon for details actions. */
@Composable
internal fun WorkspaceDetailIconAction(
    image: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    AdaptiveIconButton(
        onClick = onClick,
        modifier = if (UiPreferencesRuntime.current.listStyle == UiListStyle.GROUPED)
            Modifier.size(40.dp) else Modifier
    ) {
        AdaptiveIcon(
            image,
            contentDescription,
            modifier = Modifier.size(18.dp)
        )
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
    if (actions.isEmpty()) return
    Box {
        AdaptiveIconButton(onClick = { expanded = true }) {
            AdaptiveIcon(
                Icons.Rounded.MoreVert,
                stringResource(R.string.common_more),
                modifier = Modifier.size(18.dp)
            )
        }
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)
        ) {
            actions.forEach { action ->
                WorkspaceFlatMenuRow(
                    title = action.label,
                    selected = false,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}
