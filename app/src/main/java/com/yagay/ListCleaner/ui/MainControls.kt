package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
    @Composable
    internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit) {
        val colors = MaterialTheme.colorScheme
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                .heightIn(min = 48.dp)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("搜索应用、组件或包名", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        innerTextField()
                    }
                }
            }
        )
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainToolbar(query: String, expanded: Boolean, onQuery: (String) -> Unit,
                         onSearch: () -> Unit, onClose: () -> Unit, onRefresh: () -> Unit,
                         onRestore: () -> Unit, onBackup: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else Text(androidx.compose.ui.res.stringResource(com.yagay.ListCleaner.R.string.app_name), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            if (expanded) IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "关闭搜索")
            }
        },
        actions = {
            if (expanded) {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "清空搜索") }
            } else {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索") }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "刷新") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("恢复备份") }, onClick = { menu = false; onRestore() })
                        DropdownMenuItem(text = { Text("导出备份") }, onClick = { menu = false; onBackup() })
                    }
                }
            }
        }
    )
}

@Composable
internal fun ListControls(state: MainState, onFilter: (IntentKind?) -> Unit, onUiFilter: (UiFilter) -> Unit,
                          includeAllKinds: Boolean = true, viewTitle: (UiFilter) -> String = { it.title },
                          onSelectAll: (() -> Unit)? = null, onInvert: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items((if (includeAllKinds) listOf<IntentKind?>(null) else emptyList()) + IntentKind.entries) { kind ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { onFilter(kind) }) {
                        Text(kind?.shortTitle ?: "全部",
                            fontWeight = if (state.filter == kind) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.filter == kind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.height(2.dp).width(24.dp).background(
                        if (state.filter == kind) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick = { menu = true }) {
                    Text(viewTitle(state.uiFilter))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    UiFilter.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(viewTitle(mode)) },
                            leadingIcon = { if (mode == state.uiFilter) Icon(Icons.Rounded.Check, null) },
                            onClick = { menu = false; onUiFilter(mode) })
                    }
                }
            }
            if (onSelectAll != null || onInvert != null) {
                Spacer(Modifier.weight(1f))
                onSelectAll?.let { action ->
                    TextButton(onClick = action) { Text("全选") }
                }
                onInvert?.let { action ->
                    TextButton(onClick = action) { Text("反选") }
                }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider()
    }
}

@Composable
internal fun ModuleStatusRow(state: MainState, compact: Boolean = false, onClick: () -> Unit) {
    val status = state.module
    val title = when {
        !status.connected -> "LSPosed 未连接"
        status.outdated -> "旧模块仍在运行／更新未完成"
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) -> "模块状态需要处理"
        status.resolverLoaded -> "检测到模块已加载"
        else -> "LSPosed 已连接"
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(if (!compact) state.syncStatus else if (state.runtime.ready) "系统已确认配置" else "需要检查配置，点击查看状态", style = MaterialTheme.typography.labelSmall,
                color = if (status.outdated || !state.runtime.ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ExpandMore, "查看模块状态")
    }
}
