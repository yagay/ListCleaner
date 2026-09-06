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

private const val SCOPE_TAG = "ListCleaner.Scope"

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

    resultDialog?.let { text ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            confirmButton = { TextButton(onClick = { resultDialog = null }) { Text("确定") } },
            title = { Text("LSPosed Hook 作用域") },
            text = { Text(text) },
        )
    }

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
                                color = if (it.startsWith("失败") || it.startsWith("未确认")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            onClick = {
                                if (pending.isEmpty() || requesting) return@Button
                                val requested = pending.sorted()
                                requesting = true
                                message = "正在请求 LSPosed 授权 ${requested.size} 个应用…"
                                Log.i(SCOPE_TAG, "SCOPE_REQUEST_START packages=$requested connected=${status.connected} scopeKnown=${status.scopeKnown}")
                                scope.launch {
                                    try {
                                        val service = app.service.value ?: error("未连接 LSPosed，请先启用模块")
                                        message = "等待 LSPosed 确认…如果没有出现框架授权窗口，请等待返回结果。"
                                        val approved = withTimeoutOrNull(120_000) {
                                            suspendCancellableCoroutine<List<String>> { continuation ->
                                                service.requestScope(requested, object : XposedService.OnScopeEventListener {
                                                    override fun onScopeRequestApproved(approved: List<String>) {
                                                        Log.i(SCOPE_TAG, "SCOPE_REQUEST_APPROVED requested=$requested approved=$approved")
                                                        if (continuation.isActive) continuation.resume(approved)
                                                    }

                                                    override fun onScopeRequestFailed(error: String) {
                                                        Log.e(SCOPE_TAG, "SCOPE_REQUEST_FAILED requested=$requested error=$error")
                                                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                                                    }
                                                })
                                            }
                                        }

                                        if (approved == null) {
                                            Log.w(SCOPE_TAG, "SCOPE_REQUEST_TIMEOUT requested=$requested")
                                            message = "未确认：等待 LSPosed 授权超时。"
                                            resultDialog = "等待 LSPosed 授权超时。\n\n如果始终没有出现 LSPosed 确认窗口，请检查框架是否阻止了该模块的动态作用域请求，然后导出诊断日志。"
                                            refresh()
                                            return@launch
                                        }

                                        if (approved.isEmpty()) {
                                            Log.w(SCOPE_TAG, "SCOPE_REQUEST_EMPTY_APPROVAL requested=$requested")
                                            message = "未确认：LSPosed 没有批准任何应用。"
                                            resultDialog = "LSPosed 已返回，但没有批准任何应用。\n\n如果没有出现授权窗口，动态作用域请求可能已被框架拒绝或屏蔽。"
                                            refresh()
                                            return@launch
                                        }

                                        message = "LSPosed 已返回授权结果，正在核验实际 Hook 列表…"
                                        val approvedSet = approved.toSet()
                                        var grantedNow = emptySet<String>()
                                        repeat(12) {
                                            grantedNow = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
                                            if (approvedSet.all { it in grantedNow }) return@repeat
                                            delay(250)
                                        }
                                        val verified = approvedSet.intersect(grantedNow)
                                        val missing = approvedSet - grantedNow
                                        Log.i(SCOPE_TAG, "SCOPE_VERIFY requested=$requested approved=$approved granted=$grantedNow verified=$verified missing=$missing")
                                        refresh()

                                        if (missing.isEmpty()) {
                                            onSelectedChange(selected - verified)
                                            message = "已核验：${verified.size} 个应用已加入 LSPosed Hook 列表。"
                                            resultDialog = "已成功加入 ${verified.size} 个应用到 LSPosed Hook 列表。\n\n目标应用需要完全结束进程后重新启动，新的 Hook 才会加载。"
                                        } else {
                                            onSelectedChange(selected - verified)
                                            message = "未确认：LSPosed 回调成功，但仍有 ${missing.size} 个应用未出现在实际作用域。"
                                            resultDialog = "LSPosed 返回了“已批准”，但二次读取实际 Hook 作用域后仍缺少：\n\n${missing.sorted().joinToString("\n")}\n\n这说明授权结果与实际作用域不同，请导出诊断日志继续分析。"
                                        }
                                    } catch (failure: Exception) {
                                        val detail = failure.message ?: failure.javaClass.simpleName
                                        Log.e(SCOPE_TAG, "SCOPE_REQUEST_EXCEPTION requested=$requested error=$detail", failure)
                                        message = "失败：$detail"
                                        resultDialog = "LSPosed 作用域申请失败：\n\n$detail\n\n如果点击后完全没有出现 LSPosed 授权窗口，这通常表示框架直接拒绝了动态作用域请求。请保留当前状态并导出诊断日志。"
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
                                requesting -> "等待 LSPosed 授权…"
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
                    "先勾选应用，再点击底部“加入 LSPosed Hook 列表”。提交后会等待框架结果，并再次读取实际作用域确认是否真的加入。",
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
