package com.yagay.ListCleaner.ui

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yagay.ListCleaner.data.SpacePackageScanState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.domain.supportsSurfaceOrdering
import com.yagay.ListCleaner.runtime.NotificationCleanupController

/**
 * AOSP list editor now shares Entry/Root's category/filter/selection controls.
 * Rules, locks and priorities live in ONE staged profile-aware SurfaceFilterConfig.
 */
@Composable
internal fun AospSurfaceSettings(
    vm: MainViewModel,
    userId: Int,
    selectedKind: SurfaceKind,
    pendingNavigation: PendingNavigationGuard? = null,
    onSearchStateChanged: ((SurfaceWorkspaceUiState?) -> Unit)? = null,
    refreshRegistry: ListsRefreshRegistry? = null,
) {
    val context = LocalContext.current
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    val remoteInstalled by vm.surfaceInstalledPackages.collectAsStateWithLifecycle()
    val remoteScanStatus by vm.spaceScanState.collectAsStateWithLifecycle()
    var draftBase by remember(userId) { mutableStateOf(saved) }
    var draft by remember(userId) { mutableStateOf(saved) }
    LaunchedEffect(userId, saved) {
        if (draftBase != saved) {
            draft = rebaseSurfaceDraft(draftBase, draft, saved)
            draftBase = saved
        }
    }
    val uiState = rememberSurfaceWorkspaceUiState(
        userId, "aosp_${selectedKind.name}",
        UiPreferencesStore.initialFilter(context, "lists_aosp_${selectedKind.name}"),
        UiPreferencesStore.initialAppType(context, "lists_aosp_${selectedKind.name}"),
    )
    RegisterSurfaceWorkspaceSearch(uiState, onSearchStateChanged)
    val query = uiState.searchQuery
    val viewFilter = uiState.viewFilter
    val appCategory = uiState.appTypeFilter
    var sorting by remember(userId, selectedKind) { mutableStateOf(false) }
    var refreshRevision by remember(userId, selectedKind) { mutableStateOf(0) }
    val guardOwner = remember(userId, selectedKind) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, surfacePendingChangeCount(saved, draft),
        apply = { vm.setSurfaceFilters(rebaseSurfaceDraft(draftBase, draft, saved)) },
        discard = { draft = saved; draftBase = saved },
    )

    val active by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    var catalog by remember(userId, selectedKind) {
        mutableStateOf(SurfaceCandidateDiscovery.Result(emptyList(), ""))
    }
    var loading by remember(userId, selectedKind) { mutableStateOf(true) }
    RegisterListsRefresh(refreshRegistry, enabled = !loading) { refreshRevision++ }
    LaunchedEffect(userId, selectedKind, refreshRevision, active, remoteInstalled) {
        loading = true
        val scanned = withContext(Dispatchers.IO) {
            SurfaceCandidateDiscovery.query(context, selectedKind, userId, active, remoteInstalled)
        }
        catalog = scanned
        loading = false
    }
    val selected = draft.selected(selectedKind)
    val locked = draft.locked(selectedKind)
    val priority = draft.priorities(selectedKind).withIndex().associate { it.value to it.index }
    val entries = (catalog.items + (selected + locked + draft.priorities(selectedKind))
        .map { it to it }).distinctBy { it.first }
        .sortedWith(compareBy<Pair<String, String>>(
            { priority[it.first] ?: Int.MAX_VALUE }, { it.second.lowercase() }, { it.first },
        ))

    val applicationBased = selectedKind in setOf(
        SurfaceKind.NOTIFICATION_SHADE, SurfaceKind.LAUNCHER_DRAWER,
        SurfaceKind.LAUNCHER_PREDICTIONS, SurfaceKind.RECENTS,
        SurfaceKind.QS_EDITOR, SurfaceKind.SETTINGS_APPS,
    )
    var types by remember(userId, selectedKind) { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    LaunchedEffect(userId, selectedKind, entries.map { it.first }) {
        types = if (!applicationBased) emptyMap() else withContext(Dispatchers.IO) {
            SurfaceProfileAppTypes.classify(context, userId, entries.map { it.first })
        }
    }
    val visible = entries.filter { (id, title) ->
        (query.isBlank() || title.contains(query.trim(), ignoreCase = true) ||
            id.contains(query.trim(), ignoreCase = true)) &&
            (!applicationBased || when (appCategory) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> types[id] != true
                AppTypeFilter.SYSTEM -> types[id] == true
            }) && surfaceFilterAllows(viewFilter, id in selected, id in locked)
    }
    val visibleIds = visible.map { it.first }
    val editableIds = visibleIds.filter { it !in locked }
    val selectionConfirmation = rememberSelectionConfirmationController()
    SelectionConfirmationDialog(selectionConfirmation)

    ListsEditorScaffold(
        controls = {
            ListsV2Controls(
                category = "",
                options = listOf("" to stringResource(R.string.common_all)),
                onCategory = {},
                filter = viewFilter, onFilter = { uiState.viewFilter = it; UiPreferencesStore.rememberFilter(context, it, "lists_aosp_${selectedKind.name}") },
                appTypeFilter = appCategory,
                onAppTypeFilter = { uiState.appTypeFilter = it; UiPreferencesStore.rememberAppType(context, it, "lists_aosp_${selectedKind.name}") },
                showAppTypeFilter = applicationBased,
                sortSupported = selectedKind.supportsSurfaceOrdering(),
                sorting = sorting,
                onToggleSort = { sorting = !sorting },
                actions = listOf(
                    WorkspaceMenuAction(stringResource(R.string.select_all), {
                        val label = context.getString(R.string.select_all)
                        selectionConfirmation.request(
                            label,
                            editableIds.count { it !in selected && types[it] != false },
                            SystemSelectionRisk.ENTRY,
                        ) {
                            draft = draft.withSelected(selectedKind,
                                applySurfaceBulkSelection(selected, editableIds, SurfaceBulkOperation.ALL))
                        }
                    }, editableIds.any { it !in selected }),
                    WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                        selectionConfirmation.request(
                            context.getString(R.string.surface_invert_selected),
                            editableIds.count { it !in selected && types[it] != false },
                            SystemSelectionRisk.ENTRY,
                        ) {
                            draft = draft.withSelected(selectedKind,
                                applySurfaceBulkSelection(selected, editableIds, SurfaceBulkOperation.INVERT))
                        }
                    }, editableIds.isNotEmpty()),
                    WorkspaceMenuAction(stringResource(R.string.select_none), {
                        selectionConfirmation.request(context.getString(R.string.select_none)) {
                            draft = draft.withSelected(selectedKind,
                                applySurfaceBulkSelection(selected, editableIds, SurfaceBulkOperation.NONE))
                        }
                    }, editableIds.any { it in selected }),
                ),
            )
        },
        summary = {
            WorkspaceListSummary(
                    title = stringResource(R.string.surface_aosp_selected, selected.size),
                    secondary = if (loading) "Scanning…" else catalog.description,
                )
        },
        pending = {
            if (draft != saved) PendingChangesBar(
                   count = surfacePendingChangeCount(saved, draft),
                   onDiscard = { draft = saved; draftBase = saved },
                   onApply = { vm.setSurfaceFilters(rebaseSurfaceDraft(draftBase, draft, saved)) },
                )
        },
    ) {
        if (visible.isEmpty() && !loading) item(key = "empty") {
            WorkspaceEmptyState(
                message = stringResource(R.string.root_empty_filtered),
                clearLabel = stringResource(R.string.root_clear_filters),
                onClear = {
                    uiState.searchQuery = ""
                    uiState.viewFilter = UiFilter.ALL
                    uiState.appTypeFilter = AppTypeFilter.ALL
                },
            )
        }
        items(visible, key = { it.first }) { (id, title) ->
        val appIcon = remember(id, userId) {
            if (!applicationBased) null else runCatching {
                val drawable = context.packageManager.getApplicationIcon(id).mutate()
                val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(it))
                }
            }.getOrNull()
        }
        val isLocked = id in locked
        val actions = buildList {
            add(WorkspaceMenuAction(
                label = context.getString(
                    if (isLocked) R.string.surface_unlock_item else R.string.surface_lock_target
                ),
                onClick = {
                    draft = draft.withLocked(selectedKind,
                        if (isLocked) locked - id else locked + id)
                },
            ))
            if (selectedKind.supportsSurfaceOrdering() && sorting) {
                fun move(direction: Int) {
                    val baseOrder = (draft.priorities(selectedKind) +
                        entries.map { it.first }).distinct().take(2000)
                    if (id in baseOrder) {
                        draft = draft.withPriorities(selectedKind, baseOrder)
                            .movePriority(selectedKind, id, direction)
                    }
                }
                add(WorkspaceMenuAction(context.getString(R.string.surface_move_earlier), {
                    move(-1)
                }))
                add(WorkspaceMenuAction(context.getString(R.string.surface_move_later), {
                    move(1)
                }))
            }
        }
        SurfaceWorkspaceRow(
            icon = appIcon,
            packageName = if (applicationBased) id else null,
            title = title, subtitle = id, selected = id in selected,
            enabled = !isLocked, locked = isLocked,
            onToggle = {
                draft = draft.withSelected(selectedKind,
                    if (id in selected) selected - id else selected + id)
            }, actions = actions,
            onLock = { draft = draft.withLocked(selectedKind, locked + id) },
            onUnlock = { draft = draft.withLocked(selectedKind, locked - id) },
            expanded = uiState.expandedKey == id,
            onExpand = { uiState.toggleExpanded(id) },
            details = {
                Column(Modifier.fillMaxWidth().padding(
                    start = MaterialUiTokens.pageHorizontal, end = MaterialUiTokens.pageHorizontal,
                    bottom = 8.dp,
                )) {
                    WorkspaceDetailHeading(title = title, kind = selectedKind.name)
                    WorkspaceDetailCaption(id)
                    if (isLocked) WorkspaceAppNote(
                        stringResource(R.string.filter_locked),
                        informational = true,
                    )
                }
            },
        )
    }
    }
}
