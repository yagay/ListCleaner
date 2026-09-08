package com.yagay.ListCleaner.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

@Composable
internal fun ScopeDialog(status: ModuleStatus, requestScope: () -> Unit, refresh: () -> Unit, dismiss: () -> Unit) {
    FullScreenDetails(
        onDismissRequest = dismiss,
        title = { Text("选择器作用域") },
        text = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("以下结果来自示例请求解析，不代表所有内容类型或厂商场景；不会打开应用。")
                Text("检测到的宿主", fontWeight = FontWeight.Bold)
                if (status.detection.hosts.isEmpty()) Text("未确认宿主。可能已有默认处理应用，或系统采用其他选择器实现。")
                status.detection.hosts.forEach { host ->
                    Text("${host.packageName}\n${host.className}\n进程：${host.processName}\n依据：${host.scenarios.joinToString("、")}")
                    if (host.requiresManualScope) Text("未确认兼容的宿主或进程：不会自动申请。请在 LSPosed 手动核查，不建议扩大到所有系统应用。", color = MaterialTheme.colorScheme.error)
                    else if (host.packageName == "android") Text("android 用于框架选择器界面兜底；system 用于系统服务全局查询过滤，两者不能互相替代。")
                }
                val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
                if (unconfirmed.isNotEmpty()) Text("仅检测到已安装，未确认宿主：\n${unconfirmed.joinToString("\n")}")
                Text("已授权范围", fontWeight = FontWeight.Bold)
                Text(if (!status.scopeKnown) "暂未读取到授权结果" else status.grantedScope.sorted().joinToString("\n").ifEmpty { "暂无" })
                if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                    Text("未被本次宿主检测确认的额外授权：\n${status.extraScope.sorted().joinToString("\n")}\n可能来自旧版或手动配置，已保留。请在 LSPosed 中核查，确认不需要后再移除。")
                }
                Text("运行目标", fontWeight = FontWeight.Bold)
                if (status.runningTargets.isEmpty()) Text("未读取到运行目标，不代表作用域一定错误。")
                status.runningTargets.forEach { target ->
                    val stateLabel = when (target.state) {
                        "UP_TO_DATE" -> "当前版本已加载"
                        "STALE" -> "仍加载旧版本"
                        "RELOADING" -> "正在重载"
                        "FAILED" -> "加载或重载失败"
                        else -> target.state
                    }
                    Text("${target.processName} · $stateLabel · 版本码 ${target.version}")
                }
                Text("模块加载不等于过滤成功。请实际打开分享或文件选择器核对规则；文本处理不保证覆盖应用内菜单。")
                status.detection.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                status.message?.let { Text(it) }
                status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = refresh, enabled = !status.requesting) { Text("重新检测") }
                Text("高级配置：在 LSPosed → 本模块 → 作用域中手动管理，不建议勾选所有应用。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = requestScope,
                enabled = status.connected && status.scopeKnown && !status.requesting && status.missingScope.isNotEmpty()
            ) {
                Text(when {
                    status.requesting -> "正在申请…"
                    status.detection.recommended.isEmpty() -> "无自动推荐目标"
                    status.scopeKnown && status.missingScope.isEmpty() -> "推荐范围已授权"
                    else -> "申请缺少的作用域（${status.missingScope.size}）"
                })
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenDetails(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
                              text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
                              dismissButton: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            TopAppBar(title = title, navigationIcon = {
                IconButton(onClick = onDismissRequest) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            })
        }, bottomBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End) { dismissButton(); confirmButton() }
            }
        }) { padding -> Box(Modifier.padding(padding).padding(horizontal = 16.dp)) { text() } }
    }
}
