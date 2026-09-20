package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.PriorityListFilter
import com.yagay.ListCleaner.domain.matchesOpenPreset
import com.yagay.ListCleaner.domain.matchesBrowserHost
import com.yagay.ListCleaner.domain.priorityAppGroups
import com.yagay.ListCleaner.domain.priorityCandidates
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriorityDialogContent(state: MainState, vm: MainViewModel) {
    var editingTitle by remember { mutableStateOf<ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
    editingTitle?.let { item ->
        ComponentTitleDialog(
            item,
            state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null }
        )
    }
    if (showCustomTypes) {
        CustomOpenTypeDialog(
            config = state.openTypesExplicit,
            onSave = vm::setCustomOpenDefinition,
            onDismiss = { showCustomTypes = false }
        )
    }
    if (showBrowserHosts) {
        BrowserHostDialog(
            config = state.browserLinks,
            onSave = vm::setBrowserHosts,
            onDismiss = { showBrowserHosts = false }
        )
    }

    var kind by rememberSaveable { mutableStateOf(state.filter ?: IntentKind.SHARE) }
    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }
    var browserHost by rememberSaveable { mutableStateOf<String?>(null) }
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
    val bulkLockRevision by vm.bulkLockRevision.collectAsState()
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(kind) {
        if (kind != IntentKind.OPEN) openPreset = null
        if (kind != IntentKind.DEEP_LINK) browserHost = null
    }
    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) openPreset = null
    }
    LaunchedEffect(state.browserAvailableHosts, browserHost) {
        if (browserHost != null && browserHost !in state.browserAvailableHosts) browserHost = null
    }

    val typedSelected = openPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = browserHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = kind == IntentKind.DEEP_LINK && browserHost != null
    val scopedCandidates = when {
        kind == IntentKind.OPEN && openPreset != null ->
            state.candidates.filter { it.matchesOpenPreset(openPreset!!, state.openTypesExplicit.customDefinitions) }
        deepLinkScoped -> state.candidates.filter {
            it.rule.kind == IntentKind.DEEP_LINK &&
                (it.matchesBrowserHost(browserHost!!) || it.rule in deepLinkSelected)
        }
        else -> state.candidates
    }
    val explicitTypedPriority = openPreset?.let { state.openTypesExplicit.priorities[it].orEmpty() }.orEmpty()
    val genericOpenPriority = state.priorities.apps[IntentKind.OPEN].orEmpty()
    val inheritsOpenPriority = kind == IntentKind.OPEN && openPreset != null && explicitTypedPriority.isEmpty() && genericOpenPriority.isNotEmpty()
    val hasExplicitOpenPriority = kind == IntentKind.OPEN && openPreset != null && explicitTypedPriority.isNotEmpty()
    val explicitDeepLinkPriority = browserHost?.let { state.browserLinks.priorities[it].orEmpty() }.orEmpty()
    val genericDeepLinkPriority = state.priorities.apps[IntentKind.DEEP_LINK].orEmpty()
    val inheritsDeepLinkPriority = deepLinkScoped && explicitDeepLinkPriority.isEmpty() && genericDeepLinkPriority.isNotEmpty()
    val hasExplicitDeepLinkPriority = deepLinkScoped && explicitDeepLinkPriority.isNotEmpty()
    val rankedRaw = when {
        kind == IntentKind.OPEN && openPreset != null -> state.openTypes.priorities[openPreset].orEmpty()
        deepLinkScoped && explicitDeepLinkPriority.isNotEmpty() -> explicitDeepLinkPriority
        deepLinkScoped -> genericDeepLinkPriority
        else -> state.priorities.apps[kind].orEmpty()
    }
    val lockScope = if (deepLinkScoped) browserPriorityBulkLockScope(browserHost!!) else priorityBulkLockScope(kind, openPreset)
    val scopedExtraSelected = if (deepLinkScoped) explicitDeepLinkSelected else typedSelected
    val baseGroups = remember(scopedCandidates, state.selected, scopedExtraSelected, state.displayMode, kind, openPreset, browserHost, rankedRaw, state.query, viewFilter, bulkLockRevision) {
        priorityAppGroups(
            scopedCandidates,
            state.selected,
            state.displayMode,
            kind,
            rankedRaw,
            state.query,
            when (viewFilter) {
                UiFilter.ALL, UiFilter.LOCKED -> PriorityListFilter.ALL
                UiFilter.HIDE_SELECTED -> PriorityListFilter.UNSELECTED
                UiFilter.SHOW_SELECTED -> PriorityListFilter.SELECTED
            },
            scopedExtraSelected
        )
    }
    val lockFilteredGroups = if (viewFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseGroups.filter { vm.bulkLockState(lockScope, it.packageName, emptyList()) != BulkLockState.NONE }
    } else {
        baseGroups
    }
    val groups = lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    val moveTargets = groups.filter { it.rank != null }.sortedBy { it.rank }.map { it.packageName }
    val visibleSaved = priorityCandidates(scopedCandidates, state.selected, state.displayMode, kind, scopedExtraSelected)
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
    LaunchedEffect(kind, openPreset, browserHost, viewFilter, appTypeFilter, state.query, rankedRaw, moveTargets) { dragState.cancel() }
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
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(kind, openPreset, browserHost, viewFilter, appTypeFilter, state.query) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (dragState.start(position.y, kind.name, currentVisible, currentSaved)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragState.session != null) {
                            change.consume()
                            dragState.move(amount.y)
                        }
                    },
                    onDragCancel = { dragState.cancel() },
                    onDragEnd = {
                        dragState.finish()?.let { finished ->
                            when {
                                kind == IntentKind.OPEN && openPreset != null ->
                                    vm.moveOpenTypePriorityTo(openPreset!!, finished.packageName, finished.target, finished.visible, finished.saved)
                                deepLinkScoped ->
                                    vm.moveBrowserHostPriorityTo(browserHost!!, finished.packageName, finished.target, finished.visible, finished.saved)
                                else -> vm.movePriorityTo(kind, finished.packageName, finished.target, finished.visible, finished.saved)
                            }
                        }
                    }
                )
            },
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "status") {
                ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
                if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
            }
            stickyHeader(key = "controls") {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        ListControls(
                            state.copy(filter = kind, uiFilter = viewFilter),
                            onFilter = { entry -> if (entry != null) { kind = entry; expandedKey = null } },
                            onUiFilter = { viewFilter = it },
                            appTypeFilter = appTypeFilter,
                            onAppTypeFilter = { appTypeFilter = it },
                            includeAllKinds = false,
                            viewTitle = {
                                stringResource(
                                    when (it) {
                                        UiFilter.ALL -> R.string.common_all
                                        UiFilter.HIDE_SELECTED -> R.string.priority_unselected
                                        UiFilter.SHOW_SELECTED -> R.string.priority_selected
                                        UiFilter.LOCKED -> R.string.filter_locked
                                    }
                                )
                            },
                            extraFilter = {
                                when (kind) {
                                    IntentKind.OPEN -> OpenPresetFilterMenu(
                                        selected = openPreset,
                                        config = state.openTypesExplicit,
                                        onSelected = { openPreset = it },
                                        onManageCustom = { showCustomTypes = true }
                                    )
                                    IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                        selected = browserHost,
                                        config = state.browserLinks,
                                        onSelected = { browserHost = it },
                                        onManage = { showBrowserHosts = true }
                                    )
                                    else -> Unit
                                }
                            },
                            onSelectAll = {
                                when {
                                    kind == IntentKind.OPEN && openPreset != null ->
                                        vm.selectOpenTypePriorityApps(openPreset!!, groups.map { it.packageName }, lockScope)
                                    deepLinkScoped ->
                                        vm.selectBrowserHostPriorityApps(browserHost!!, groups.map { it.packageName }, lockScope)
                                    else -> vm.selectPriorityApps(kind, groups.map { it.packageName }, lockScope)
                                }
                            },
                            onInvert = {
                                when {
                                    kind == IntentKind.OPEN && openPreset != null ->
                                        vm.invertOpenTypePriorityApps(openPreset!!, groups.map { it.packageName }, lockScope)
                                    deepLinkScoped ->
                                        vm.invertBrowserHostPriorityApps(browserHost!!, groups.map { it.packageName }, lockScope)
                                    else -> vm.invertPriorityApps(kind, groups.map { it.packageName }, lockScope)
                                }
                            }
                        )
                    }
                }
            }
            item(key = "summary") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val presetTitle = when {
                        openPreset != null -> state.openTypes.localizedTitle(openPreset!!)
                        browserHost != null -> browserHost
                        else -> null
                    }
                    Text(
                        if (presetTitle == null) stringResource(R.string.app_list_count, groups.size)
                        else stringResource(R.string.app_list_count_type, groups.size, presetTitle),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        stringResource(R.string.priority_intro),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.priority_drag_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (kind == IntentKind.OPEN && openPreset != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    inheritsOpenPriority -> stringResource(R.string.priority_source_inherited)
                                    hasExplicitOpenPriority -> stringResource(R.string.priority_source_dedicated, presetTitle.orEmpty())
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsOpenPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitOpenPriority) {
                                TextButton(onClick = { vm.resetOpenTypePriority(openPreset!!) }) {
                                    Text(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.priority_inheritance_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (deepLinkScoped) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    inheritsDeepLinkPriority -> stringResource(R.string.priority_browser_source_inherited)
                                    hasExplicitDeepLinkPriority -> stringResource(R.string.priority_source_dedicated, browserHost!!)
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsDeepLinkPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitDeepLinkPriority) {
                                TextButton(onClick = { vm.resetBrowserHostPriority(browserHost!!) }) {
                                    Text(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.priority_browser_inheritance_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            stringResource(R.string.priority_general_help),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val compatibility = when {
                        !state.module.connected -> stringResource(R.string.priority_compat_disconnected)
                        state.module.detection.hosts.isEmpty() -> stringResource(R.string.priority_compat_unknown_host)
                        state.module.detection.hosts.any {
                            it.packageName != "system" &&
                                !it.className.startsWith("com.android.internal.app.") &&
                                !it.className.startsWith("com.android.intentresolver.")
                        } -> stringResource(R.string.priority_compat_vendor_path)
                        else -> stringResource(R.string.priority_compat_aosp)
                    }
                    Text(
                        if (!state.runtime.ready) state.runtime.message
                        else if (kind == IntentKind.PROCESS_TEXT) stringResource(R.string.priority_process_text_note)
                        else compatibility,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hiddenSavedCount > 0) {
                        Text(stringResource(R.string.priority_hidden_saved, hiddenSavedCount), style = MaterialTheme.typography.bodySmall)
                    }
                    if (rankedRaw.size >= 200) {
                        Text(
                            stringResource(R.string.priority_limit_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.error != null) {
                        Text(stringResource(R.string.rules_refresh_incomplete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            groups.forEach { group ->
                val packageName = group.packageName
                val key = "${kind.name}|${openPreset?.name ?: browserHost ?: "ALL"}|$packageName"
                val expanded = expandedKey == key
                val onExpand = { expandedKey = if (expanded) null else key }
                val first = group.components.first()
                item(key = "app|$key", contentType = "app") {
                    val marker = MaterialTheme.colorScheme.primary
                    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
                    Row(
                        Modifier.fillMaxWidth()
                            .bulkLockSwipe(
                                onLock = { vm.setBulkAppLocked(lockScope, packageName, emptyList(), true) },
                                onUnlock = { vm.setBulkAppLocked(lockScope, packageName, emptyList(), false) }
                            )
                            .alpha(if (drag?.packageName == packageName) 0.3f else 1f)
                            .drawWithContent {
                                drawContent()
                                if (drag?.target == packageName && drag.packageName != packageName) {
                                    val y = if (drag.movingDown) size.height - 2.dp.toPx() else 2.dp.toPx()
                                    drawLine(marker, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                                }
                            }
                            .clickable(onClickLabel = expandLabel, onClick = onExpand)
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = group.rank != null,
                            enabled = group.rank != null || rankedRaw.size < 200,
                            onCheckedChange = { checked ->
                                when {
                                    kind == IntentKind.OPEN && openPreset != null -> {
                                        if (checked) vm.pinOpenTypeApp(openPreset!!, packageName)
                                        else vm.removeOpenTypePriority(openPreset!!, packageName)
                                    }
                                    deepLinkScoped -> {
                                        if (checked) vm.pinBrowserHostApp(browserHost!!, packageName)
                                        else vm.removeBrowserHostPriority(browserHost!!, packageName)
                                    }
                                    checked -> vm.pinApp(kind, packageName)
                                    else -> vm.removePriority(kind, packageName)
                                }
                            }
                        )
                        AppIcon(first.appIcon, first.appLabel)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            val rankText = group.rank?.let {
                                stringResource(
                                    if (inheritsOpenPriority || inheritsDeepLinkPriority) R.string.priority_rank_inherited else R.string.priority_rank,
                                    it
                                )
                            } ?: stringResource(R.string.priority_not_prioritized)
                            Text(
                                stringResource(R.string.priority_app_summary, rankText, group.components.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val lockState = vm.bulkLockState(lockScope, packageName, emptyList())
                        if (lockState != BulkLockState.NONE) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.bulk_lock_full),
                                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
                            )
                        }
                        IconButton(onClick = onExpand) {
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                expandLabel
                            )
                        }
                    }
                    HorizontalDivider()
                }
                if (expanded) {
                    if (group.rank != null) {
                        item(key = "order|$key") {
                            val index = moveTargets.indexOf(packageName)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(
                                        if (inheritsOpenPriority) R.string.priority_rank_inherited_short else R.string.priority_rank,
                                        group.rank
                                    ),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                TextButton(
                                    onClick = {
                                        when {
                                            kind == IntentKind.OPEN && openPreset != null ->
                                                vm.moveOpenTypePriority(openPreset!!, packageName, -1, moveTargets)
                                            deepLinkScoped ->
                                                vm.moveBrowserHostPriority(browserHost!!, packageName, -1, moveTargets)
                                            else -> vm.movePriority(kind, packageName, -1, moveTargets)
                                        }
                                    },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.priority_move_up))
                                }
                                TextButton(
                                    onClick = {
                                        when {
                                            kind == IntentKind.OPEN && openPreset != null ->
                                                vm.moveOpenTypePriority(openPreset!!, packageName, 1, moveTargets)
                                            deepLinkScoped ->
                                                vm.moveBrowserHostPriority(browserHost!!, packageName, 1, moveTargets)
                                            else -> vm.movePriority(kind, packageName, 1, moveTargets)
                                        }
                                    },
                                    enabled = index >= 0 && index < moveTargets.lastIndex
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.priority_move_down))
                                }
                            }
                        }
                    }
                    items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { item ->
                        ComponentInfoRow(item, state.priorities.titles[item.rule.id]) { editingTitle = item }
                    }
                }
            }

            if (!state.loading && groups.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when {
                                    state.query.isNotBlank() -> R.string.priority_empty_search
                                    viewFilter == UiFilter.SHOW_SELECTED -> R.string.priority_empty_selected
                                    viewFilter == UiFilter.HIDE_SELECTED -> R.string.priority_empty_unselected
                                    viewFilter == UiFilter.LOCKED -> R.string.no_matching_components
                                    else -> R.string.priority_empty_category
                                }
                            )
                        )
                    }
                }
            }
        }

        drag?.let { moving ->
            groups.firstOrNull { it.packageName == moving.packageName }?.let { group ->
                val first = group.components.first()
                Surface(
                    Modifier.fillMaxWidth().offset { IntOffset(0, moving.top.roundToInt()) }.zIndex(1f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(first.appIcon, first.appLabel)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(
                                    R.string.priority_drag_target,
                                    groups.firstOrNull { it.packageName == moving.target }?.rank?.toString() ?: "?"
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentInfoRow(item: ComponentCandidate, customTitle: String?, onEditTitle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .heightIn(min = 48.dp).padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!customTitle.isNullOrBlank()) {
                Text(
                    stringResource(R.string.component_shown_as, customTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                item.rule.className,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEditTitle) {
            Icon(Icons.Rounded.Edit, stringResource(R.string.component_edit_display_name))
        }
    }
}
