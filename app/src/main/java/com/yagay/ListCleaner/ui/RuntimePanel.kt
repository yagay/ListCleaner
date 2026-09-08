package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RuntimePanel(state: MainState, vm: MainViewModel, showUpdateTools: Boolean = true) {
    val updating by vm.updating.collectAsState()
    val result by vm.updateMessage.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("重置远程清理配置？") },
        text = { Text("将清空 Intent 规则、排序及磁贴清理配置，并暂停过滤。旧 Hook 未退出时，系统效果仍可能保留；必须等待系统确认。如需保留旧规则，请取消，先恢复远程配置并导出 JSON 备份。") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resolveRecovery(false) }) { Text("确认重置") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } }
    )
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.runtime.ready) {
            Text(UiText.translate(state.runtime.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showUpdateTools) Text("验证失败时不更新扫描结果；已选规则仍可管理。", style = MaterialTheme.typography.labelSmall)
        }
        if (state.runtime.needsDecision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.resolveRecovery(true) }, enabled = !state.runtime.recoveryCorrupt) { Text("恢复远程配置") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("重置并暂停过滤") }
            }
        }
        if (showUpdateTools) OutlinedButton(onClick = vm::applyModuleUpdate, enabled = state.module.connected && !updating) {
            Text(if (updating) "正在检查更新…" else "检测并应用模块更新")
        }
        if (showUpdateTools) result?.let { Text(UiText.translate(it), style = MaterialTheme.typography.bodySmall) }
    }
}
