package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.domain.AppTypeFilter

private data class PendingRootBatch(val targets: List<RootComponent>, val enable: Boolean?)

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
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    val message by vm.componentMessage.collectAsStateWithLifecycle()
    val batchProgress by vm.componentBatchProgress.collectAsStateWithLifecycle()
    val rootNotice by vm.componentRootNotice.collectAsStateWithLifecycle()
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
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var appTypeMenu by remember { mutableStateOf(false) }
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingBatch by remember { mutableStateOf<PendingRootBatch?>(null) }
    pendingBatch?.let { batch ->
        val eligible = batch.targets.filter { item ->
            item.blocked == null && item.enabled != null &&
                !vm.isComponentBulkProtected(kind, item) &&
                (batch.enable == null || item.enabled != batch.enable)
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        val action = stringResource(when (batch.enable) {
            true -> R.string.root_batch_action_enable
            false -> R.string.root_batch_action_disable
            null -> R.string.root_batch_action_invert
        })
        AlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = { Text(stringResource(R.string.root_batch_confirm_title)) },
            text = { Text(stringResource(R.string.root_batch_confirmation_text, action)) },
            confirmButton = {
                TextButton(enabled = eligible.isNotEmpty() && !busy, onClick = {
                    pendingBatch = null
                    if (batch.enable == null) vm.invertComponentsBulk(kind, eligible)
                    else vm.changeComponentsBulk(kind, eligible, batch.enable)
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatch = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    LaunchedEffect(Unit) { vm.refreshComponents() }

    val kindItems = scan.items.filter { kind == null || it.kind == kind }
    val lockItemsByApp = remember(kindItems, bulkLockRevision) {
        kindItems.groupBy(::componentBulkLockAppId)
    }
    val scopeItems = kindItems.filter { appTypeFilter.matches(it.appType) }
    val baseVisible = scopeItems.filter {
        (when (viewFilter) {
                UiFilter.ALL, UiFilter.LOCKED -> true
                UiFilter.SHOW_SELECTED -> it.enabled == false
                UiFilter.HIDE_SELECTED -> it.enabled == true
            }) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    val lockVisible = if (viewFilter == UiFilter.LOCKED) {
        baseVisible.filter { vm.isComponentBulkProtected(kind, it) }
    } else baseVisible
    val baseGroups = lockVisible.groupBy { "${it.user}|${it.component.packageName}" }.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                .thenBy { it.value.first().owner.lowercase() }
                .thenBy { it.key }
        )
    val groups = baseGroups
    val visible = groups.flatMap { it.value }

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
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            TextButton(
                                onClick = { appTypeMenu = true },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.compact_filter_format,
                                        stringResource(R.string.app_type_filter),
                                        stringResource(appTypeFilter.titleRes())
                                    )
                                )
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = appTypeMenu, onDismissRequest = { appTypeMenu = false }) {
                                AppTypeFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(filter.titleRes())) },
                                        leadingIcon = { if (appTypeFilter == filter) Icon(Icons.Rounded.Check, null) },
                                        onClick = { appTypeFilter = filter; appTypeMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            TextButton(
                                onClick = { filterMenu = true },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.compact_filter_format,
                                        stringResource(R.string.view_filter),
                                        stringResource(viewFilter.titleRes())
                                    )
                                )
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
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
                        TextButton(
                            onClick = { pendingBatch = PendingRootBatch(visible, false) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled == true },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(stringResource(R.string.select_all)) }
                        TextButton(
                            onClick = { pendingBatch = PendingRootBatch(visible, null) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(stringResource(R.string.invert_selection)) }
                    }
                    if (batchProgress != null) {
                        val current = requireNotNull(batchProgress)
                        LinearProgressIndicator(
                            progress = { current.completed.toFloat() / current.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.root_batch_processing),
                                style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = vm::cancelComponentBatch) {
                                Text(stringResource(R.string.root_batch_stop_remaining))
                            }
                        }
                    } else if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider()
                }
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(stringResource(if (detailsExpanded) R.string.root_help_collapse else R.string.root_help_expand))
                    Icon(if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (detailsExpanded) {
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
                            CleanupKind.LAUNCHER -> R.string.root_kind_launcher_help
                            CleanupKind.TILE -> R.string.root_kind_tile_help
                            CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
                            CleanupKind.WIDGET -> R.string.root_kind_widget_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
                val kindTitles = CleanupKind.entries.filter { entry -> components.any { it.kind == entry } }
                    .map { stringResource(it.titleRes()) }
                val lockState = vm.componentBulkLockState(
                    kind,
                    appKey,
                    lockItemsByApp[appKey].orEmpty()
                )
                var lockMenuExpanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClickLabel = expandLabel,
                                onClick = onExpand,
                                onLongClick = { lockMenuExpanded = true }
                            )
                            .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(
                            state = selectionState,
                            enabled = !busy && editableComponents.isNotEmpty(),
                            onClick = { pendingBatch = PendingRootBatch(editableComponents, selectionState == ToggleableState.On) }
                        )
                        AppIcon(first.icon, first.owner)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(first.owner, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            val componentStatus = stringResource(when {
                                components.all { it.enabled == false } -> R.string.root_disabled
                                components.all { it.enabled == true } -> R.string.root_enabled
                                components.any { it.enabled == false } -> R.string.root_partially_disabled
                                else -> R.string.root_unknown_state
                            })
                            Text(
                                stringResource(R.string.root_component_status_categories, componentStatus, kindTitles.joinToString(" · ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (editableComponents.size < components.size) {
                                Text(
                                    stringResource(R.string.root_display_only_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (lockState != BulkLockState.NONE) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(
                                    if (lockState == BulkLockState.PARTIAL) R.string.bulk_lock_partial
                                    else R.string.bulk_lock_full
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                                tint = if (lockState == BulkLockState.PARTIAL) MaterialTheme.colorScheme.tertiary
                                else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = onExpand) {
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, expandLabel)
                        }
                    }
                    BulkLockContextMenu(
                        expanded = lockMenuExpanded,
                        onDismiss = { lockMenuExpanded = false },
                        canLock = lockState != BulkLockState.FULL,
                        canUnlock = lockState != BulkLockState.NONE,
                        onLock = {
                            vm.setComponentAppLocked(
                                kind,
                                appKey,
                                lockItemsByApp[appKey].orEmpty(),
                                true
                            )
                        },
                        onUnlock = {
                            vm.setComponentAppLocked(
                                kind,
                                appKey,
                                lockItemsByApp[appKey].orEmpty(),
                                false
                            )
                        }
                    )
                }
                HorizontalDivider()
            }

            if (expanded) items(components, key = { it.id }) { item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                val lockToggleEnabled = !vm.isComponentAppLockedForItem(kind, item)
                val itemLocked = vm.isComponentBulkProtected(kind, item)
                var lockMenuExpanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .combinedClickable(
                                role = Role.Checkbox,
                                onClick = {
                                    if (editable) vm.changeComponent(item, item.enabled == false)
                                },
                                onLongClick = {
                                    if (lockToggleEnabled) lockMenuExpanded = true
                                }
                            )
                            .heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdaptiveCheckbox(
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
                        if (itemLocked) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.bulk_lock_full),
                                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                            )
                        }
                    }
                    BulkLockContextMenu(
                        expanded = lockMenuExpanded,
                        onDismiss = { lockMenuExpanded = false },
                        canLock = lockToggleEnabled && !itemLocked,
                        canUnlock = lockToggleEnabled && itemLocked,
                        onLock = { vm.setComponentItemLocked(kind, item, true) },
                        onUnlock = { vm.setComponentItemLocked(kind, item, false) }
                    )
                }
            }
        }
        if (visible.isEmpty() && !busy) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(
                        if (kindItems.isNotEmpty()) R.string.root_empty_filtered else R.string.root_no_components
                    ))
                    if (kind != null || viewFilter != UiFilter.ALL ||
                        appTypeFilter != AppTypeFilter.ALL || state.query.isNotBlank()
                    ) {
                        TextButton(onClick = {
                            kind = null
                            viewFilter = UiFilter.ALL
                            appTypeFilter = AppTypeFilter.ALL
                            vm.setQuery("")
                        }) { Text(stringResource(R.string.root_clear_filters)) }
                    }
                }
            }
        }
    }
}