package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.priorityCandidates
import com.yagay.ListCleaner.domain.priorityAppGroups
import com.yagay.ListCleaner.domain.PriorityListFilter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriorityDialogContent(state: MainState, vm: MainViewModel) {
    var kind by rememberSaveable { mutableStateOf(state.filter ?: IntentKind.SHARE) }
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val rankedRaw = state.priorities.apps[kind].orEmpty()
    val groups = remember(state.candidates, state.selected, state.displayMode, kind, rankedRaw, state.query, viewFilter) {
        priorityAppGroups(state.candidates, state.selected, state.displayMode, kind, rankedRaw, state.query,
            when (viewFilter) {
                UiFilter.ALL -> PriorityListFilter.ALL
                UiFilter.HIDE_SELECTED -> PriorityListFilter.UNSELECTED
                UiFilter.SHOW_SELECTED -> PriorityListFilter.SELECTED
            })
    }
    // Search narrows the move targets too; hidden saved slots remain untouched.
    val moveTargets = groups.filter { it.rank != null }.sortedBy { it.rank }.map { it.packageName }
    val visibleSaved = priorityCandidates(state.candidates, state.selected, state.displayMode, kind)
        .map { it.rule.packageName }.toSet()
    val hiddenSavedCount = rankedRaw.count { it !in visibleSaved }

    val listState = rememberLazyListState()
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val currentSaved by rememberUpdatedState(rankedRaw)
    val currentVisible by rememberUpdatedState(moveTargets)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 640.dp.toPx() }
    // Changing category/search/filter, importing or refreshing invalidates the gesture snapshot.
    LaunchedEffect(kind, viewFilter, state.query, rankedRaw, moveTargets) { dragState.cancel() }
    DisposableEffect(dragState) { onDispose { dragState.cancel() } }
    LaunchedEffect(drag?.packageName) {
        if (dragState.session != null) {
            var previous = withFrameNanos { it }
            while (dragState.session != null) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val delta = dragState.scrollSpeed(edge, speed) * seconds
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    dragState.retarget()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().pointerInput(kind, viewFilter, state.query) {
        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                if (dragState.start(position.y, kind.name, currentVisible, currentSaved)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, amount ->
                if (dragState.session != null) { change.consume(); dragState.move(amount.y) }
            },
            onDragCancel = { dragState.cancel() },
            onDragEnd = {
                dragState.finish()?.let { finished ->
                    vm.movePriorityTo(kind, finished.packageName, finished.target, finished.visible, finished.saved)
                }
            }
        )
    }, state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "status") {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        stickyHeader(key = "controls") {
            Surface(tonalElevation = 2.dp) {
                ListControls(state.copy(filter = kind, uiFilter = viewFilter),
                    onFilter = { entry -> if (entry != null) { kind = entry; expandedKey = null } },
                    onUiFilter = { viewFilter = it }, includeAllKinds = false,
                    viewTitle = { when (it) {
                        UiFilter.ALL -> "全部"
                        UiFilter.HIDE_SELECTED -> "未优先"
                        UiFilter.SHOW_SELECTED -> "已优先"
                    } },
                    onSelectAll = { vm.selectPriorityApps(kind, groups.map { it.packageName }) },
                    onInvert = { vm.invertPriorityApps(kind, groups.map { it.packageName }) })
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("应用列表 · ${groups.size}", style = MaterialTheme.typography.labelLarge)
                Text("勾选后按优先位置排列，取消后回到未优先应用的默认名称顺序。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("长按已优先应用可拖动排序，松手保存；展开后也可上移、下移。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("按分类保存。规则要求清理的组件不显示；应用仍有可见组件时保留。暂时隐藏的排序配置不删除，取消清理后恢复。分享适配推荐区及全部应用区，文本处理在查询出口排序；未知厂商菜单可能另行重排。联系人和调用方专属入口不调整。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val compatibility = when {
                    !state.module.connected -> "LSPosed 未连接：可以保存，但尚未生效。"
                    state.module.detection.hosts.isEmpty() -> "未确认选择器宿主，当前设备排序能力未知。"
                    state.module.detection.hosts.any { it.packageName != "system" && !it.className.startsWith("com.android.internal.app.") && !it.className.startsWith("com.android.intentresolver.") } ->
                        "厂商独立排序路径尚未适配；仅走 AOSP 路径时可能有效，不能保证置顶。"
                    else -> "已实现 AOSP 路径适配，未确认本机命中。保存后重新打开选择器核对。"
                }
                Text(if (!state.runtime.ready) state.runtime.message else if (kind == IntentKind.PROCESS_TEXT)
                    "文本候选按查询结果排序；来源应用若自行重排，仍可能不同。保存后重新打开文本菜单。" else compatibility,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hiddenSavedCount > 0) Text("$hiddenSavedCount 项因已清理或本次未匹配而暂不显示，排序配置保留。",
                    style = MaterialTheme.typography.bodySmall)
                if (rankedRaw.size >= 200) Text("当前分类已达 200 项上限，取消部分优先后可继续添加。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (state.error != null) Text("本次刷新未完整完成，详情见状态页", color = MaterialTheme.colorScheme.error)
            }
        }
        groups.forEach { group ->
            val packageName = group.packageName
            val key = "${kind.name}|$packageName"
            val expanded = expandedKey == key
            val onExpand = { expandedKey = if (expanded) null else key }
            val first = group.components.first()
            item(key = "app|$key", contentType = "app") {
                val marker = MaterialTheme.colorScheme.primary
                Row(Modifier.fillMaxWidth()
                    .alpha(if (drag?.packageName == packageName) 0.3f else 1f)
                    .drawWithContent {
                        drawContent()
                        if (drag?.target == packageName && drag.packageName != packageName) {
                            val y = if (drag.movingDown) size.height - 2.dp.toPx() else 2.dp.toPx()
                            drawLine(marker, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                        }
                    }
                    .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                    .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = group.rank != null, enabled = group.rank != null || rankedRaw.size < 200,
                        onCheckedChange = { checked ->
                            if (checked) vm.pinApp(kind, packageName) else vm.removePriority(kind, packageName)
                        })
                    AppIcon(first.appIcon, first.appLabel)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text((group.rank?.let { "优先第 $it 位" } ?: "未优先") + " · ${group.components.size} 个组件",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onExpand) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) "折叠" else "展开")
                    }
                }
                HorizontalDivider()
            }
            if (expanded) {
                if (group.rank != null) item(key = "order|$key") {
                    val index = moveTargets.indexOf(packageName)
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("优先第 ${group.rank} 位", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { vm.movePriority(kind, packageName, -1, moveTargets) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)); Text("上移")
                        }
                        TextButton(onClick = { vm.movePriority(kind, packageName, 1, moveTargets) },
                            enabled = index >= 0 && index < moveTargets.lastIndex) {
                            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)); Text("下移")
                        }
                    }
                }
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { ComponentInfoRow(it) }
            }
        }
        if (!state.loading && groups.isEmpty()) item(key = "empty") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(when {
                    state.query.isNotBlank() -> "没有匹配的应用"
                    viewFilter == UiFilter.SHOW_SELECTED -> "当前没有可显示的优先应用"
                    viewFilter == UiFilter.HIDE_SELECTED -> "当前没有可显示的未优先应用"
                    else -> "当前分类没有可排序的应用"
                })
            }
        }
    }
    drag?.let { moving ->
        groups.firstOrNull { it.packageName == moving.packageName }?.let { group ->
            val first = group.components.first()
            Surface(Modifier.fillMaxWidth().offset { IntOffset(0, moving.top.roundToInt()) }.zIndex(1f),
                tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(first.appIcon, first.appLabel)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("松手移动到优先第 ${groups.firstOrNull { it.packageName == moving.target }?.rank} 位",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ComponentInfoRow(item: ComponentCandidate) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .heightIn(min = 48.dp).padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
