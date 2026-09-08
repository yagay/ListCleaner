package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
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
            title = { Text(stringResource(R.string.root_permission_required)) },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = vm::dismissComponentRootNotice) {
                    Text(stringResource(R.string.root_permission_ack))
                }
            }
        )
    }

    var kind by remember { mutableStateOf<CleanupKind?>(null) }
    var viewFilter by remember { mutableStateOf(UiFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshComponents() }

    val visible = scan.items.filter {
        (kind == null || it.kind == kind) &&
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
                Text(stringResource(R.string.root_screen_title), style = MaterialTheme.typography.titleLarge)
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
                                        entry?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all),
                                        fontWeight = if (kind == entry) FontWeight.Bold else FontWeight.Normal,
                                        color = if (kind == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    Modifier.height(2.dp).width(24.dp).background(
                                        if (kind == entry) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.view_filter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            TextButton(onClick = { filterMenu = true }) {
                                Text(stringResource(viewFilter.titleRes()))
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                                UiFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(filter.titleRes())) },
                                        leadingIcon = { if (viewFilter == filter) Icon(Icons.Rounded.Check, null) },
                                        onClick = { viewFilter = filter; filterMenu = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { vm.changeComponents(visible, enable = false) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled == true }
                        ) { Text(stringResource(R.string.select_all)) }
                        TextButton(
                            onClick = { vm.invertComponents(visible) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null }
                        ) { Text(stringResource(R.string.invert_selection)) }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider()
                }
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.root_summary, groups.size, visible.size), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.root_filter_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.root_selection_semantics), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.root_change_semantics),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        when (kind) {
                            null -> R.string.root_kind_all_help
                            CleanupKind.TILE -> R.string.root_kind_tile_help
                            CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
                            CleanupKind.WIDGET -> R.string.root_kind_widget_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
                val kindTitles = CleanupKind.entries.filter { entry -> components.any { it.kind == entry } }
                    .map { stringResource(it.titleRes()) }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = expandLabel, onClick = onExpand)
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
                            stringResource(
                                R.string.root_disabled_summary,
                                components.count { it.enabled == false },
                                components.size,
                                kindTitles.joinToString(" · ")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (editableComponents.size < components.size) {
                            Text(
                                stringResource(R.string.root_display_only_count, components.size - editableComponents.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onExpand) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, expandLabel)
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
                            Text(stringResource(item.kind.titleRes()), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            item.component.flattenToShortString(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            item.blocked ?: stringResource(
                                when (item.overrideState) {
                                    0 -> if (item.enabled == true) R.string.root_default_enabled else R.string.root_default_disabled
                                    1 -> R.string.root_explicit_enabled
                                    2, 3, 4 -> R.string.root_disabled_unknown_source
                                    else -> R.string.root_unknown_state
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (visible.isEmpty() && !busy) {
            item { Text(stringResource(R.string.root_no_components), Modifier.padding(vertical = 16.dp)) }
        }
    }
}
