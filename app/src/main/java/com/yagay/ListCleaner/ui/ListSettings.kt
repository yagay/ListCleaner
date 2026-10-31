package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.kinds

/**
 * The former Lists settings page now owns the Entry Global Mode policy.
 * Manager-only UI list defaults remain stored for compatibility but are not
 * exposed here: they are distinct from resolver/LSPosed visibility modes.
 */
@Composable
internal fun SettingsListControls(state: MainState, vm: MainViewModel) {
    SettingsArea(stringResource(R.string.dashboard_global_mode)) {
        SettingsChoices(
            title = stringResource(R.string.lc_mode_default_title),
            value = state.displayMode,
            options = DisplayMode.entries.map { it to stringResource(it.titleRes()) },
            onSelected = vm::setDisplayMode,
        )
        SettingsHint(stringResource(R.string.dashboard_global_mode_help))
    }

    SettingsArea(stringResource(R.string.lc_mode_category_overrides)) {
        SettingsHint(stringResource(R.string.lc_mode_category_overrides_help))
    }

    // Expand one category group at a time. Keep the full list of entry kinds
    // available without making the settings landing view dozens of rows long.
    var expanded by rememberSaveable { mutableStateOf<EntryGroup?>(null) }
    EntryGroup.entries.forEach { group ->
        val kinds = group.kinds()
        val overridden = kinds.count { it in state.modeOverrides }
        SettingsArea(stringResource(group.titleRes())) {
            SettingsActionButton(onClick = {
                expanded = if (expanded == group) null else group
            }) {
                AdaptiveText(
                    if (overridden == 0) stringResource(R.string.lc_mode_all_inherit)
                    else stringResource(R.string.lc_mode_override_count, overridden, kinds.size),
                )
            }
            if (expanded == group) {
                kinds.forEach { kind: IntentKind ->
                    SettingsChoices(
                        title = stringResource(kind.titleRes()),
                        value = state.modeOverrides[kind],
                        options = listOf<Pair<DisplayMode?, String>>(
                            null to stringResource(
                                R.string.lc_mode_follow_global_value,
                                stringResource(state.displayMode.titleRes()),
                            ),
                        ) + DisplayMode.entries.map { mode ->
                            (mode as DisplayMode?) to stringResource(mode.titleRes())
                        },
                        onSelected = { chosen -> vm.setKindDisplayMode(kind, chosen) },
                    )
                }
            }
        }
    }
}
