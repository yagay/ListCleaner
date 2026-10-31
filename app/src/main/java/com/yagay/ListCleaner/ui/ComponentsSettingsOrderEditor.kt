package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.activePriorityRanks
import com.yagay.ListCleaner.domain.addActivePriority
import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.domain.moveVisiblePriority
import com.yagay.ListCleaner.domain.moveVisiblePriorityTo

/**
 * Entry / Root style ordering interface for the Settings Apps surface.
 * Never sends priority changes through Root's component enable/disable path.
 */
@Composable
internal fun ComponentsSettingsOrderEditor(
    vm: MainViewModel,
    userId: Int,
    saved: SurfaceFilterConfig,
    candidates: List<String>,
    labels: Map<String, String>,
    pendingNavigation: PendingNavigationGuard?,
    onExit: () -> Unit,
    onRootKind: (CleanupKind?) -> Unit,
    onManagedKind: (ManagedComponentSurface) -> Unit,
) {
    val context = LocalContext.current
    val kind = SurfaceKind.SETTINGS_APPS
    val original = saved.priorities(kind)
    var base by remember(userId) { mutableStateOf(original) }
    var draft by remember(userId) { mutableStateOf(original) }
    LaunchedEffect(userId, original) {
        if (base != original) {
            if (draft == base) draft = original
            base = original
        }
    }
    val changed = draft != original
    val save = {
        val current = vm.surfaceFilters.value
        val updated = current.withPriorities(kind, draft)
        if (updated == current || vm.setSurfaceFilters(updated)) {
            base = draft
            true
        } else false
    }
    val discard = { draft = original }
    val guard = remember(userId) { Any() }
    RegisterPendingNavigation(pendingNavigation, guard, if (changed) 1 else 0,
        save, discard, rootEditor = true)
    var confirmExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestExit: (() -> Unit) -> Unit = { action ->
        if (pendingNavigation != null) pendingNavigation.request(action)
        else if (changed) confirmExit = action else action()
    }
    confirmExit?.let { action ->
        AdaptiveAlertDialog(
            onDismissRequest = { confirmExit = null },
            title = { AdaptiveText(stringResource(R.string.lc_pending_leave_title)) },
            text = { AdaptiveText(stringResource(R.string.lc_pending_leave_body)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    if (save()) { confirmExit = null; action() }
                }) { AdaptiveText(stringResource(R.string.lc_pending_leave_apply)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = {
                    discard(); confirmExit = null; action()
                }) { AdaptiveText(stringResource(R.string.lc_pending_leave_discard)) }
                AdaptiveTextButton(onClick = { confirmExit = null }) {
                    AdaptiveText(stringResource(R.string.lc_pending_leave_stay))
                }
            },
        )
    }
    BackHandler { requestExit(onExit) }
    var viewFilter by rememberSaveable(userId) { mutableStateOf(UiFilter.ALL) }
    var appTypeFilter by rememberSaveable(userId) {
        mutableStateOf(UiPreferencesStore.initialAppType(context, "components"))
    }
    var systemApps by remember(userId, candidates) { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    LaunchedEffect(userId, candidates) {
        systemApps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SurfaceProfileAppTypes.classify(context, userId, candidates)
        }
    }
    val known = candidates.distinct()
    val byId = known.toSet()
    val activeRanks = activePriorityRanks(draft, byId)
    val ranks = draft.filter { it in byId }
    val ranked = ranks.toSet()
    val all = ranks + known.filterNot { it in ranked }.sortedWith(compareBy(
        { labels[it].orEmpty().lowercase() }, { it },
    ))
    val visible = all.filter { id ->
        val typeAllowed = when (appTypeFilter) {
            AppTypeFilter.ALL -> true
            AppTypeFilter.USER -> systemApps[id] != true
            AppTypeFilter.SYSTEM -> systemApps[id] == true
        }
        typeAllowed && when (viewFilter) {
            UiFilter.ALL -> true
            UiFilter.SHOW_SELECTED -> id in draft
            UiFilter.HIDE_SELECTED -> id !in draft
            UiFilter.LOCKED -> id in saved.locked(kind)
        }
    }
    val moves = visible.filter { it in draft && it !in saved.locked(kind) }
    val listState = rememberLazyListState()
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val handlePx = with(density) { 40.dp.toPx() }
    LaunchedEffect(draft, moves, viewFilter, appTypeFilter) { dragState.cancel() }
    DisposableEffect(dragState) { onDispose { dragState.cancel() } }
    val toggle: (String) -> Unit = { id ->
        if (id !in saved.locked(kind)) {
            draft = if (id in draft) draft - id else addActivePriority(draft, byId, id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(bottom = if (changed) 90.dp else 16.dp),
        ) {
            stickyHeader(key = "controls") {
                PageFilterSection {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        WorkspaceCategoryFilterRow(
                            category = {
                                ComponentCategoryPicker(
                                    selected = null,
                                    onSelected = { selected -> requestExit { onRootKind(selected) } },
                                    managedSurface = ManagedComponentSurface.SETTINGS_APPS,
                                    onManagedSurface = { next ->
                                        requestExit {
                                            if (next == ManagedComponentSurface.SETTINGS_APPS) onExit()
                                            else onManagedKind(next)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            beforeFilter = {
                                WorkspaceSortModeAction(
                                    supported = true, ordering = true,
                                    onOpen = { requestExit(onExit) },
                                )
                            },
                            filter = {
                                WorkspaceFilterBar(
                                    appTypeFilter = appTypeFilter,
                                    onAppTypeFilter = { appTypeFilter = it },
                                    viewFilter = viewFilter,
                                    onViewFilter = { viewFilter = it },
                                    viewTitle = {
                                        stringResource(when (it) {
                                            UiFilter.ALL -> R.string.common_all
                                            UiFilter.SHOW_SELECTED -> R.string.priority_selected
                                            UiFilter.HIDE_SELECTED -> R.string.priority_unselected
                                            UiFilter.LOCKED -> R.string.filter_locked
                                        })
                                    },
                                )
                            },
                            afterFilter = { WorkspaceListActions(actions = listOf(
                                WorkspaceMenuAction(stringResource(R.string.select_all), {
                                    draft = visible.filterNot { it in saved.locked(kind) }
                                        .fold(draft) { order, id ->
                                            addActivePriority(order, byId, id)
                                        }
                                }, visible.any { it !in draft && it !in saved.locked(kind) }),
                                WorkspaceMenuAction(stringResource(R.string.select_none), {
                                    val ids = visible.filterNot { it in saved.locked(kind) }.toSet()
                                    draft = draft.filterNot { it in ids }
                                }, visible.any { it in draft && it !in saved.locked(kind) }),
                                WorkspaceMenuAction(stringResource(R.string.invert_selection), {
                                    val unlocked = visible.filterNot { it in saved.locked(kind) }
                                    val retained = draft.filterNot { it in unlocked }
                                    draft = unlocked.filterNot { it in draft }
                                        .fold(retained) { order, id ->
                                            addActivePriority(order, byId, id)
                                        }
                                }, visible.any { it !in saved.locked(kind) }),
                            )) },
                        )
                        AdaptiveHorizontalDivider()
                    }
                }
            }
            item(key = "summary") {
                WorkspaceListSummary(
                    title = stringResource(R.string.component_settings_apps),
                    secondary = stringResource(R.string.priority_drag_help),
                )
                AdaptiveTextButton(onClick = { draft = emptyList() }) {
                    AdaptiveText(stringResource(R.string.component_surface_restore_order))
                }
            }
            visible.forEachIndexed { index, id ->
                val prioritized = id in draft
                if (startsPrioritySection(visible, index) { it in draft }) {
                    item(key = "priority-section-${if (prioritized) "pinned" else "available"}") {
                        WorkspacePrioritySectionHeading(prioritized)
                    }
                }
                item(key = "app|SETTINGS_APPS|$id", contentType = "settings-app") {
                val position = moves.indexOf(id)
                ContinuousApplicationSurface(expanded = false) {
                    MaterialAppRowBody(
                        icon = remember(id, userId) {
                            if (userId != android.os.Process.myUid() / 100000) null
                            else runCatching {
                                val drawable = context.packageManager.getApplicationIcon(id)
                                val px = (48 * context.resources.displayMetrics.density)
                                    .toInt().coerceAtLeast(1)
                                android.graphics.Bitmap.createBitmap(px, px,
                                    android.graphics.Bitmap.Config.ARGB_8888).also {
                                    drawable.setBounds(0, 0, px, px)
                                    drawable.draw(android.graphics.Canvas(it))
                                }
                            }.getOrNull()
                        },
                        title = labels[id] ?: id, subtitle = id, packageName = id,
                        modifier = Modifier.alpha(if (drag?.packageName == id) 0.4f else 1f),
                        selection = {
                            WorkspaceAppSelectionCheckbox(
                                state = if (id in draft) ToggleableState.On else ToggleableState.Off,
                                enabled = id !in saved.locked(kind),
                                onClick = { toggle(id) },
                            )
                        },
                        details = {
                            if (id in draft) AdaptiveText(
                                stringResource(R.string.priority_rank, requireNotNull(activeRanks[id])),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailing = {
                            if (id in draft && id !in saved.locked(kind)) {
                                WorkspaceToolbarAction(
                                    enabled = position > 0,
                                    onClick = { draft = moveVisiblePriority(draft, moves, id, -1) },
                                ) {
                                    AdaptiveIcon(Icons.Rounded.ArrowUpward,
                                        stringResource(R.string.priority_move_up), Modifier.size(18.dp))
                                }
                                WorkspaceToolbarAction(
                                    enabled = position >= 0 && position < moves.lastIndex,
                                    onClick = { draft = moveVisiblePriority(draft, moves, id, 1) },
                                ) {
                                    AdaptiveIcon(Icons.Rounded.ArrowDownward,
                                        stringResource(R.string.priority_move_down), Modifier.size(18.dp))
                                }
                                Box(
                                    modifier = Modifier.size(40.dp).pointerInput(id, moves, draft) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { touch ->
                                                val row = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == "app|SETTINGS_APPS|$id" }
                                                val y = row?.let {
                                                    it.offset + (it.size - handlePx) / 2f + touch.y
                                                }
                                                if (y != null && dragState.start(y, "SETTINGS_APPS", moves, draft)) {
                                                    if (UiPreferencesRuntime.current.dragHaptics)
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            },
                                            onDrag = { change, delta ->
                                                if (dragState.session != null) {
                                                    change.consume()
                                                    dragState.move(delta.y)
                                                }
                                            },
                                            onDragCancel = { dragState.cancel() },
                                            onDragEnd = {
                                                dragState.finish()?.let {
                                                    draft = moveVisiblePriorityTo(
                                                        draft, it.visible, it.packageName, it.target)
                                                }
                                            },
                                        )
                                    }, contentAlignment = Alignment.Center,
                                ) {
                                    AdaptiveIcon(Icons.Rounded.DragHandle,
                                        stringResource(R.string.priority_drag_help), Modifier.size(22.dp))
                                }
                            }
                        },
                    )
                }
                }
            }
        }
        PendingChangesBar(
            count = if (changed) 1 else 0,
            onDiscard = discard,
            onApply = { save() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
