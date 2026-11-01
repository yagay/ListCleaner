package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig

internal data class EntryTypeMenuOption(val key: String, val title: String)

/** Shared flat type picker for Open With, Share and Multi Share. */
@Composable
internal fun EntryTypeFilterMenu(
    selected: String?,
    options: List<EntryTypeMenuOption>,
    onSelected: (String?) -> Unit,
    selectedTitle: String,
    filterTitle: String,
    onManageTypes: (() -> Unit)? = null,
    iconOnly: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScroll = rememberScrollState()
    Box {
        if (iconOnly) {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = if (selected != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AdaptiveIcon(
                        Icons.Rounded.Description,
                        "$filterTitle: $selectedTitle",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            CompactCategoryButton(selected = true, onClick = { expanded = true }) {
                WorkspaceControlLabel(
                    stringResource(R.string.compact_filter_format, filterTitle, selectedTitle),
                    modifier = Modifier.widthIn(max = 150.dp),
                    color = compactCategoryLabelColor(true)
                )
                AdaptiveIcon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp),
                    tint = compactCategoryLabelColor(true))
            }
        }
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
            menuWidth = 264.dp,
            scrollState = menuScroll,
        ) {
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { expanded = false; onSelected(null) },
            )
            if (onManageTypes != null) {
                WorkspaceFlatMenuRow(
                    title = stringResource(R.string.open_manage_custom),
                    selected = false,
                    onClick = { expanded = false; onManageTypes() },
                )
            }
            AdaptiveHorizontalDivider()
            options.forEach { option ->
                WorkspaceFlatMenuRow(
                    title = option.title,
                    selected = selected == option.key,
                    onClick = { expanded = false; onSelected(option.key) },
                )
            }
        }
    }
}

/** Open With retains its own custom-type definitions and dedicated rules. */
@Composable
fun OpenPresetFilterMenu(
    selected: OpenPreset?,
    config: OpenTypeConfig,
    onSelected: (OpenPreset?) -> Unit,
    onManageCustom: () -> Unit,
    iconOnly: Boolean = false,
) {
    EntryTypeFilterMenu(
        selected = selected?.name,
        options = config.configuredPresets().map { preset ->
            EntryTypeMenuOption(preset.name, config.localizedTitle(preset))
        },
        onSelected = { key -> onSelected(key?.let(OpenPreset::valueOf)) },
        selectedTitle = selected?.let(config::localizedTitle) ?: stringResource(R.string.common_all),
        filterTitle = stringResource(R.string.open_type_filter),
        onManageTypes = onManageCustom,
        iconOnly = iconOnly,
    )
}
