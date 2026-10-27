package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import com.yagay.ListCleaner.domain.DisplayMode
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
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = MaterialUiTokens.pageHorizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatCleanupModeTab(
                    label = stringResource(R.string.lc_cleanup_mode_entry_short),
                    description = stringResource(R.string.workspace_rule_section),
                    selected = active == ClassicCleanupMode.ENTRIES,
                    onClick = onEntries,
                    modifier = Modifier.weight(1f)
                )
                FlatCleanupModeTab(
                    label = stringResource(R.string.lc_cleanup_mode_component_short),
                    description = stringResource(R.string.root_screen_title),
                    selected = active == ClassicCleanupMode.COMPONENTS,
                    onClick = onComponents,
                    modifier = Modifier.weight(1f)
                )
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
    onDisplayModeChange: (DisplayMode) -> Unit,
) {
    // Runtime mode controls the effect of saved rules, not the visible list filter.
    // Keep this informational/action row scrollable instead of pinning another toolbar.
    CleanupStatusAlert(state, onOpenStatus = onOpenStatus)
    var expanded by remember { mutableStateOf(false) }
    WorkspaceModeStatusRow(
        title = stringResource(R.string.dashboard_global_mode),
        status = stringResource(state.displayMode.titleRes())
    ) {
        Box {
            AdaptiveTextButton(onClick = { expanded = true }) {
                AdaptiveText(stringResource(R.string.dashboard_switch), maxLines = 1)
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
            }
            FlatWorkspaceDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DisplayMode.entries.forEach { mode ->
                    WorkspaceFlatMenuRow(
                        title = stringResource(mode.titleRes()),
                        selected = state.displayMode == mode,
                        onClick = {
                            expanded = false
                            onDisplayModeChange(mode)
                        }
                    )
                }
            }
        }
    }
}
