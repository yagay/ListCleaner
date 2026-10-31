package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Shared inline expandable chooser for Entry and Lists. No modal dialog, extra
 * menu, or immediate writes: only the final Confirm persists a mode change.
 * Removing this composable (collapse/navigation/profile switch) drops the draft.
 */
@Composable
internal fun InlineEntryModePicker(
    state: MainState,
    vm: MainViewModel,
    kind: IntentKind?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val saved: DisplayMode? = if (kind == null) state.displayMode else state.modeOverrides[kind]
    var draft by remember(kind, userId, saved) { mutableStateOf(EntryModeChangeDraft(saved)) }
    val options: List<Pair<DisplayMode?, String>> =
        (if (kind == null) emptyList() else listOf(
            null to stringResource(
                R.string.lc_mode_follow_global_value,
                stringResource(state.displayMode.titleRes()),
            ),
        )) + DisplayMode.entries.map { mode ->
            (mode as DisplayMode?) to stringResource(mode.titleRes())
        }

    Column(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
    ) {
        options.forEach { (mode, label) ->
            SettingsFlatChoiceRow(
                label = label,
                selected = mode == draft.pending,
                onClick = { draft = draft.select(mode) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            }
            AdaptiveTextButton(
                enabled = draft.hasChanges,
                onClick = {
                    if (!draft.hasChanges) return@AdaptiveTextButton
                    if (kind == null) {
                        // All changes only the global default; individual overrides persist.
                        draft.pending?.let(vm::setDisplayMode)
                    } else {
                        vm.setKindDisplayMode(kind, draft.pending)
                    }
                    onDismiss()
                },
            ) {
                AdaptiveText(stringResource(R.string.lc_mode_confirm_apply))
            }
        }
    }
}
