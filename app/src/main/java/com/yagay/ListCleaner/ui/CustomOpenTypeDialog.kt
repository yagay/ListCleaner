package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig

@Composable
internal fun CustomOpenTypeDialog(
    config: OpenTypeConfig,
    onSave: (OpenPreset, CustomOpenDefinition?) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<OpenPreset?>(null) }
    val preset = editing
    if (preset != null) {
        CustomOpenTypeEditor(
            preset = preset,
            initial = config.customDefinitions[preset],
            onSave = { definition -> onSave(preset, definition); editing = null },
            onDelete = if (preset in config.customDefinitions) ({ onSave(preset, null); editing = null }) else null,
            onDismiss = { editing = null }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义打开方式类型") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "最多 8 个自定义类型。每个类型可以按 MIME 和文件后缀识别，并拥有自己的规则与排序；未设置专用规则/排序时仍继承“打开方式 · 全部”。删除类型会同时清理该类型的专用规则和排序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(OpenPreset.CUSTOM_SLOTS, key = { it.name }) { slot ->
                        val definition = config.customDefinitions[slot]
                        ListItem(
                            headlineContent = { Text(definition?.title ?: slot.title, fontWeight = if (definition != null) FontWeight.Medium else FontWeight.Normal) },
                            supportingContent = {
                                Text(if (definition == null) "未配置" else buildString {
                                    if (definition.mimeTypes.isNotEmpty()) append("MIME ${definition.mimeTypes.joinToString()}")
                                    if (definition.mimeTypes.isNotEmpty() && definition.extensions.isNotEmpty()) append(" · ")
                                    if (definition.extensions.isNotEmpty()) append("后缀 ${definition.extensions.joinToString { ".$it" }}")
                                })
                            },
                            trailingContent = { TextButton(onClick = { editing = slot }) { Text(if (definition == null) "添加" else "编辑") } }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun CustomOpenTypeEditor(
    preset: OpenPreset,
    initial: CustomOpenDefinition?,
    onSave: (CustomOpenDefinition) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var mimeText by remember(initial) { mutableStateOf(initial?.mimeTypes?.sorted()?.joinToString("\n").orEmpty()) }
    var extensionText by remember(initial) { mutableStateOf(initial?.extensions?.sorted()?.joinToString("\n").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseSet(text: String): Set<String> = text
        .split(',', ';', '\n', '\r', '\t', ' ')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 ${preset.title}" else "编辑 ${initial.title}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    mimeText, { mimeText = it; error = null },
                    label = { Text("MIME（每行或逗号分隔）") },
                    supportingText = { Text("例如 application/vnd.amazon.ebook 或 application/*") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                OutlinedTextField(
                    extensionText, { extensionText = it; error = null },
                    label = { Text("文件后缀（每行或逗号分隔）") },
                    supportingText = { Text("例如 azw3, mobi, m3u8；可写或不写开头的点") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { delete -> TextButton(onClick = delete) { Text("删除", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = {
                    runCatching {
                        CustomOpenDefinition(title, parseSet(mimeText), parseSet(extensionText)).validated()
                    }.onSuccess(onSave).onFailure { error = it.message ?: "配置无效" }
                }) { Text("保存") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
