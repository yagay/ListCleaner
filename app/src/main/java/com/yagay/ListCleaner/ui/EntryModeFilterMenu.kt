package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.kinds
import com.yagay.ListCleaner.domain.entryGroup

/**
 * Uses the same anchored dropdown, 48dp selectable rows and fixed action footer
 * as WorkspaceFilterBar. Always render as a child of the clicked row's Box:
 * the menu is a popup, not an inline expansion or a separate modal dialog.
 *
 * Draft state only exists while expanded. Dismiss/Back/outside/Cancel discards it;
 * only Confirm can write a global default or per-kind override.
 */
@Composable
internal fun EntryModeFilterMenu(
    state: MainState,
    vm: MainViewModel,
    kind: IntentKind?,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    if (!expanded) return
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val saved: DisplayMode? =
        if (kind == null) state.displayMode else state.modeOverrides[kind]
    val savedDisabled = kind != null && (kind in state.disabledEntryKinds ||
        kind.entryGroup() in state.disabledEntryGroups)
    var draft by remember(kind, userId, saved) {
        mutableStateOf(EntryModeChangeDraft(saved))
    }
    var draftDisabled by remember(kind, userId, savedDisabled) { mutableStateOf(savedDisabled) }
    val changed = draft.hasChanges || draftDisabled != savedDisabled
    val title = when (kind) {
        null -> stringResource(R.string.lc_mode_default_title)
        IntentKind.OPEN -> stringResource(R.string.lc_mode_open_short)
        else -> stringResource(kind.titleRes())
    }
    val options: List<Pair<DisplayMode?, String>> =
        (if (kind == null) emptyList() else listOf(
            null to stringResource(
                R.string.lc_mode_follow_global_value,
                stringResource(state.displayMode.titleRes()),
            ),
        )) + DisplayMode.entries.map { mode ->
            (mode as DisplayMode?) to stringResource(mode.titleRes())
        }

    FlatWorkspaceDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 380.dp),
        menuWidth = 264.dp,
        header = { FlatPopupHeader(stringResource(R.string.lc_mode_select_for, title)) },
        footer = {
            FlatPopupFooter {
                WorkspaceFilterFooterAction(
                    label = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                )
                WorkspaceFilterFooterAction(
                    label = stringResource(R.string.lc_mode_confirm_apply),
                    primary = true,
                    enabled = changed,
                    onClick = {
                        if (!changed) return@WorkspaceFilterFooterAction
                        if (kind == null) {
                            // All affects only the default; overrides stay untouched.
                            draft.pending?.let(vm::setDisplayMode)
                        } else {
                            val existing = state.disabledEntryKinds +
                                state.disabledEntryGroups.flatMap { it.kinds() }
                            val updated = if (draftDisabled) existing + kind else existing - kind
                            if (draft.hasChanges) vm.setKindDisplayMode(kind, draft.pending)
                            vm.setDisabledEntryKinds(updated)
                        }
                        onDismiss()
                    },
                )
            }
        },
    ) {
        options.forEach { (mode, label) ->
            WorkspaceFlatMenuRow(
                title = label,
                selected = !draftDisabled && draft.pending == mode,
                compactOption = true,
                onClick = { draft = draft.select(mode); draftDisabled = false },
            )
        }
        if (kind != null) {
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.lc_mode_disabled),
                selected = draftDisabled,
                compactOption = true,
                onClick = { draftDisabled = true },
            )
        }
    }
}
