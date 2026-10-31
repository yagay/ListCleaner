package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.kinds

/**
 * Bottom-navigation Lists tab: Global Mode management only.
 *
 * No active notification, stale-rule or AOSP surface editor is exposed here.
 * The underlying cleanup engines and stored rules remain untouched.
 * Effective modes control system Entry filtering, never the manager's UiFilter.
 */
@Composable
internal fun SurfaceManagementTab(state: MainState, vm: MainViewModel) {
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(userId) { mutableStateOf<EntryGroup?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MaterialUiTokens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "global-mode-default") {
            SettingsArea(stringResource(R.string.dashboard_global_mode)) {
                SettingsChoices(
                    title = stringResource(R.string.lc_mode_default_title),
                    value = state.displayMode,
                    options = DisplayMode.entries.map { it to stringResource(it.titleRes()) },
                    onSelected = vm::setDisplayMode,
                )
                SettingsHint(stringResource(R.string.dashboard_global_mode_help))
            }
        }

        item(key = "global-mode-overrides-explanation") {
            SettingsArea(stringResource(R.string.lc_mode_category_overrides)) {
                SettingsHint(stringResource(R.string.lc_mode_category_overrides_help))
            }
        }

        EntryGroup.entries.forEach { group ->
            item(key = "global-mode-group-${group.name}") {
                val kinds = group.kinds()
                val overridden = kinds.count { it in state.modeOverrides }
                SettingsArea(stringResource(group.titleRes())) {
                    SettingsActionButton(
                        onClick = { expanded = if (expanded == group) null else group },
                    ) {
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
    }
}
