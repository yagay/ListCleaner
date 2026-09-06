package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.ComponentCandidate

@Composable
internal fun ComponentTitleDialog(
    item: ComponentCandidate,
    currentTitle: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(item.rule.id) { mutableStateOf(currentTitle.orEmpty()) }
    LaunchedEffect(currentTitle, item.rule.id) { text = currentTitle.orEmpty() }
    val trimmed = text.trim()
    val normalizedCurrent = currentTitle.orEmpty()
    val changed = trimmed != normalizedCurrent
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义显示名称") },
        text = {
            Column {
                Text("原名称：${item.activityLabel}", style = MaterialTheme.typography.bodyMedium)
                Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(androidx.compose.ui.Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 64 && it.none(Char::isISOControl)) text = it },
                    label = { Text("菜单显示名称") },
                    supportingText = { Text("${text.length}/64 · 留空保存可恢复原名称") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed.ifEmpty { null }); onDismiss() }, enabled = changed) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
