package com.yagay.ListCleaner.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.data.SpaceBackupInspection
import com.yagay.ListCleaner.data.UserSpace
import com.yagay.ListCleaner.R
import androidx.compose.ui.res.stringResource

/** Select one, several, or all Android users; nothing outside the selection is exported. */
@Composable
internal fun ExportSpaceBackupDialog(
    spaces: List<UserSpace>,
    onExport: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(spaces) { mutableStateOf(spaces.map { it.userId }.toSet()) }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.backup_spaces_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveText(stringResource(R.string.backup_spaces_export_help))
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    spaces.forEach { space ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = space.userId in selected,
                                onCheckedChange = { value ->
                                    selected = if (value) selected + space.userId
                                    else selected - space.userId
                                },
                            )
                            AdaptiveText(stringResource(R.string.backup_spaces_space_label, space.title, space.userId))
                        }
                    }
                }
                AdaptiveText(stringResource(R.string.backup_spaces_root_note))
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = { onExport(selected) }, enabled = selected.isNotEmpty()) {
                AdaptiveText(stringResource(R.string.backup_spaces_export_selected))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) { AdaptiveText(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Explicit source-to-target mapping. A source can be skipped and every Android target can only
 * be chosen once, preventing two backup profiles silently overwriting the same destination.
 */
@Composable
internal fun ImportSpaceBackupDialog(
    inspection: SpaceBackupInspection,
    targets: List<UserSpace>,
    selectedUserId: Int,
    onImport: (Map<Int, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var mapping: Map<Int, Int> by remember(inspection, selectedUserId) {
        mutableStateOf<Map<Int, Int>>(
            if (inspection.version <= 12) mapOf(0 to selectedUserId) else emptyMap()
        )
    }
    val isValid = mapping.isNotEmpty() && mapping.values.toSet().size == mapping.size
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.backup_spaces_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdaptiveText(stringResource(R.string.backup_spaces_import_help))
                Column(
                    modifier = Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    inspection.spaces.forEach { source ->
                        var expanded by remember(source.sourceUserId) { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AdaptiveText(stringResource(R.string.backup_spaces_source_label, source.sourceName, source.sourceUserId))
                            AdaptiveText(
                                stringResource(R.string.backup_spaces_rule_counts, source.rules, source.rootDisabledRecords),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box {
                                AdaptiveTextButton(onClick = { expanded = true }) {
                                    AdaptiveText(targets.firstOrNull {
                                        it.userId == mapping[source.sourceUserId]
                                    }?.let { stringResource(R.string.backup_spaces_target_label, it.title, it.userId) } ?: stringResource(R.string.backup_spaces_choose_target))
                                }
                                AdaptiveDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    AdaptiveDropdownMenuItem(
                                        text = { AdaptiveText(stringResource(R.string.backup_spaces_skip)) },
                                        onClick = {
                                            mapping = mapping - source.sourceUserId
                                            expanded = false
                                        },
                                    )
                                    targets.filter { target ->
                                        target.userId == mapping[source.sourceUserId] ||
                                            target.userId !in mapping.values
                                    }.forEach { target ->
                                        AdaptiveDropdownMenuItem(
                                            text = { AdaptiveText(stringResource(R.string.backup_spaces_space_label, target.title, target.userId)) },
                                            onClick = {
                                                mapping = mapping + (source.sourceUserId to target.userId)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                AdaptiveText(stringResource(R.string.backup_spaces_import_warning))
                AdaptiveText(
                    stringResource(R.string.backup_root_restore_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                enabled = isValid,
                onClick = { onImport(mapping.toMap()) },
            ) { AdaptiveText(stringResource(R.string.backup_spaces_import_count, mapping.size)) }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) { AdaptiveText(stringResource(R.string.common_cancel)) }
        },
    )
}
