package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/** Two exclusive cleanup editors, presented as flat text tabs, not a raised capsule. */
internal enum class ClassicCleanupMode { ENTRIES, COMPONENTS }

@Composable
internal fun ClassicCleanupTabs(
    active: ClassicCleanupMode,
    onEntries: () -> Unit,
    onComponents: () -> Unit,
    modeLabel: String? = null,
    onModeClick: () -> Unit = {},
    modeExpanded: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            if (active == ClassicCleanupMode.ENTRIES && modeLabel != null) {
                // One clear Entry header row: "Entry Cleanup   Open · Show selected only".
                // Keep a compact Components shortcut rather than shrinking the mode label
                // between two full-width text tabs (which made it difficult to read).
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = MaterialUiTokens.pageHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkspaceControlLabel(
                        stringResource(R.string.lc_entry_cleanup_header),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        onClick = onModeClick,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            WorkspaceControlLabel(
                                modeLabel,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            AdaptiveIcon(
                                if (modeExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Surface(
                        onClick = onComponents,
                        modifier = Modifier.size(48.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AdaptiveIcon(
                                Icons.Rounded.GridView,
                                stringResource(R.string.lc_cleanup_mode_component_short),
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = MaterialUiTokens.pageHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlatCleanupModeTab(
                        label = stringResource(R.string.lc_cleanup_mode_entry_short),
                        description = stringResource(R.string.workspace_rule_section),
                        selected = active == ClassicCleanupMode.ENTRIES,
                        onClick = onEntries,
                        modifier = Modifier.weight(1f),
                    )
                    FlatCleanupModeTab(
                        label = stringResource(R.string.lc_cleanup_mode_component_short),
                        description = stringResource(R.string.root_screen_title),
                        selected = active == ClassicCleanupMode.COMPONENTS,
                        onClick = onComponents,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AdaptiveHorizontalDivider(thickness = 0.5.dp)
        }
    }
}

/** Shared full-width, 48dp touch targets, with a 2dp indicator instead of a filled pill. */
@Composable
private fun FlatCleanupModeTab(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                WorkspaceControlLabel(
                    label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
internal fun ClassicListHeader(
    state: MainState,
    onOpenStatus: () -> Unit,
) {
    // Keep status beneath the mode switch. Only the two-mode selector is pinned.
    CleanupStatusAlert(state, onOpenStatus = onOpenStatus)
}
