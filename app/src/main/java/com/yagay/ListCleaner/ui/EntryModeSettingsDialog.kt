package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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

/**
 * Entry header shortcut: one flat choice dialog for the current Entry kind
 * (or the global default when All is selected).
 *
 * Selection only updates the local draft. Neither the repository nor the
 * hooked system resolver sees changes until the explicit Confirm action.
 */
@Composable
internal fun EntryModeSettingsDialog(
    state: MainState,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val kind: IntentKind? = state.filter
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val saved: DisplayMode? =
        if (kind == null) state.displayMode else state.modeOverrides[kind]
    var draft by remember(kind, userId) { mutableStateOf(EntryModeChangeDraft(saved)) }

    val title = if (kind == null) stringResource(R.string.lc_mode_default_title)
        else if (kind == IntentKind.OPEN) stringResource(R.string.lc_mode_open_short)
        else stringResource(kind.titleRes())
    val options: List<Pair<DisplayMode?, String>> =
        (if (kind == null) emptyList() else listOf(
            null to stringResource(
                R.string.lc_mode_follow_global_value,
                stringResource(state.displayMode.titleRes()),
            ),
        )) + DisplayMode.entries.map { mode ->
            (mode as DisplayMode?) to stringResource(mode.titleRes())
        }

    SettingsFlatDialog(
        onDismissRequest = onDismiss,
        title = {
            AdaptiveText(stringResource(R.string.lc_mode_select_for, title))
        },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (mode, label) ->
                    SettingsFlatChoiceRow(
                        label = label,
                        selected = mode == draft.pending,
                        onClick = { draft = draft.select(mode) },
                    )
                }
                AdaptiveText(
                    stringResource(R.string.lc_mode_confirm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                enabled = draft.hasChanges,
                onClick = {
                    if (!draft.hasChanges) return@AdaptiveTextButton
                    if (kind == null) {
                        // The All page configures only the global default; per-kind
                        // overrides remain intact. The global choice is never null.
                        draft.pending?.let(vm::setDisplayMode)
                    } else {
                        vm.setKindDisplayMode(kind, draft.pending)
                    }
                    onDismiss()
                },
            ) {
                AdaptiveText(stringResource(R.string.lc_mode_confirm_apply))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            }
        },
    )
}
