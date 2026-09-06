package com.yagay.ListCleaner.ui

// Scope requests selected here are paired with the third-party PackageManager query Hook.
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
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
import kotlinx.coroutines.delay
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
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    refresh: () -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ListCleanerApp
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var requesting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<String?>(null) }

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

    resultDialog?.let { text ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            confirmButton = { TextButton(onClick = { resultDialog = null }) { Text("确定") } },
            title = { Text(if (text.startsWith("成功")) "LSPosed Hook 列表" else "加入 Hook 失败") },
            text = { Text(text) },
        )
    }

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
                                color = if (it.startsWith("失败") || it.startsWith("未加入")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            onClick = {
                                Log.i("ListCleaner", "SCOPE_BUTTON_CLICK selected=${selected.sorted()} pending=${pending.sorted()} connected=${status.connected} scopeKnown=${status.scopeKnown} requesting=$requesting granted=${status.grantedScope.sorted()}")
                                if (pending.isEmpty()) {
                                    message = "未加入：当前没有待提交应用。"
                                    Log.w("ListCleaner", "SCOPE_BUTTON_BLOCKED reason=no_pending")
                                    return@Button
                                }
                                if (requesting) {
                                    Log.w("ListCleaner", "SCOPE_BUTTON_BLOCKED reason=request_in_flight")
                                    return@Button
                                }
                                if (!status.connected) {
                                    message = "失败：未连接 LSPosed。"
                                    resultDialog = message
                                    Log.w("ListCleaner", "SCOPE_BUTTON_BLOCKED reason=service_disconnected")
                                    return@Button
                                }
                                if (!status.scopeKnown) {
                                    message = "失败：LSPosed 作用域状态尚未读取完成，请刷新后重试。"
                                    resultDialog = message
                                    Log.w("ListCleaner", "SCOPE_BUTTON_BLOCKED reason=scope_unknown")
                                    return@Button
                                }

                                val requested = pending.sorted()
                                requesting = true
                                message = "正在请求 LSPosed 授权 ${requested.size} 个应用…"
                                Log.i("ListCleaner", "SCOPE_REQUEST_START packages=$requested")
                                scope.launch {
                                    try {
                                        val service = app.service.value ?: error("未连接 LSPosed，请先启用模块")
                                        val approved = withTimeoutOrNull(120_000) {
                                            suspendCancellableCoroutine<List<String>> { continuation ->
                                                service.requestScope(requested, object : XposedService.OnScopeEventListener {
                                                    override fun onScopeRequestApproved(approved: List<String>) {
                                                        Log.i("ListCleaner", "SCOPE_REQUEST_APPROVED requested=$requested approved=${approved.sorted()}")
                                                        if (continuation.isActive) continuation.resume(approved)
                                                    }

                                                    override fun onScopeRequestFailed(error: String) {
                                                        Log.e("ListCleaner", "SCOPE_REQUEST_FAILED requested=$requested error=$error")
                                                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                                                    }
                                                })
                                            }
                                        }
                                        if (approved == null) {
                                            Log.e("ListCleaner", "SCOPE_REQUEST_TIMEOUT requested=$requested")
                                            message = "失败：等待 LSPosed 授权超过 120 秒。若没有出现 LSPosed 授权窗口，请检查框架是否阻止了动态作用域请求。"
                                            resultDialog = message
                                            refresh()
                                            return@launch
                                        }

                                        // Give the service a brief moment to publish the updated scope, then verify it directly.
                                        var grantedNow = service.scope.toSet()
                                        repeat(5) {
                                            if (requested.all { it in grantedNow }) return@repeat
                                            delay(200)
                                            grantedNow = service.scope.toSet()
                                        }
                                        val actuallyGranted = requested.filter { it in grantedNow }
                                        val missing = requested.filterNot { it in grantedNow }
                                        Log.i("ListCleaner", "SCOPE_VERIFY requested=$requested callbackApproved=${approved.sorted()} granted=${grantedNow.sorted()} missing=$missing")
                                        refresh()

                                        if (missing.isEmpty()) {
                                            onSelectedChange(selected - actuallyGranted.toSet())
                                            message = "已加入 ${actuallyGranted.size} 个应用到 LSPosed Hook 列表。"
                                            resultDialog = "成功加入 ${actuallyGranted.size} 个应用到 LSPosed Hook 列表。\n\n目标应用需要完全结束进程后重新启动，新的 Hook 才会加载。"
                                        } else {
                                            message = "失败：LSPosed 回调完成，但 ${missing.size} 个应用未出现在实际作用域中。"
                                            resultDialog = "LSPosed 已返回授权结果，但以下应用未真正加入作用域：\n\n${missing.joinToString("\n")}\n\n请导出诊断包；日志中会包含 SCOPE_VERIFY。"
                                        }
                                    } catch (failure: Exception) {
                                        val detail = failure.message ?: failure.javaClass.simpleName
                                        Log.e("ListCleaner", "SCOPE_REQUEST_EXCEPTION requested=$requested error=$detail", failure)
                                        message = "失败：$detail"
                                        resultDialog = "LSPosed 作用域请求失败：\n\n$detail\n\n如果没有出现 LSPosed 授权窗口，请导出诊断包查看 SCOPE_REQUEST_FAILED / SCOPE_REQUEST_EXCEPTION。"
                                    } finally {
                                        requesting = false
                                    }
                                }
                            },
                            enabled = pending.isNotEmpty() && !requesting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (requesting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(when {
                                requesting -> "正在等待 LSPosed…"
                                pending.isEmpty() && selected.isNotEmpty() -> "所选应用已在 Hook 列表"
                                selected.isEmpty() -> "请选择应用"
                                else -> "加入 LSPosed Hook 列表（${pending.size}）"
                            })
                        }
                        if (selected.isNotEmpty() && !requesting) {
                            TextButton(
                                onClick = { onSelectedChange(emptySet()) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("清空未提交选择") }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Text(
                    "先勾选应用，再点击底部“加入 LSPosed Hook 列表”。返回不会丢失尚未提交的勾选；重新进入后仍会保留。",
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
                                    onSelectedChange(if (entry.packageName in selected) selected - entry.packageName else selected + entry.packageName)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = if (granted || requesting) null else { value ->
                                        onSelectedChange(if (value) selected + entry.packageName else selected - entry.packageName)
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, fontWeight = FontWeight.Medium)
                                    Text(entry.packageName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (granted) Text("已 Hook", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                else if (entry.packageName in selected) Text("待加入", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
