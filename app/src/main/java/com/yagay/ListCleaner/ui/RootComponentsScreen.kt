package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent

private fun componentAppSelectionRank(items: List<RootComponent>): Int {
    val editable = items.filter { it.blocked == null && it.enabled != null }
    val disabledCount = editable.count { it.enabled == false }
    return when {
        editable.isNotEmpty() && disabledCount == editable.size -> 0
        disabledCount > 0 -> 1
        else -> 2
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RootComponentsScreen(state: MainState, vm: MainViewModel) {
    val scan by vm.componentScan.collectAsState()
    val busy by vm.componentBusy.collectAsState()
    val message by vm.componentMessage.collectAsState()
    val rootNotice by vm.componentRootNotice.collectAsState()
    rootNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = vm::dismissComponentRootNotice,
            title = { Text("需要 Root 授权") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = vm::dismissComponentRootNotice) { Text("知道了") } }
        )
    }

    var kind by remember { mutableStateOf<CleanupKind?>(null) }
    var viewFilter by remember { mutableStateOf(UiFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var onlyModified by rememberSaveable { mutableStateOf(false) }
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshComponents() }

    val visible = scan.items.filter {
        (kind == null || it.kind == kind) &&
            (!onlyModified || it.modifiedByListCleaner) &&
            (when (viewFilter) {
                UiFilter.ALL -> true
                UiFilter.SHOW_SELECTED -> it.enabled == false
                UiFilter.HIDE_SELECTED -> it.enabled == true
            }) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    val groups = visible.groupBy { "${it.user}|${it.component.packageName}" }.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                .thenBy { it.value.first().owner.lowercase() }
                .thenBy { it.key }
        )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "title") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("组件清理 · Root", style = MaterialTheme.typography.titleLarge)
            }
        }
        stickyHeader(key = "controls") {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(listOf<CleanupKind?>(null) + CleanupKind.entries) { entry ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(onClick = { kind = entry }) {
                                    Text(
                                        entry?.title ?: "全部",
                                        fontWeight = if (kind == entry) FontWeight.Bold else FontWeight.Normal,
                                        color = if (kind == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    Modifier.height(2.dp).width(24.dp).background(
                                        if (kind == entry) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            TextButton(onClick = { filterMenu = true }) {
                                Text(viewFilter.title)
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                                UiFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.title) },
                                        leadingIcon = { if (viewFilter == filter) Icon(Icons.Rounded.Check, null) },
                                        onClick = { viewFilter = filter; filterMenu = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { vm.changeComponents(visible, enable = false) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null && it.enabled == true }
                        ) { Text("全选") }
                        TextButton(
                            onClick = { vm.invertComponents(visible) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null }
                        ) { Text("反选") }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("只看本应用修改", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = onlyModified, onCheckedChange = { onlyModified = it })
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider()
                }
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("应用列表 · ${groups.size} · ${visible.size} 个组件", style = MaterialTheme.typography.labelLarge)
                Text(
                    "计数仅针对当前可见组件；应用勾选仅操作其中可更改的组件，保护/未知状态项跳过。全部视图不会因勾选隐藏应用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "勾选＝组件已禁用。系统当前状态始终直接读取；“本应用修改”历史只在 Root 操作成功且系统状态核验成功后记录，用于筛选，不作为当前启用/禁用状态依据。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "勾选立即禁用，取消勾选立即明确启用，不再弹窗确认。禁用会影响正在使用此组件的功能；启用不是恢复原默认状态。清除应用数据不会改变系统组件状态，但会清除本应用修改历史。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (kind) {
                        null -> "仅管理标准磁贴服务、快捷方式创建入口及小部件接收器；不是禁用整个应用。"
                        CleanupKind.TILE -> "仅列出标准 TileService。Wi-Fi、蓝牙等没有独立服务的系统内置磁贴不支持；核心系统组件仅展示。"
                        CleanupKind.SHORTCUT -> "标准快捷方式创建 Activity；不含动态快捷方式或 ShortX 私有项目。禁用后可能影响其他使用该 Activity 的入口。"
                        CleanupKind.WIDGET -> "带小部件声明的接收器。禁用可能使已放置的小部件失效；启用不保证恢复原布局。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onlyModified) {
                    Text(
                        "当前只显示曾被 List Cleaner 成功修改过的组件；即使后来被其他工具再次改动，仍会保留历史标记。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (scan.warning.isNotBlank()) Text(scan.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        groups.forEach { (appKey, unsorted) ->
            val components = unsorted.sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }, { it.id }))
            val editableComponents = components.filter { it.blocked == null && it.enabled != null }
            val disabledCount = editableComponents.count { it.enabled == false }
            val selectionState = when {
                disabledCount == 0 -> ToggleableState.Off
                disabledCount == editableComponents.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            val first = components.first()
            val expansionKey = "${kind?.name ?: "ALL"}|$appKey"
            val expanded = expandedAppKey == expansionKey
            val onExpand = { expandedAppKey = if (expandedAppKey == expansionKey) null else expansionKey }

            item(key = "app|$expansionKey") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                        .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(
                        state = selectionState,
                        enabled = !busy && editableComponents.isNotEmpty(),
                        onClick = { vm.changeComponents(editableComponents, selectionState == ToggleableState.On) }
                    )
                    AppIcon(first.icon, first.owner)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(first.owner, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            "已禁用 ${components.count { it.enabled == false }}/${components.size} · ${components.map { it.kind.title }.distinct().joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val modifiedCount = components.count { it.modifiedByListCleaner }
                        if (modifiedCount > 0) {
                            Text(
                                "本应用修改历史 $modifiedCount 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (editableComponents.size < components.size) {
                            Text(
                                "${components.size - editableComponents.size} 项仅展示，不参与批量操作",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onExpand) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) "折叠" else "展开"
                        )
                    }
                }
                HorizontalDivider()
            }

            if (expanded) items(components, key = { it.id }) { item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .toggleable(
                            value = item.enabled == false,
                            enabled = editable,
                            role = Role.Checkbox,
                            onValueChange = { checked -> vm.changeComponent(item, !checked) }
                        )
                        .heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.enabled == false,
                        enabled = editable,
                        onCheckedChange = null,
                        modifier = Modifier.padding(12.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.kind.title, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            item.component.flattenToShortString(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            item.blocked ?: when (item.overrideState) {
                                0 -> if (item.enabled == true) "默认启用" else "默认关闭"
                                1 -> "明确启用"
                                2, 3, 4 -> "已禁用（来源未知）"
                                else -> "状态未知"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (item.modifiedByListCleaner) {
                            Text(
                                "曾由 List Cleaner 成功修改",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        if (visible.isEmpty() && !busy) {
            item {
                Text(
                    if (onlyModified) "没有符合条件的本应用修改历史" else "没有匹配的组件",
                    Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}
