package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    onLock: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    onOverview: (() -> Unit)? = null,
    selection: @Composable () -> Unit,
    packageName: String? = null,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    menuActions: List<WorkspaceMenuAction>? = null,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val appContent: @Composable () -> Unit = {
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
            // Move the entire app-row content to the left. The selector's 48dp
            // target already supplies a 15dp visual inset before its 18dp mark,
            // balancing the 16dp right edge of the expand arrow.
            contentStartInset = 0.dp,
            selectionToIconGap = 0.dp,
            details = details,
            trailing = {
                trailing()
                WorkspaceFlatOverflowMenu(
                    actions = menuActions ?: listOfNotNull(
                        onOverview?.let { WorkspaceMenuAction(label = stringResource(R.string.app_overview_title), onClick = it) },
                        onLock?.let { WorkspaceMenuAction(
                            label = stringResource(R.string.bulk_lock_action_lock),
                            onClick = it,
                            enabled = lockState != BulkLockState.FULL
                        ) },
                        onUnlock?.let { WorkspaceMenuAction(
                            label = stringResource(R.string.bulk_lock_action_unlock),
                            onClick = it,
                            enabled = lockState != BulkLockState.NONE
                        ) }
                    ),
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    contentDescription = stringResource(
                        when (lockState) {
                            BulkLockState.NONE -> R.string.common_more
                            BulkLockState.PARTIAL -> R.string.bulk_lock_partial
                            BulkLockState.FULL -> R.string.bulk_lock_full
                        }
                    ),
                    iconTint = if (lockState == BulkLockState.PARTIAL)
                        MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                )
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    expandLabel, modifier = Modifier.size(MaterialUiTokens.appExpandIndicatorWidth)
                )
            }
        )
    }
    ContinuousApplicationSurface(expanded = expanded) { appContent() }
    if (expanded) ContinuousDetailSurface(selected = true) {
        Column { expandedContent() }
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
    // Only direct workspace app rows are edge-aligned; other callers retain
    // the shared page inset (e.g. app-scope picker).
    contentStartInset: Dp = MaterialUiTokens.pageHorizontal,
    selectionToIconGap: Dp = MaterialUiTokens.appLabelInset,
    packageName: String? = null,
    details: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = MaterialUiTokens.listRowHeight)
            .padding(
                start = contentStartInset,
                end = MaterialUiTokens.pageHorizontal,
                top = 4.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        selection()
        Spacer(Modifier.width(selectionToIconGap))
        AppIcon(icon, title)
        Spacer(Modifier.width(MaterialUiTokens.appLabelInset))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AdaptiveText(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = UiPreferencesRuntime.current.appTitleLines,
                overflow = TextOverflow.Ellipsis
            )
            AdaptiveText(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
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
