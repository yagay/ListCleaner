package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

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
    // Only one inline mode editor at a time, and no modes are written
    // until its explicit confirmation button is pressed.
    var editingModeKey by rememberSaveable(userId) { mutableStateOf<String?>(null) }

    val savedDisabled = remember(state.disabledEntryKinds, state.disabledEntryGroups) {
        state.disabledEntryKinds + state.disabledEntryGroups.flatMap { it.kinds() }
    }
    var draftDisabled by remember(userId, savedDisabled) { mutableStateOf(savedDisabled) }
    val hasPendingChanges = draftDisabled != savedDisabled

    Scaffold(
        bottomBar = {
            if (hasPendingChanges) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveTextButton(onClick = { draftDisabled = savedDisabled }) {
                        AdaptiveText(stringResource(R.string.common_cancel))
                    }
                    AdaptiveButton(onClick = {
                        // Convert legacy group disables to explicit per-kind state so
                        // enabling one member cannot be overridden by its old group flag.
                        vm.setDisabledEntryKinds(draftDisabled)
                        if (state.disabledEntryGroups.isNotEmpty()) vm.setDisabledEntryGroups(emptySet())
                    }) {
                        AdaptiveText(stringResource(R.string.lc_mode_confirm_apply))
                    }
                }
            }
        },
    ) { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        contentPadding = PaddingValues(vertical = MaterialUiTokens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "global-mode-default") {
            GlobalModeSettingsRow(
                title = stringResource(R.string.lc_mode_default_title),
                selectedMode = stringResource(state.displayMode.titleRes()),
                expanded = editingModeKey == GLOBAL_MODE_KEY,
                onClick = {
                    editingModeKey = if (editingModeKey == GLOBAL_MODE_KEY) null else GLOBAL_MODE_KEY
                },
                modeMenu = {
                    EntryModeFilterMenu(
                        state = state, vm = vm, kind = null,
                        expanded = editingModeKey == GLOBAL_MODE_KEY,
                        onDismiss = { editingModeKey = null },
                    )
                },
            )
        }

        IntentKind.entries.forEach { kind ->
            item(key = "global-mode-kind-${kind.name}") {
                        val isDisabled = kind in draftDisabled
                        GlobalModeSettingsRow(
                            title = stringResource(kind.titleRes()),
                            selectedMode = stringResource(
                                if (kind in (draftDisabled - savedDisabled)) R.string.lc_mode_pending_disable
                                else if (kind in (savedDisabled - draftDisabled)) R.string.lc_mode_pending_enable
                                else if (isDisabled) R.string.lc_mode_disabled else R.string.lc_mode_enabled
                            ) + " · " + (state.modeOverrides[kind]?.let {
                                stringResource(it.titleRes())
                            } ?: stringResource(
                                R.string.lc_mode_follow_global_value,
                                stringResource(state.displayMode.titleRes()),
                            )),
                            expanded = editingModeKey == kind.name,
                            onClick = {
                                editingModeKey = if (editingModeKey == kind.name) null else kind.name
                            },
                            modeMenu = {
                                EntryModeFilterMenu(
                                    state = state, vm = vm, kind = kind,
                                    expanded = editingModeKey == kind.name,
                                    onDismiss = { editingModeKey = null },
                                )
                            },
                            enabled = !isDisabled,
                            onEnabledChange = { enabled ->
                                draftDisabled = if (enabled) draftDisabled - kind else draftDisabled + kind
                            },
                        )
            }
        }
    }
    }

}

/** The same anchored Filter-style menu opens from each Lists mode row. */
private const val GLOBAL_MODE_KEY = "GLOBAL"

@Composable
private fun GlobalModeSettingsRow(
    title: String,
    selectedMode: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modeMenu: @Composable () -> Unit,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        MaterialSettingsRow(
            title = title,
            supportingText = selectedMode,
            onClick = onClick,
        ) {
            if (enabled != null && onEnabledChange != null) {
                AdaptiveSwitch(checked = enabled, onCheckedChange = onEnabledChange)
            } else {
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    title,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The popup is anchored to this particular row, not a full-screen dialog.
        modeMenu()
    }
}
