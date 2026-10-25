package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig

/** Compact OPEN-type selector shown inside the shared top filter row. */
@Composable
fun OpenPresetFilterMenu(
    selected: OpenPreset?,
    config: OpenTypeConfig,
    onSelected: (OpenPreset?) -> Unit,
    onManageCustom: () -> Unit,
    iconOnly: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScroll = rememberScrollState()
    val selectedTitle = if (selected == null) stringResource(R.string.common_all) else config.localizedTitle(selected)

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
                        stringResource(R.string.open_type_filter) + ": " + selectedTitle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            CompactCategoryButton(
                selected = true,
                onClick = { expanded = true }
            ) {
                WorkspaceControlLabel(
                    stringResource(
                        R.string.compact_filter_format,
                        stringResource(R.string.open_type_filter),
                        selectedTitle
                    ),
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
            scrollState = menuScroll
        ) {
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { expanded = false; onSelected(null) }
            )
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.open_manage_custom),
                selected = false,
                onClick = { expanded = false; onManageCustom() }
            )
            AdaptiveHorizontalDivider()
            config.configuredPresets().forEach { preset ->
                WorkspaceFlatMenuRow(
                    title = config.localizedTitle(preset),
                    selected = selected == preset,
                    onClick = { expanded = false; onSelected(preset) }
                )
            }
        }
    }
}
