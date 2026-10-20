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
        title = { AdaptiveText("选择备份空间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveText("可以同时选择多个空间，未勾选的空间不会导出。")
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
                            AdaptiveText("${space.title} · User ${space.userId}")
                        }
                    }
                }
                AdaptiveText("Root 组件禁用记录仅供迁移参考；导入不会自动执行禁用命令。")
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = { onExport(selected) }, enabled = selected.isNotEmpty()) {
                AdaptiveText("导出所选空间")
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) { AdaptiveText("取消") }
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
    var mapping by remember(inspection, selectedUserId) {
        mutableStateOf(
            if (inspection.version <= 12) mapOf(0 to selectedUserId) else emptyMap()
        )
    }
    val isValid = mapping.isNotEmpty() && mapping.values.toSet().size == mapping.size
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText("选择恢复目标空间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdaptiveText("逐项选择来源备份对应的目标空间。未选择的空间保持原样。")
                Column(
                    modifier = Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    inspection.spaces.forEach { source ->
                        var expanded by remember(source.sourceUserId) { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AdaptiveText("${source.sourceName} · 来源 User ${source.sourceUserId}")
                            AdaptiveText(
                                "${source.rules} 条规则 · ${source.rootDisabledRecords} 条 Root 禁用记录（仅参考）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box {
                                AdaptiveTextButton(onClick = { expanded = true }) {
                                    AdaptiveText(targets.firstOrNull {
                                        it.userId == mapping[source.sourceUserId]
                                    }?.let { "→ ${it.title} (User ${it.userId})" } ?: "跳过此空间 / 选择目标")
                                }
                                AdaptiveDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    AdaptiveDropdownMenuItem(
                                        text = { AdaptiveText("不导入此空间") },
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
                                            text = { AdaptiveText("${target.title} · User ${target.userId}") },
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
                AdaptiveText("导入将替换所选目标空间的规则、排序等配置；不会更改其他空间，也不会自动执行 Root 命令。")
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                enabled = isValid,
                onClick = { onImport(mapping.toMap()) },
            ) { AdaptiveText("恢复 ${mapping.size} 个空间") }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) { AdaptiveText("取消") }
        },
    )
}
