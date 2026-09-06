package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset

@Composable
fun DefaultOpenScreen(state: MainState, vm: MainViewModel) {
    var choosing by remember { mutableStateOf<OpenPreset?>(null) }
    val rulesById = remember(state.candidates) { state.candidates.associateBy { it.rule.id } }

    choosing?.let { preset ->
        PreferredComponentDialog(
            preset = preset,
            candidates = state.candidates.filter {
                it.isCatalogCandidate && if (preset == OpenPreset.BROWSER) it.rule.kind == IntentKind.BROWSER else it.rule.kind == IntentKind.OPEN
            }.distinctBy { it.rule.id },
            currentId = state.defaultOpen.preferred[preset],
            onSelect = { vm.setDefaultOpen(preset, it?.id); choosing = null },
            onDismiss = { choosing = null }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("默认打开", style = MaterialTheme.typography.titleLarge)
                Text("当前先实现安全的“首选”模式：按实际 Intent 的 MIME / 链接类型，把指定组件提升到候选列表前面；不会注入不支持该类型的组件，也不会直接跳过系统选择器。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("如果目标应用已卸载、被规则隐藏或本次查询没有返回该组件，则保持 Android 原结果。厂商选择器若在查询后自行重排，最终位置可能不同。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.displayMode == DisplayMode.SHOW_ALL) {
                    Text("“全部显示”是全局暂停模式，默认打开首选也暂不应用；配置仍会保留。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(OpenPreset.entries, key = { it.name }) { preset ->
            val id = state.defaultOpen.preferred[preset]
            val known = id?.let(rulesById::get)
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().clickable { choosing = preset }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.title, fontWeight = FontWeight.Medium)
                        Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (id == null) {
                            Text("跟随系统", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(known?.let { "首选：${it.appLabel} · ${it.activityLabel}" } ?: "首选：${ComponentRule.fromId(id)?.packageName ?: id}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = { choosing = preset }) { Text(if (id == null) "选择" else "更换") }
                }
            }
        }
    }
}

@Composable
private fun PreferredComponentDialog(
    preset: OpenPreset,
    candidates: List<ComponentCandidate>,
    currentId: String?,
    onSelect: (ComponentRule?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${preset.title} · 首选应用") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                item {
                    Text("这里只选择实际查询结果中的首选组件；不会强行让不支持该文件类型的应用接管 Intent。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onSelect(null) }, enabled = currentId != null) { Text("清除首选，跟随系统") }
                }
                items(candidates.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.activityLabel.lowercase() })), key = { it.rule.id }) { item ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(item.rule) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(item.appIcon, item.appLabel)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(item.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        RadioButton(selected = item.rule.id == currentId, onClick = { onSelect(item.rule) })
                    }
                }
                if (candidates.isEmpty()) item { Text("当前扫描没有可选择的组件。", Modifier.padding(vertical = 16.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
