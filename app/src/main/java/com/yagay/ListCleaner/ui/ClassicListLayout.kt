package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/** Two mutually exclusive cleanup editors, not unrelated filter chips. */
internal enum class ClassicCleanupMode { ENTRIES, COMPONENTS }

@Composable
internal fun ClassicCleanupTabs(
    active: ClassicCleanupMode,
    onEntries: () -> Unit,
    onComponents: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MaterialUiTokens.pageHorizontal,
                vertical = 4.dp
            )
        ) {
            SegmentedButton(
                selected = active == ClassicCleanupMode.ENTRIES,
                onClick = { if (active != ClassicCleanupMode.ENTRIES) onEntries() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Entry cleanup (LSPosed)"
                },
                icon = {}
            ) {
                WorkspaceControlLabel(
                    stringResource(R.string.lc_cleanup_mode_entry_short),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            SegmentedButton(
                selected = active == ClassicCleanupMode.COMPONENTS,
                onClick = { if (active != ClassicCleanupMode.COMPONENTS) onComponents() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Component cleanup (Root)"
                },
                icon = {}
            ) {
                WorkspaceControlLabel(
                    stringResource(R.string.lc_cleanup_mode_component_short),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
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
