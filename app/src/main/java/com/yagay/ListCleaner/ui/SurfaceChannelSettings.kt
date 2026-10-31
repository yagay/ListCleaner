package com.yagay.ListCleaner.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.domain.AppTypeFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.runtime.ActiveNotificationRow
import com.yagay.ListCleaner.runtime.ChannelInventory
import com.yagay.ListCleaner.runtime.ChannelInventoryStatus
import com.yagay.ListCleaner.runtime.NotificationCleanupController
import kotlinx.coroutines.launch

/** The same flat list and category/filter controls as Entry/Root cleanup. */
@Composable
internal fun SurfaceChannelSettings(
    vm: MainViewModel,
    userId: Int,
    observed: List<ActiveNotificationRow>,
    pendingNavigation: PendingNavigationGuard? = null,
    onSearchStateChanged: ((SurfaceWorkspaceUiState?) -> Unit)? = null,
    refreshRegistry: ListsRefreshRegistry? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    val remoteInstalled by vm.surfaceInstalledPackages.collectAsStateWithLifecycle()
    val observedRows by NotificationCleanupController.observedRows.collectAsStateWithLifecycle()
    var draftBase by remember(userId) { mutableStateOf(saved) }
    var draft by remember(userId) { mutableStateOf(saved) }
    LaunchedEffect(userId, saved) {
        if (draftBase != saved) {
            draft = rebaseSurfaceDraft(draftBase, draft, saved)
            draftBase = saved
        }
    }
    val uiState = rememberSurfaceWorkspaceUiState(
        userId, "channels",
        UiPreferencesStore.initialFilter(context, "lists_channels"),
        UiPreferencesStore.initialAppType(context, "lists_channels"),
    )
    RegisterSurfaceWorkspaceSearch(uiState, onSearchStateChanged)
    val pkg = uiState.searchQuery
    val viewFilter = uiState.viewFilter
    val appTypeFilter = uiState.appTypeFilter
    var packageInventories by remember(userId) { mutableStateOf<Map<String, ChannelInventory>>(emptyMap()) }
    var scanProgress by remember(userId) { mutableStateOf(0) }
    var scanning by remember(userId) { mutableStateOf(false) }
    var scanRevision by remember(userId) { mutableStateOf(0) }
    RegisterListsRefresh(refreshRegistry, enabled = !scanning) { scanRevision++ }
    val localUserId = android.os.Process.myUid() / 100000
    val installedPackages = remember(userId, remoteInstalled) {
        if (userId != localUserId) remoteInstalled.orEmpty().toList()
        else runCatching {
            context.packageManager.getInstalledApplications(0).map { it.packageName }
        }.getOrDefault(emptyList())
    }

    val guardOwner = remember(userId) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, surfacePendingChangeCount(saved, draft),
        apply = { vm.setSurfaceFilters(rebaseSurfaceDraft(draftBase, draft, saved)) },
        discard = { draft = saved; draftBase = saved },
    )

    val observedPackages = (installedPackages +
        observed.filter { it.userId == userId }.map { it.packageName } +
        observedRows.filter { it.userId == userId }.map { it.packageName }).distinct().sorted()
    // Scan once on entering the page, independent of expansion. Keep one root
    // probe at a time and publish partial results without blocking composition.
    LaunchedEffect(userId, observedPackages, scanRevision) {
        scanning = true
        scanProgress = 0
        packageInventories = emptyMap()
        NotificationCleanupController.invalidateChannelScanCache(userId)
        val targets = observedPackages
        try {
            for (batch in targets.chunked(32)) {
                coroutineContext.ensureActive()
                val found = NotificationCleanupController.channelsForPackages(batch, userId)
                coroutineContext.ensureActive()
                packageInventories = packageInventories + found
                scanProgress += batch.size
            }
        } finally {
            scanning = false
        }
    }
    var appTypes by remember(userId) { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    LaunchedEffect(userId, observedPackages) {
        appTypes = withContext(Dispatchers.IO) {
            SurfaceProfileAppTypes.classify(context, userId, observedPackages)
        }
    }
    val packageLabels = remember(userId, observedPackages) {
        observedPackages.associateWith { name ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(name, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(name)
        }
    }
    val visiblePackages = observedPackages.filter { name ->
        pkg.isBlank() || name.contains(pkg.trim(), ignoreCase = true) ||
            packageLabels[name].orEmpty().contains(pkg.trim(), ignoreCase = true) ||
            packageInventories[name]?.items.orEmpty().any {
                it.channelId.contains(pkg.trim(), ignoreCase = true) ||
                    it.label.contains(pkg.trim(), ignoreCase = true)
            }
    }.filter { name ->
        if (appTypeFilter == AppTypeFilter.ALL) true else {
            val isSystem = appTypes[name]
            when (appTypeFilter) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> isSystem != true
                AppTypeFilter.SYSTEM -> isSystem == true
            }
        }
    }
    // Filter at the app level, like Entry/Component: the rows and the
    // bulk-action scope must be based on exactly the same visible apps.
    val filteredPackages = visiblePackages.filter { appPackage ->
        val channels = packageInventories[appPackage]?.items.orEmpty()
        when (viewFilter) {
            UiFilter.ALL -> true
            UiFilter.HIDE_SELECTED -> channels.isNotEmpty() &&
                channels.any { !draft.isChannelHidden(appPackage, it.channelId) }
            UiFilter.SHOW_SELECTED -> channels.any {
                draft.isChannelHidden(appPackage, it.channelId)
            }
            UiFilter.LOCKED -> channels.any {
                draft.isChannelLocked(appPackage, it.channelId)
            }
        }
    }
    val displayedPackages = filteredPackages
    val visibleChannels = displayedPackages.flatMap { packageInventories[it]?.items.orEmpty() }
    val editableChannels = visibleChannels.filterNot {
        draft.isChannelLocked(it.packageName, it.channelId)
    }
    val visibleChannelIds = editableChannels.map { it.packageName to it.channelId }.toSet()
    val selectedChannelIds = editableChannels.filter {
        draft.isChannelHidden(it.packageName, it.channelId)
    }.map { it.packageName to it.channelId }.toSet()
    val applyBulkChannels: (SurfaceBulkOperation) -> Unit = { operation ->
        val updated = applySurfaceBulkSelection(selectedChannelIds, visibleChannelIds, operation)
        editableChannels.forEach { channel ->
            val id = channel.packageName to channel.channelId
            if ((id in updated) != (id in selectedChannelIds)) {
                draft = draft.withChannelHidden(channel.packageName, channel.channelId, id in updated)
            }
        }
    }

    ListsEditorScaffold(
        controls = {
            ListsV2Controls(
                category = "",
                options = listOf("" to stringResource(R.string.common_all)),
                onCategory = {},
                filter = viewFilter, onFilter = { uiState.viewFilter = it; UiPreferencesStore.rememberFilter(context, it, "lists_channels") },
                appTypeFilter = appTypeFilter,
                onAppTypeFilter = { uiState.appTypeFilter = it; UiPreferencesStore.rememberAppType(context, it, "lists_channels") },
                showAppTypeFilter = true,
                actions = listOf(
                    WorkspaceMenuAction(stringResource(R.string.select_all), {
                        applyBulkChannels(SurfaceBulkOperation.ALL)
                    }, !scanning && visibleChannelIds.any { it !in selectedChannelIds }),
                    WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                        applyBulkChannels(SurfaceBulkOperation.INVERT)
                    }, !scanning && visibleChannelIds.isNotEmpty()),
                    WorkspaceMenuAction(stringResource(R.string.select_none), {
                        applyBulkChannels(SurfaceBulkOperation.NONE)
                    }, !scanning && selectedChannelIds.isNotEmpty()),
                ),
            )
        },
        summary = {
            WorkspaceListSummary(
                    title = stringResource(R.string.surface_channels_title),
                    secondary = if (scanning) "$scanProgress / ${observedPackages.size}"
                        else stringResource(R.string.surface_channels_help),
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
        if (displayedPackages.isEmpty() && !scanning) item(key = "empty") {
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
        items(displayedPackages, key = { it }) { appPackage ->
        val appLabel = packageLabels[appPackage] ?: appPackage
        val appIcon = remember(appPackage, userId) {
            if (userId != localUserId) null else runCatching {
                val drawable = context.packageManager.getApplicationIcon(appPackage).mutate()
                val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
                bitmap
            }.getOrNull()
        }
        val found = packageInventories[appPackage]
        val channels = found?.items.orEmpty()
        val selected = channels.isNotEmpty() && channels.all { draft.isChannelHidden(appPackage, it.channelId) }
        val appLocked = channels.isNotEmpty() && channels.all { draft.isChannelLocked(appPackage, it.channelId) }
        val editableChannels = channels.filterNot { draft.isChannelLocked(appPackage, it.channelId) }
        val allEditableSelected = editableChannels.isNotEmpty() &&
            editableChannels.all { draft.isChannelHidden(appPackage, it.channelId) }
        val expandApp: () -> Unit = {
            uiState.toggleExpanded(appPackage)

        }
        WorkspaceAppRow(
            icon = appIcon,
            title = appLabel,
            subtitle = if (found == null || found.status != ChannelInventoryStatus.COMPLETE)
                appPackage else stringResource(R.string.surface_channels_count, channels.size),
            packageName = appPackage,
            expanded = uiState.expandedKey == appPackage,
            lockState = if (appLocked) BulkLockState.FULL else if (channels.any { draft.isChannelLocked(appPackage, it.channelId) }) BulkLockState.PARTIAL else BulkLockState.NONE,
            onExpand = expandApp,
            onLock = { channels.forEach { draft = draft.withChannelLocked(appPackage, it.channelId, true) } },
            onUnlock = { channels.forEach { draft = draft.withChannelLocked(appPackage, it.channelId, false) } },
            selection = {
                WorkspaceAppSelectionCheckbox(
                    state = when {
                        selected -> ToggleableState.On
                        channels.any { draft.isChannelHidden(appPackage, it.channelId) } ->
                            ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    enabled = editableChannels.isNotEmpty() && !scanning,
                    onClick = {
                        editableChannels.forEach {
                            draft = draft.withChannelHidden(appPackage, it.channelId, !allEditableSelected)
                        }
                    },
                )
            },
            menuActions = listOf(
                WorkspaceMenuAction(
                    stringResource(R.string.surface_lock_target),
                    { channels.forEach { draft = draft.withChannelLocked(appPackage, it.channelId, true) } },
                    !scanning && channels.isNotEmpty() && !appLocked,
                ),
                WorkspaceMenuAction(
                    stringResource(R.string.surface_unlock_item),
                    { channels.forEach { draft = draft.withChannelLocked(appPackage, it.channelId, false) } },
                    !scanning && channels.any { draft.isChannelLocked(appPackage, it.channelId) },
                ),
            ) + if (userId == android.os.Process.myUid() / 100000) listOf(
                WorkspaceMenuAction(stringResource(R.string.surface_channel_settings), {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, appPackage)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }),
            ) else emptyList(),
            expandedContent = {
                if (found != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal)) {
                    WorkspaceAppNote(stringResource(when (found.status) {
                        ChannelInventoryStatus.COMPLETE -> R.string.surface_channels_full
                        ChannelInventoryStatus.OBSERVED_ONLY -> R.string.surface_channels_restricted
                        ChannelInventoryStatus.DISCONNECTED -> R.string.surface_wait_for_listener
                    }))
                    }
                    channels.groupBy { channel ->
                        channel.groupId.orEmpty()
                    }.toSortedMap().forEach { (_, groupChannels) ->
                        val groupTitle = groupChannels.firstOrNull()?.groupLabel
                        if (!groupTitle.isNullOrBlank()) {
                            Column(Modifier.fillMaxWidth().padding(
                                start = MaterialUiTokens.pageHorizontal + 24.dp,
                                end = MaterialUiTokens.pageHorizontal,
                            )) {
                                WorkspaceDetailHeading(
                                    title = groupTitle,
                                    kind = groupChannels.size.toString(),
                                )
                            }
                        }
                        groupChannels.forEachIndexed { index, channel ->
                            val checked = draft.isChannelHidden(appPackage, channel.channelId)
                            WorkspaceDetailRow(
                                position = if (index == groupChannels.lastIndex)
                                    ListRowPosition.LAST else ListRowPosition.MIDDLE,
                                checked = checked,
                                locked = draft.isChannelLocked(appPackage, channel.channelId),
                                enabled = !draft.isChannelLocked(appPackage, channel.channelId),
                                onToggle = {
                                    draft = draft.withChannelHidden(
                                        appPackage, channel.channelId, !checked)
                                },
                                content = {
                                    WorkspaceDetailHeading(channel.label, "")
                                    WorkspaceDetailCaption(channel.channelId)
                                },
                                trailing = { menuOpen, setMenuOpen ->
                                    WorkspaceFlatOverflowMenu(
                                        actions = listOf(
                                            WorkspaceMenuAction(
                                                stringResource(if (draft.isChannelLocked(appPackage, channel.channelId)) R.string.surface_unlock_item else R.string.surface_lock_target),
                                                { draft = draft.withChannelLocked(appPackage, channel.channelId, !draft.isChannelLocked(appPackage, channel.channelId)) },
                                            ),
                                        ) + if (userId == android.os.Process.myUid() / 100000)
                                            listOf(WorkspaceMenuAction(
                                                stringResource(R.string.surface_channel_settings), {
                                                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                        .putExtra(Settings.EXTRA_APP_PACKAGE, appPackage)
                                                        .putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    runCatching { context.startActivity(intent) }
                                                },
                                            )) else emptyList(),
                                        expanded = menuOpen,
                                        onExpandedChange = setMenuOpen,
                                        contentDescription = stringResource(R.string.common_more),
                                    )
                                },
                            )
                        }
                    }
                }
            },
        )
    }
    }
}
