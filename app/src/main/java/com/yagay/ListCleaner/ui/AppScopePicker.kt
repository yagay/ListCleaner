package com.yagay.ListCleaner.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.domain.VisibilityScope
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
    val app = context.applicationContext as ListCleanerApp
    val visibilityScopes by app.rules.visibilityScopes.collectAsState()
    val fullPackages by app.rules.visibilityFullPackages.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem, selected) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.label.lowercase().contains(needle) || entry.packageName.lowercase().contains(needle))
        }.sortedWith(
            compareBy<ScopeAppEntry> { if (it.packageName in selected) 0 else 1 }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )
    }
    val activeTargets = remember(visibilityScopes, fullPackages) {
        com.yagay.ListCleaner.domain.VisibilityCompatConfig(visibilityScopes, fullPackages).activePackages()
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
                    navigationIcon = { IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, UiText.translate("返回")) } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                Text(
                    "这里选择“哪些来源应用”需要包级隐藏兼容。下面的“命中分类”决定哪些规则分类可以贡献目标应用；默认不选择任何分类，因此不会自动启用兼容隐藏。仅在“隐藏选中”模式生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("命中分类（可多选）", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(VisibilityScope.entries, key = { it.name }) { scope ->
                        val checked = scope in visibilityScopes
                        FilterChip(
                            selected = checked,
                            onClick = {
                                app.rules.setVisibilityScopes(
                                    if (checked) visibilityScopes - scope else visibilityScopes + scope
                                )
                            },
                            label = { Text(scope.title) },
                        )
                    }
                }
                Text(
                    if (visibilityScopes.isEmpty()) {
                        "当前未选择分类：应用隐藏兼容不会隐藏任何目标应用。"
                    } else {
                        "只使用所选分类中“应用整行完整勾选”的目标；半勾选（只选部分组件）不加入。当前可命中 ${activeTargets.size} 个目标包。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索来源应用或包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示系统应用", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Text(
                    "显示 ${visible.size}/${apps.size} 个来源应用 · 已加入 ${selected.size} 个",
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
