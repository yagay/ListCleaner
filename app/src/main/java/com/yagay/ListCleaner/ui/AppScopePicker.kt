package com.yagay.ListCleaner.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScopeAppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val icon: Bitmap?,
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
                icon = runCatching { pm.getApplicationIcon(info).toBitmap(64, 64) }.getOrNull(),
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ScopeAppEntry> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScopePickerDialog(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.label.lowercase().contains(needle) || entry.packageName.lowercase().contains(needle))
        }
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("应用隐藏列表") },
                    navigationIcon = { IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Text(
                    "这里选择的是“哪些来源应用需要打开方式兼容隐藏”。仅在“隐藏选中”模式生效；目标范围只跟随“打开方式”通用规则和 PDF、APK、视频等分类型 OPEN 规则，不再因为分享或文本处理规则而隐藏整个目标应用。该功能仍是 system/system_server 侧的包级隐藏，适合 ES 等自己查询应用列表的文件管理器。勾选立即保存。",
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
                    "显示 ${visible.size}/${apps.size} 个应用 · 已加入隐藏列表 ${selected.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { entry ->
                            val checked = entry.packageName in selected
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onSelectedChange(if (checked) selected - entry.packageName else selected + entry.packageName)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                entry.icon?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { value ->
                                        onSelectedChange(if (value) selected + entry.packageName else selected - entry.packageName)
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, fontWeight = FontWeight.Medium)
                                    Text(entry.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (checked) Text("已加入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
