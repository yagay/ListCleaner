package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.moveVisiblePriority
import com.yagay.ListCleaner.domain.moveVisiblePriorityTo

/**
 * Root's counterpart to Entry Priority. The same sort-mode toolbar and
 * list presentation are reused, while SystemUI receives TileService IDs,
 * never Root component enable/disable commands or application package ranks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RootTileOrderEditor(
    vm: MainViewModel,
    userId: Int,
    candidates: List<RootComponent>,
    saved: SurfaceFilterConfig,
    pendingNavigation: PendingNavigationGuard?,
    onExit: () -> Unit,
    onSelectKind: (CleanupKind?) -> Unit,
    onManagedKind: (ManagedComponentSurface) -> Unit = {},
) {
    val context = LocalContext.current
    val original = saved.tileOrder
    var base by remember(userId) { mutableStateOf(original) }
    var draft by remember(userId) { mutableStateOf(original) }
    LaunchedEffect(original, userId) {
        if (base != original) {
            if (draft == base) draft = original
            base = original
        }
    }
    val changes = draft != original
    val save = { vm.setSurfaceFilters(saved.withTileOrder(draft)) }
    val discard = { draft = original }
    val owner = remember(userId) { Any() }
    RegisterPendingNavigation(pendingNavigation, owner, if (changes) 1 else 0, save, discard)

    var confirmExit by remember { mutableStateOf(false) }
    fun requestExit(next: () -> Unit) {
        if (pendingNavigation != null) pendingNavigation.request(next)
        else if (changes) confirmExit = true
        else next()
    }
    if (confirmExit) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { AdaptiveText(stringResource(R.string.lc_pending_leave_title)) },
            text = { AdaptiveText(stringResource(R.string.lc_pending_leave_body)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    if (save()) { confirmExit = false; onExit() }
                }) { AdaptiveText(stringResource(R.string.lc_pending_leave_apply)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = {
                    confirmExit = false
                    discard()
                    onExit()
                }) { AdaptiveText(stringResource(R.string.lc_pending_leave_discard)) }
                AdaptiveTextButton(onClick = { confirmExit = false }) {
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
    val known = remember(candidates) {
        candidates.filter { it.kind == CleanupKind.TILE }
            .distinctBy { it.component.flattenToString() }
    }
    val byComponent = known.associateBy { it.component.flattenToString() }
    // A vanished TileService can stay in the saved policy until its rule is
    // restored, but never appears as a fabricated draggable component row.
    val ranked = draft.filter { it in byComponent }
    val rankedIds = ranked.toSet()
    val all = ranked.mapNotNull(byComponent::get) +
        known.filterNot { it.component.flattenToString() in rankedIds }
            .sortedWith(compareBy({ it.label.lowercase() },
                { it.component.flattenToString() }))
    val visible = all.filter { item ->
        appTypeFilter.matches(item.appType) && when (viewFilter) {
            UiFilter.ALL -> true
            UiFilter.SHOW_SELECTED -> item.component.flattenToString() in draft
            UiFilter.HIDE_SELECTED -> item.component.flattenToString() !in draft
            UiFilter.LOCKED -> vm.isComponentBulkProtected(CleanupKind.TILE, item)
        }
    }
    val moveTargets = visible.map { it.component.flattenToString() }.filter { it in draft }

    fun toggle(id: String) {
        draft = if (id in draft) draft - id else (draft + id).distinct().take(2000)
    }
    val listState = rememberLazyListState()
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val handlePx = with(density) { 40.dp.toPx() }
    LaunchedEffect(draft, moveTargets, viewFilter, appTypeFilter) { dragState.cancel() }
    DisposableEffect(dragState) { onDispose { dragState.cancel() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = if (changes) 90.dp else 16.dp),
        ) {
            stickyHeader(key = "controls") {
                PageFilterSection {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        WorkspaceCategoryFilterRow(
                            category = {
                                ClassicComponentCategoryFilter(
                                    selected = CleanupKind.TILE,
                                    modifier = Modifier.fillMaxWidth(),
                                    onSelected = { newKind ->
                                        if (newKind != CleanupKind.TILE) {
                                            requestExit { onSelectKind(newKind) }
                                        }
                                    },
                                    onManagedSurface = { managed ->
                                        requestExit { onManagedKind(managed) }
                                    },
                                )
                            },
                            beforeFilter = {
                                WorkspaceSortModeAction(
                                    supported = true,
                                    ordering = true,
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
                            afterFilter = {
                                WorkspaceListActions(actions = listOf(
                                    WorkspaceMenuAction(
                                        stringResource(R.string.select_all),
                                        {
                                            draft = (draft + visible.map {
                                                it.component.flattenToString()
                                            }).distinct().take(2000)
                                        },
                                        visible.any { it.component.flattenToString() !in draft },
                                    ),
                                    WorkspaceMenuAction(
                                        stringResource(R.string.select_none),
                                        {
                                            val ids = visible.map {
                                                it.component.flattenToString()
                                            }.toSet()
                                            draft = draft.filterNot { it in ids }
                                        },
                                        visible.any { it.component.flattenToString() in draft },
                                    ),
                                ))
                            },
                        )
                        AdaptiveHorizontalDivider()
                    }
                }
            }
            item(key = "summary") {
                WorkspaceListSummary(
                    title = stringResource(R.string.cleanup_tile),
                    secondary = stringResource(R.string.priority_drag_help),
                )
                if (draft.isNotEmpty()) {
                    AdaptiveTextButton(onClick = { draft = emptyList() }) {
                        AdaptiveText(stringResource(R.string.qs_tile_sort_restore))
                    }
                }
            }
            items(visible, key = { "app|TILE|" + it.component.flattenToString() }) { tile ->
                val id = tile.component.flattenToString()
                val position = moveTargets.indexOf(id)
                ContinuousApplicationSurface(expanded = false) {
                    MaterialAppRowBody(
                        icon = tile.icon,
                        title = tile.label,
                        subtitle = tile.component.flattenToShortString(),
                        packageName = tile.component.packageName,
                        modifier = Modifier.alpha(if (drag?.packageName == id) 0.4f else 1f),
                        selection = {
                            WorkspaceAppSelectionCheckbox(
                                state = if (id in draft) ToggleableState.On else ToggleableState.Off,
                                enabled = true,
                                onClick = { toggle(id) },
                            )
                        },
                        details = {
                            if (id in draft) AdaptiveText(
                                stringResource(R.string.priority_rank, draft.indexOf(id) + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailing = {
                            if (id in draft) {
                                WorkspaceToolbarAction(
                                    enabled = position > 0,
                                    onClick = {
                                        draft = moveVisiblePriority(draft, moveTargets, id, -1)
                                    },
                                ) { AdaptiveIcon(Icons.Rounded.ArrowUpward, stringResource(R.string.priority_move_up), Modifier.size(18.dp)) }
                                WorkspaceToolbarAction(
                                    enabled = position >= 0 && position < moveTargets.lastIndex,
                                    onClick = {
                                        draft = moveVisiblePriority(draft, moveTargets, id, 1)
                                    },
                                ) { AdaptiveIcon(Icons.Rounded.ArrowDownward, stringResource(R.string.priority_move_down), Modifier.size(18.dp)) }
                                Box(
                                    modifier = Modifier.size(40.dp).pointerInput(id, moveTargets, draft) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { touch ->
                                                val row = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == "app|TILE|" + id }
                                                val y = row?.let {
                                                    it.offset + (it.size - handlePx) / 2f + touch.y
                                                }
                                                if (y != null && dragState.start(y, "TILE", moveTargets, draft)) {
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
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AdaptiveIcon(Icons.Rounded.DragHandle,
                                        stringResource(R.string.priority_drag_help), Modifier.size(22.dp))
                                }
                            }
                        },
                    )
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    WorkspaceEmptyState(
                        message = stringResource(R.string.root_empty_filtered),
                        clearLabel = stringResource(R.string.root_clear_filters),
                        onClear = {
                            viewFilter = UiFilter.ALL
                            appTypeFilter = AppTypeFilter.ALL
                        },
                    )
                }
            }
        }
        PendingChangesBar(
            count = if (changes) 1 else 0,
            onDiscard = discard,
            onApply = { save() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
