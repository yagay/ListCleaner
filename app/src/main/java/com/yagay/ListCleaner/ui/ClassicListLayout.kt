package com.yagay.ListCleaner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    val entryDescription = stringResource(R.string.workspace_rule_section)
    val componentDescription = stringResource(R.string.root_screen_title)
    Surface(color = MaterialSelectionPalette.background()) {
        // Keep the controls content-sized instead of stretching each border to half
        // the display. A narrow device can scroll without shrinking the labels.
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Native SegmentedButtonRow divides its measured width evenly across both
            // buttons. An intrinsic-width row was too narrow for Component · Root.
            // A minimum width preserves full labels; horizontal scroll handles
            // unusually large font scales or narrow screens.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = active == ClassicCleanupMode.ENTRIES,
                onClick = { if (active != ClassicCleanupMode.ENTRIES) onEntries() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = MaterialActionColors.segmented(),
                modifier = Modifier.semantics {
                    contentDescription = entryDescription
                },
                icon = {}
            ) {
                WorkspaceControlLabel(
                    stringResource(R.string.lc_cleanup_mode_entry_short),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            SegmentedButton(
                selected = active == ClassicCleanupMode.COMPONENTS,
                onClick = { if (active != ClassicCleanupMode.COMPONENTS) onComponents() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = MaterialActionColors.segmented(),
                modifier = Modifier.semantics {
                    contentDescription = componentDescription
                },
                icon = {}
            ) {
                WorkspaceControlLabel(
                    stringResource(R.string.lc_cleanup_mode_component_short),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
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
