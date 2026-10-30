package com.yagay.ListCleaner.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
) {
    val context = LocalContext.current
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    var draft by remember(userId, saved) { mutableStateOf(saved) }
    var query by remember(userId, selectedKind) { mutableStateOf("") }
    var viewFilter by remember(userId, selectedKind) { mutableStateOf(UiFilter.ALL) }
    var appCategory by remember(userId, selectedKind) { mutableStateOf(AppTypeFilter.ALL) }
    var refreshRevision by remember(userId, selectedKind) { mutableStateOf(0) }
    var expandedId by remember(userId, selectedKind) { mutableStateOf<String?>(null) }
    val guardOwner = remember(userId, selectedKind) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, if (draft != saved) 1 else 0,
        apply = { vm.setSurfaceFilters(draft); true },
        discard = { draft = saved },
    )

    val active by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val catalog = remember(userId, selectedKind, refreshRevision, active) {
        SurfaceCandidateDiscovery.query(context, selectedKind, userId, active)
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
    val types = remember(entries, applicationBased) {
        if (!applicationBased) emptyMap() else entries.associate { (pkg, _) ->
            val system = runCatching {
                @Suppress("DEPRECATION")
                val app = context.packageManager.getApplicationInfo(pkg, 0)
                (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrNull()
            pkg to system
        }
    }
    val visible = entries.filter { (id, title) ->
        (query.isBlank() || title.contains(query.trim(), ignoreCase = true) ||
            id.contains(query.trim(), ignoreCase = true)) &&
            (!applicationBased || when (appCategory) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> types[id] == false
                AppTypeFilter.SYSTEM -> types[id] == true
            }) && surfaceFilterAllows(viewFilter, id in selected, id in locked)
    }
    val visibleIds = visible.take(250).map { it.first }
    val editableIds = visibleIds.filter { it !in locked }

    SurfaceWorkspaceToolbar(
        category = appCategory.name,
        options = if (applicationBased) AppTypeFilter.entries.map {
            it.name to stringResource(it.titleRes())
        } else listOf(AppTypeFilter.ALL.name to stringResource(R.string.common_all)),
        onCategory = { value ->
            appCategory = AppTypeFilter.entries.firstOrNull { it.name == value } ?: AppTypeFilter.ALL
        },
        filter = viewFilter, onFilter = { viewFilter = it },
        appTypeFilter = appCategory,
        onAppTypeFilter = { appCategory = it },
        showAppTypeFilter = applicationBased,
        actions = listOf(
            WorkspaceMenuAction(stringResource(R.string.select_all), {
                draft = draft.withSelected(selectedKind, selected + editableIds)
            }, editableIds.any { it !in selected }),
            WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                draft = draft.withSelected(
                    selectedKind, (selected - editableIds.toSet()) +
                        editableIds.filter { it !in selected }
                )
            }, editableIds.isNotEmpty()),
            WorkspaceMenuAction(stringResource(R.string.select_none), {
                draft = draft.withSelected(selectedKind, selected - editableIds.toSet())
            }, editableIds.any { it in selected }),
        ),
    )
    WorkspaceListSummary(
        title = stringResource(R.string.surface_aosp_selected, selected.size),
        secondary = catalog.description,
    )
    BasicTextField(
        value = query,
        onValueChange = { query = it.take(80) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        decorationBox = { input ->
            Column {
                WorkspaceDetailCaption(stringResource(R.string.surface_aosp_search))
                input()
            }
        },
    )
    visible.take(250).forEach { (id, title) ->
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
            if (selectedKind.supportsSurfaceOrdering()) {
                add(WorkspaceMenuAction(context.getString(R.string.surface_move_earlier), {
                    draft = draft.movePriority(selectedKind, id, -1)
                }))
                add(WorkspaceMenuAction(context.getString(R.string.surface_move_later), {
                    draft = draft.movePriority(selectedKind, id, 1)
                }))
            }
        }
        SurfaceWorkspaceRow(
            title = title, subtitle = id, selected = id in selected,
            enabled = !isLocked, locked = isLocked,
            onToggle = {
                draft = draft.withSelected(selectedKind,
                    if (id in selected) selected - id else selected + id)
            }, actions = actions,
            expanded = expandedId == id,
            onExpand = { expandedId = if (expandedId == id) null else id },
            details = {
                Column(Modifier.fillMaxWidth().padding(
                    start = MaterialUiTokens.pageHorizontal, end = MaterialUiTokens.pageHorizontal,
                    bottom = 8.dp,
                )) {
                    WorkspaceDetailHeading(title = title, kind = selectedKind.name)
                    WorkspaceDetailCaption(id)
                    WorkspaceListActions(actions = actions)
                }
            },
        )
    }
    if (visible.size > 250) WorkspaceAppNote(
        stringResource(R.string.surface_aosp_more, visible.size - 250),
    )
    WorkspaceListActions(actions = listOf(
        WorkspaceMenuAction(stringResource(R.string.surface_scan_refresh), {
            refreshRevision++
        }),
    ))
    if (draft != saved) PendingChangesBar(
        count = 1, onDiscard = { draft = saved },
        onApply = { vm.setSurfaceFilters(draft) },
    )
}
