package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.DisplayMode

/**
 * Global Mode is an Entry runtime filtering policy, not the manager's list filter.
 * A category override is nullable: null means inherit the global mode.
 * Only confirmed rule selections are consumed by Hook implementations.
 */
@Composable
internal fun EntryModeSettingsDialog(
    state: MainState,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val kind = state.filter
    SettingsFlatDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.dashboard_global_mode)) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                SettingsChoices(
                    title = stringResource(R.string.dashboard_global_mode),
                    value = state.displayMode,
                    options = DisplayMode.entries.map { it to stringResource(it.titleRes()) },
                    onSelected = vm::setDisplayMode,
                )
                if (kind != null) {
                    SettingsChoices(
                        title = stringResource(kind.titleRes()),
                        value = state.modeOverrides[kind],
                        options = listOf<Pair<DisplayMode?, String>>(
                            null to stringResource(R.string.lc_mode_follow_global)
                        ) + DisplayMode.entries.map {
                            (it as DisplayMode?) to stringResource(it.titleRes())
                        },
                        onSelected = { vm.setKindDisplayMode(kind, it) },
                    )
                }
                AdaptiveText(
                    stringResource(R.string.lc_mode_settings_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            }
        },
    )
}
