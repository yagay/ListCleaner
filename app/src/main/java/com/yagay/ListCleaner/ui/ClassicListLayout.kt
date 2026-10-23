package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/** Two top-level modes for the classic (main-style) cleanup workspace.
 * They are tabs over the existing editors, not two copies of the rule data.
 */
internal enum class ClassicCleanupMode { ENTRIES, COMPONENTS }

@Composable
internal fun ClassicCleanupTabs(
    active: ClassicCleanupMode,
    onEntries: () -> Unit,
    onComponents: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MaterialUiTokens.pageHorizontal,
                vertical = MaterialUiTokens.contentGap
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.categoryGap)
        ) {
            AdaptiveFilterChip(
                modifier = Modifier.weight(1f),
                selected = active == ClassicCleanupMode.ENTRIES,
                onClick = { if (active != ClassicCleanupMode.ENTRIES) onEntries() },
                label = {
                    AdaptiveText(
                        stringResource(R.string.workspace_rule_section),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            )
            AdaptiveFilterChip(
                modifier = Modifier.weight(1f),
                selected = active == ClassicCleanupMode.COMPONENTS,
                onClick = { if (active != ClassicCleanupMode.COMPONENTS) onComponents() },
                label = {
                    AdaptiveText(
                        stringResource(R.string.root_screen_title),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
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
