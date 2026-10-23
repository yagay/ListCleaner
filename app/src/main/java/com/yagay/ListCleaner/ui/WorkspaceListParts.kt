package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    warning: Boolean = false
) {
    AdaptiveText(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (warning) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
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
    warning: Boolean = false
) {
    AdaptiveText(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
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
    AdaptiveIconButton(onClick = onClick) {
        AdaptiveIcon(
            image,
            contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}
