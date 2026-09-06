package com.yagay.ListCleaner.ui

// Scope requests selected here are paired with the third-party PackageManager query Hook.
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yagay.ListCleaner.ListCleanerApp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScopeAppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

@Suppress("DEPRECATION")
private fun loadScopeApps(pm: PackageManager, selfPackage: String): List<ScopeAppEntry> {
    val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getInstalledApplications(0)
    }
    return installed.asSequence()
        .filter { it.packageName != selfPackage && it.packageName != "android" }
        .map { info ->
            ScopeAppEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ScopeAppEntry> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScopePickerDialog(
    status: ModuleStatus,
    refresh: () -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ListCleanerApp
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var requesting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem) {
        val needle = query.trim().lowercase()
        apps.filter { appEntry ->
            (showSystem || !appEntry.system) &&
                (needle.isEmpty() || appEntry.label.lowercase().contains(needle) || appEntry.packageName.lowercase().contains(needle))
        }
    }
    val pending = selected - status.grantedScope

    Dialog(
        onDismissRequest = { if (!requesting) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("添加应用 Hook") },
                    navigationIcon = {
                        IconButton(onClick = dismiss, enabled = !requesting) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            onClick = {
                                if (pending.isEmpty() || requesting) return@Button
                                requesting = true
                                message = "正在请求 LSPosed 授权 ${pending.size} 个应用…"
                                scope.launch {
                                    try {
                                        val service = app.service.value ?: error("未连接 LSPosed，请先启用模块")
                                        val approved = withTimeoutOrNull(120_000) {
                                            suspendCancellableCoroutine<List<String>> { continuation ->
                                                service.requestScope(pending.sorted(), object : XposedService.OnScopeEventListener {
                                                    override fun onScopeRequestApproved(approved: List<String>) {
                                                        if (continuation.isActive) continuation.resume(approved)
                                                    }

                                                    override fun onScopeRequestFailed(error: String) {
                                                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                                                    }
                                                })
                                            }
                                        }
                                        refresh()
                                        message = if (approved == null) {
                                            "等待授权超时；申请可能仍在 LSPosed 中处理，请稍后刷新。"
                                        } else {
                                            "已提交并获批 ${approved.size} 个应用；目标应用需重新启动后才能加载 Hook。"
                                        }
                                    } catch (failure: Exception) {
                                        message = "失败：${failure.message ?: "无法申请作用域"}"
                                    } finally {
                                        requesting = false
                                    }
                                }
                            },
                            enabled = status.connected && status.scopeKnown && pending.isNotEmpty() && !requesting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (requesting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(when {
                                requesting -> "正在申请…"
                                pending.isEmpty() && selected.isNotEmpty() -> "所选应用已在 Hook 范围"
                                selected.isEmpty() -> "请选择应用"
                                else -> "加入 LSPosed Hook（${pending.size}）"
                            })
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Text(
                    "用于应用自己绘制的“打开方式 / 分享”列表。这里只向 LSPosed 申请作用域，不会修改应用数据。仅选择确实需要处理自定义列表的应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示系统应用", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Text(
                    "已选 ${selected.size} · 待加入 ${pending.size} · 当前已授权 ${status.grantedScope.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { entry ->
                            val granted = entry.packageName in status.grantedScope
                            val checked = granted || entry.packageName in selected
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !granted && !requesting) {
                                    selected = if (entry.packageName in selected) selected - entry.packageName else selected + entry.packageName
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = if (granted || requesting) null else { value ->
                                        selected = if (value) selected + entry.packageName else selected - entry.packageName
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, fontWeight = FontWeight.Medium)
                                    Text(entry.packageName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (granted) Text("已 Hook", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
