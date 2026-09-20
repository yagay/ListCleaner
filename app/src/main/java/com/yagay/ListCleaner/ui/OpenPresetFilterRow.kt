package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
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
    onManageCustom: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScroll = rememberScrollState()
    val selectedTitle = if (selected == null) stringResource(R.string.common_all) else config.localizedTitle(selected)

    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text(
                stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.open_type_filter),
                    selectedTitle
                ),
                modifier = Modifier.widthIn(max = 150.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.ExpandMore, null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
            scrollState = menuScroll
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_all)) },
                leadingIcon = { if (selected == null) Icon(Icons.Rounded.Check, null) },
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_manage_custom)) },
                onClick = {
                    expanded = false
                    onManageCustom()
                }
            )
            HorizontalDivider()
            config.configuredPresets().forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Text(
                            config.localizedTitle(preset),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { if (selected == preset) Icon(Icons.Rounded.Check, null) },
                    onClick = {
                        expanded = false
                        onSelected(preset)
                    }
                )
            }
        }
    }
}
