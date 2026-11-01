package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
    // Only one inline mode editor at a time, and no modes are written
    // until its explicit confirmation button is pressed.
    var editingModeKey by rememberSaveable(userId) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MaterialUiTokens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "global-mode-default") {
            SettingsArea(stringResource(R.string.dashboard_global_mode)) {
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
                val overridden = kinds.count { it in state.modeOverrides || it in state.disabledEntryKinds || it.entryGroup() in state.disabledEntryGroups }
                val isExpanded = expanded == group
                GlobalModeGroupHeader(
                    title = stringResource(group.titleRes()),
                    summary = if (overridden == 0) {
                        stringResource(R.string.lc_mode_inherit_count, kinds.size)
                    } else {
                        stringResource(R.string.lc_mode_override_count, overridden, kinds.size)
                    },
                    expanded = isExpanded,
                    onClick = {
                        expanded = if (isExpanded) null else group
                        editingModeKey = null // Closing a group discards any pending mode.
                    },
                )
                if (isExpanded) {
                    // The rail and indented row backgrounds mark these as children
                    // of the expanded group instead of separate top-level sections.
                    val railColor = MaterialTheme.colorScheme.primary
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                            .drawBehind {
                                val inset = 5.dp.toPx()
                                drawLine(
                                    railColor,
                                    Offset(inset, 0f),
                                    Offset(inset, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            .padding(start = 12.dp),
                    ) {
                        kinds.forEach { kind: IntentKind ->
                            GlobalModeSettingsRow(
                                title = stringResource(kind.titleRes()),
                                selectedMode = if (kind in state.disabledEntryKinds || group in state.disabledEntryGroups) {
                                    stringResource(R.string.lc_mode_disabled)
                                } else state.modeOverrides[kind]?.let {
                                    stringResource(it.titleRes())
                                } ?: stringResource(
                                    R.string.lc_mode_follow_global_value,
                                    stringResource(state.displayMode.titleRes()),
                                ),
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
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single clickable accordion header: title, override count and affordance live
 * together. FlatRowSurface preserves the shared 48+dp click target and ripple.
 * Explicit state text + opposite chevrons make expansion clear in either theme.
 */
@Composable
private fun GlobalModeGroupHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val action = stringResource(
        if (expanded) R.string.lc_mode_collapse_group else R.string.lc_mode_expand_group
    )
    val accent = MaterialTheme.colorScheme.primary
    val textColor = if (expanded) accent else MaterialTheme.colorScheme.onSurface
    FlatRowSurface(
        modifier = Modifier.semantics { stateDescription = action },
        onClick = onClick,
        color = if (expanded) accent.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.surface,
        showDivider = !expanded,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AdaptiveText(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                )
                AdaptiveText(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AdaptiveText(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = if (expanded) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdaptiveIcon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (expanded) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        MaterialSettingsRow(
            title = title,
            supportingText = selectedMode,
            onClick = onClick,
        ) {
            AdaptiveIcon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                title,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The popup is anchored to this particular row, not a full-screen dialog.
        modeMenu()
    }
}
