package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.SurfaceFilterConfig
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.runtime.ChannelInventory
import com.yagay.ListCleaner.runtime.ChannelInventoryStatus
import com.yagay.ListCleaner.runtime.RegisteredChannelRow
import com.yagay.ListCleaner.runtime.NotificationCleanupController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * One candidate source for both channel rows and bulk actions. Saved rules
 * whose channels were not returned by a restricted/failed scan remain editable.
 */
internal fun notificationRuleChannelRows(
    packageName: String,
    discovered: List<RegisteredChannelRow>,
    hidden: Set<String>,
    locked: Set<String>,
): List<RegisteredChannelRow> {
    val observedIds = discovered.map { it.channelId }.toSet()
    val retainedIds = (hidden + locked).mapNotNull { key ->
        key.substringAfter('|').takeIf {
            key.substringBefore('|') == packageName && it.isNotBlank()
        }
    }.toSet()
    return discovered + (retainedIds - observedIds).sorted().map { id ->
        RegisteredChannelRow(packageName, id, id, -1)
    }
}

/**
 * Presentation-only rule categories attached to Components. They are NOT
 * CleanupKind / ComponentName records and must never reach the root pm
 * enable/disable controller. All visual interactions use the Root workspace
 * controls, app rows, item rows and PendingSelectionDraft.
 */
internal enum class ManagedComponentSurface(@StringRes val titleRes: Int) {
    SETTINGS_APPS(R.string.component_settings_apps),
    NOTIFICATIONS(R.string.component_notifications),
}

internal enum class ManagedComponentRuleType { SETTINGS_APP, NOTIFICATION_APP, CHANNEL, LEGACY_CATEGORY }

internal data class ManagedComponentRule(
    val type: ManagedComponentRuleType,
    val packageName: String,
    val key: String,
    val title: String,
    val detail: String,
) {
    val id: String get() = "${type.name}|$packageName|$key"
}

internal fun managedRuleHidden(config: SurfaceFilterConfig, item: ManagedComponentRule): Boolean =
    when (item.type) {
        ManagedComponentRuleType.SETTINGS_APP ->
            item.packageName in config.selected(SurfaceKind.SETTINGS_APPS)
        ManagedComponentRuleType.NOTIFICATION_APP ->
            item.packageName in config.selected(SurfaceKind.NOTIFICATION_SHADE)
        ManagedComponentRuleType.CHANNEL ->
            config.isChannelHidden(item.packageName, item.key)
        ManagedComponentRuleType.LEGACY_CATEGORY ->
            item.key in config.hiddenNotificationCategories
    }

internal fun managedRuleLocked(config: SurfaceFilterConfig, item: ManagedComponentRule): Boolean =
    when (item.type) {
        ManagedComponentRuleType.SETTINGS_APP ->
            item.packageName in config.locked(SurfaceKind.SETTINGS_APPS)
        ManagedComponentRuleType.NOTIFICATION_APP ->
            item.packageName in config.locked(SurfaceKind.NOTIFICATION_SHADE)
        ManagedComponentRuleType.CHANNEL ->
            config.isChannelLocked(item.packageName, item.key)
        ManagedComponentRuleType.LEGACY_CATEGORY -> false
    }

internal fun withManagedRuleHidden(
    config: SurfaceFilterConfig, item: ManagedComponentRule, hidden: Boolean,
): SurfaceFilterConfig = when (item.type) {
    ManagedComponentRuleType.SETTINGS_APP, ManagedComponentRuleType.NOTIFICATION_APP -> {
        val kind = if (item.type == ManagedComponentRuleType.SETTINGS_APP)
            SurfaceKind.SETTINGS_APPS else SurfaceKind.NOTIFICATION_SHADE
        val selected = config.selected(kind)
        config.withSelected(kind,
            if (hidden) selected + item.packageName else selected - item.packageName)
    }
    ManagedComponentRuleType.CHANNEL ->
        config.withChannelHidden(item.packageName, item.key, hidden)
    ManagedComponentRuleType.LEGACY_CATEGORY ->
        config.withNotificationCategoryHidden(item.key, hidden)
}

internal fun withManagedRuleLocked(
    config: SurfaceFilterConfig, item: ManagedComponentRule, lock: Boolean,
): SurfaceFilterConfig = when (item.type) {
    ManagedComponentRuleType.SETTINGS_APP, ManagedComponentRuleType.NOTIFICATION_APP -> {
        val kind = if (item.type == ManagedComponentRuleType.SETTINGS_APP)
            SurfaceKind.SETTINGS_APPS else SurfaceKind.NOTIFICATION_SHADE
        val locks = config.locked(kind)
        config.withLocked(kind, if (lock) locks + item.packageName else locks - item.packageName)
    }
    ManagedComponentRuleType.CHANNEL ->
        config.withChannelLocked(item.packageName, item.key, lock)
    ManagedComponentRuleType.LEGACY_CATEGORY -> config
}

internal fun applyManagedComponentDraft(
    original: SurfaceFilterConfig,
    items: Collection<ManagedComponentRule>,
    changes: Map<String, Boolean>,
): SurfaceFilterConfig {
    val byId = items.associateBy { it.id }
    return changes.entries.fold(original) { policy, (id, target) ->
        val item = byId[id] ?: return@fold policy
        if (managedRuleLocked(policy, item)) policy
        else withManagedRuleHidden(policy, item, target)
    }
}

@Composable
internal fun ComponentsSurfaceEditor(
    state: MainState,
    vm: MainViewModel,
    surface: ManagedComponentSurface,
    pendingNavigation: PendingNavigationGuard?,
    refreshRegistry: ListsRefreshRegistry?,
    onRootKind: (CleanupKind?) -> Unit,
    onManagedKind: (ManagedComponentSurface) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    val remoteInstalled by vm.surfaceInstalledPackages.collectAsStateWithLifecycle()
    val active by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val observed by NotificationCleanupController.observedRows.collectAsStateWithLifecycle()
    val selectionDraft = remember(userId, surface) { PendingSelectionDraft<String>() }
    var viewFilter by rememberSaveable(userId, surface) {
        mutableStateOf(UiPreferencesStore.initialFilter(context, "components"))
    }
    var appTypeFilter by rememberSaveable(userId, surface) {
        mutableStateOf(UiPreferencesStore.initialAppType(context, "components"))
    }
    var expandedApp by rememberSaveable(userId, surface) { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable(userId, surface) { mutableStateOf(false) }
    var scanRevision by remember(userId, surface) { mutableIntStateOf(0) }
    RegisterListsRefresh(refreshRegistry) { scanRevision++ }
    val isNotification = surface == ManagedComponentSurface.NOTIFICATIONS
    val currentUser = android.os.Process.myUid() / 100000

    val installedPackages = remember(userId, remoteInstalled) {
        if (userId != currentUser) remoteInstalled.orEmpty().toList()
        else runCatching {
            context.packageManager.getInstalledApplications(0).map { it.packageName }
        }.getOrDefault(emptyList())
    }
    val notificationPackages = remember(userId, installedPackages, active, observed, saved) {
        (installedPackages +
            active.filter { it.userId == userId }.map { it.packageName } +
            observed.filter { it.userId == userId }.map { it.packageName } +
            saved.selected(SurfaceKind.NOTIFICATION_SHADE) +
            saved.locked(SurfaceKind.NOTIFICATION_SHADE) +
            (saved.hiddenNotificationChannels + saved.lockedNotificationChannels)
                .map { it.substringBefore('|') }).distinct().sorted()
    }
    var inventories by remember(userId, surface) {
        mutableStateOf<Map<String, ChannelInventory>>(emptyMap())
    }
    var scanning by remember(userId, surface) { mutableStateOf(false) }
    var scannedCount by remember(userId, surface) { mutableIntStateOf(0) }
    LaunchedEffect(userId, surface, notificationPackages, scanRevision) {
        if (!isNotification) return@LaunchedEffect
        scanning = true
        scannedCount = 0
        inventories = emptyMap()
        try {
            NotificationCleanupController.invalidateChannelScanCache(userId)
            for (batch in notificationPackages.chunked(32)) {
                coroutineContext.ensureActive()
                val next = NotificationCleanupController.channelsForPackages(
                    batch, userId, context.applicationContext)
                coroutineContext.ensureActive()
                inventories = inventories + next
                scannedCount += batch.size
            }
        } finally {
            scanning = false
        }
    }

    var settingsCatalog by remember(userId) {
        mutableStateOf(SurfaceCandidateDiscovery.Result(emptyList(), ""))
    }
    var settingsLoading by remember(userId) { mutableStateOf(false) }
    LaunchedEffect(userId, surface, remoteInstalled, scanRevision) {
        if (isNotification) return@LaunchedEffect
        settingsLoading = true
        try {
            settingsCatalog = withContext(Dispatchers.IO) {
                SurfaceCandidateDiscovery.query(context, SurfaceKind.SETTINGS_APPS,
                    userId, emptyList(), remoteInstalled)
            }
        } finally { settingsLoading = false }
    }

    val packageNames = if (isNotification) notificationPackages.filter { pkg ->
        inventories[pkg]?.items?.isNotEmpty() == true ||
            pkg in saved.selected(SurfaceKind.NOTIFICATION_SHADE) ||
            pkg in saved.locked(SurfaceKind.NOTIFICATION_SHADE) ||
            (saved.hiddenNotificationChannels + saved.lockedNotificationChannels).any {
                it.substringBefore('|') == pkg
            }
    } else {
        (settingsCatalog.items.map { it.first } +
            saved.selected(SurfaceKind.SETTINGS_APPS) +
            saved.locked(SurfaceKind.SETTINGS_APPS) +
            saved.priorities(SurfaceKind.SETTINGS_APPS)).distinct()
    }

    val catalogLabels = settingsCatalog.items.toMap()
    val labels = remember(packageNames, catalogLabels, userId) {
        packageNames.associateWith { pkg ->
            catalogLabels[pkg] ?: runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }
    }
    var systemApps by remember(userId, surface) {
        mutableStateOf<Map<String, Boolean?>>(emptyMap())
    }
    LaunchedEffect(userId, surface, packageNames) {
        systemApps = withContext(Dispatchers.IO) {
            SurfaceProfileAppTypes.classify(context, userId, packageNames)
        }
    }

    val byPackage = packageNames.associateWith { pkg ->
        if (!isNotification) listOf(ManagedComponentRule(
            ManagedComponentRuleType.SETTINGS_APP, pkg, pkg,
            context.getString(R.string.component_surface_settings_rule), pkg,
        )) else {
            val app = ManagedComponentRule(
                ManagedComponentRuleType.NOTIFICATION_APP, pkg, pkg,
                context.getString(R.string.component_surface_app_rule), pkg,
            )
            val channels = notificationRuleChannelRows(
                pkg, inventories[pkg]?.items.orEmpty(),
                saved.hiddenNotificationChannels, saved.lockedNotificationChannels,
            ).map { channel ->
                ManagedComponentRule(
                    ManagedComponentRuleType.CHANNEL, pkg, channel.channelId,
                    channel.label, channel.channelId,
                )
            }
            listOf(app) + channels
        }
    }

    // Retained global category rules must not become hidden, uneditable policies
    // after the old Lists page is removed.
    val legacyRules = if (isNotification) saved.hiddenNotificationCategories.sorted().map { category ->
        ManagedComponentRule(ManagedComponentRuleType.LEGACY_CATEGORY,
            "__legacy__", category, category, category)
    } else emptyList()
    val allRules = byPackage.values.flatten() + legacyRules
    val getEffective: (ManagedComponentRule) -> Boolean = { item ->
        selectionDraft.selected(item.id, managedRuleHidden(saved, item))
    }
    val isEditable: (ManagedComponentRule) -> Boolean = { item ->
        !managedRuleLocked(saved, item)
    }
    val applyChanges: () -> Boolean = {
        val latest = vm.surfaceFilters.value
        val updated = applyManagedComponentDraft(latest, allRules, selectionDraft.changes)
        if (selectionDraft.count == 0) true else if (updated == latest) {
            selectionDraft.discard()
            true
        } else if (vm.setSurfaceFilters(updated)) {
            selectionDraft.discard()
            true
        } else false
    }
    val owner = remember(userId, surface) { Any() }
    RegisterPendingNavigation(pendingNavigation, owner, selectionDraft.count,
        applyChanges, selectionDraft::discard, rootEditor = true)

    val request: (() -> Unit) -> Unit = { action ->
        if (pendingNavigation != null) pendingNavigation.request(action)
        else if (selectionDraft.count == 0) action()
        else { selectionDraft.discard(); action() }
    }
    BackHandler(enabled = !sorting) { request { onRootKind(null) } }

    val hasQuery = state.query.isNotBlank()
    val groups = byPackage.entries.mapNotNull { (pkg, rows) ->
        val appMatches = !hasQuery || pkg.contains(state.query, ignoreCase = true) ||
            labels[pkg].orEmpty().contains(state.query, ignoreCase = true)
        val visibleRows = rows.filter { item ->
            (appMatches || item.title.contains(state.query, ignoreCase = true) ||
                item.detail.contains(state.query, ignoreCase = true)) &&
                when (viewFilter) {
                    UiFilter.ALL -> true
                    UiFilter.SHOW_SELECTED -> getEffective(item)
                    UiFilter.HIDE_SELECTED -> !getEffective(item)
                    UiFilter.LOCKED -> managedRuleLocked(saved, item)
                }
        }
        if (visibleRows.isEmpty() || !when (appTypeFilter) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> systemApps[pkg] != true
                AppTypeFilter.SYSTEM -> systemApps[pkg] == true
            }) null
        else pkg to visibleRows
    }.sortedWith(
        if (!isNotification && saved.priorities(SurfaceKind.SETTINGS_APPS).isNotEmpty()) {
            val rank = saved.priorities(SurfaceKind.SETTINGS_APPS).withIndex()
                .associate { it.value to it.index }
            compareBy<Pair<String, List<ManagedComponentRule>>> {
                rank[it.first] ?: Int.MAX_VALUE
            }.thenBy { labels[it.first].orEmpty().lowercase() }
        } else when (UiPreferencesRuntime.current.appSort) {
            UiAppSort.SELECTED_FIRST -> compareBy<Pair<String, List<ManagedComponentRule>>> {
                it.second.count(getEffective) != it.second.size
            }.thenBy { labels[it.first].orEmpty().lowercase() }
            UiAppSort.NAME -> compareBy { labels[it.first].orEmpty().lowercase() }
            else -> compareBy { it.first }
        },
    )
    val visible = groups.flatMap { it.second } + legacyRules.filter { item ->
        (!hasQuery || item.title.contains(state.query, ignoreCase = true)) &&
            when (viewFilter) {
                UiFilter.ALL, UiFilter.SHOW_SELECTED -> getEffective(item)
                UiFilter.HIDE_SELECTED -> !getEffective(item)
                UiFilter.LOCKED -> false
            }
    }
    val editable = visible.filter(isEditable)

    val confirmation = rememberSelectionConfirmationController()
    SelectionConfirmationDialog(confirmation)
    var pendingBulk by remember(userId, surface) {
        mutableStateOf<SurfaceBulkOperation?>(null)
    }
    pendingBulk?.let { operation ->
        val action = stringResource(when (operation) {
            SurfaceBulkOperation.ALL -> R.string.select_all
            SurfaceBulkOperation.NONE -> R.string.select_none
            SurfaceBulkOperation.INVERT -> R.string.invert_selection
        })
        AdaptiveAlertDialog(
            onDismissRequest = { pendingBulk = null },
            title = { AdaptiveText(stringResource(R.string.root_batch_confirm_title)) },
            text = { AdaptiveText(stringResource(R.string.root_batch_confirmation_text, action)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    val targets = editable
                    val chosen = targets.filterTo(hashSetOf()) { getEffective(it) }.mapTo(hashSetOf()) { it.id }
                    val result = applySurfaceBulkSelection(chosen, targets.map { it.id }, operation)
                    targets.forEach { item ->
                        selectionDraft.set(item.id, managedRuleHidden(saved, item), item.id in result)
                    }
                    pendingBulk = null
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { pendingBulk = null }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (sorting && !isNotification) {
        ComponentsSettingsOrderEditor(
            vm = vm, userId = userId, saved = saved,
            candidates = packageNames, labels = labels,
            pendingNavigation = pendingNavigation,
            onExit = { sorting = false },
            onRootKind = onRootKind,
            onManagedKind = onManagedKind,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selectionDraft.count > 0) 90.dp else 16.dp),
        ) {
            stickyHeader(key = "controls") {
                PageFilterSection {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        WorkspaceCategoryFilterRow(
                            category = {
                                ComponentCategoryPicker(
                                    selected = null, managedSurface = surface,
                                    onSelected = { newKind -> request { onRootKind(newKind) } },
                                    onManagedSurface = { next -> request { onManagedKind(next) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            beforeFilter = {
                                WorkspaceSortModeAction(
                                    supported = !isNotification,
                                    onOpen = { request { sorting = true } },
                                )
                            },
                            filter = {
                                WorkspaceFilterBar(
                                    appTypeFilter = appTypeFilter,
                                    onAppTypeFilter = {
                                        appTypeFilter = it
                                        UiPreferencesStore.rememberAppType(context, it, "components")
                                    },
                                    viewFilter = viewFilter,
                                    onViewFilter = {
                                        viewFilter = it
                                        UiPreferencesStore.rememberFilter(context, it, "components")
                                    },
                                    viewTitle = {
                                        stringResource(when (it) {
                                            UiFilter.ALL -> R.string.common_all
                                            UiFilter.SHOW_SELECTED -> R.string.root_filter_disabled
                                            UiFilter.HIDE_SELECTED -> R.string.root_filter_enabled
                                            UiFilter.LOCKED -> R.string.filter_locked
                                        })
                                    },
                                )
                            },
                            afterFilter = { WorkspaceListActions(actions = listOf(
                                WorkspaceMenuAction(stringResource(R.string.select_all), {
                                    pendingBulk = SurfaceBulkOperation.ALL
                                }, editable.any { !getEffective(it) }),
                                WorkspaceMenuAction(stringResource(R.string.select_none), {
                                    pendingBulk = SurfaceBulkOperation.NONE
                                }, editable.any { getEffective(it) }),
                                WorkspaceMenuAction(stringResource(R.string.invert_selection), {
                                    pendingBulk = SurfaceBulkOperation.INVERT
                                }, editable.isNotEmpty()),
                            )) },
                        )
                        AdaptiveHorizontalDivider()
                    }
                }
            }
            item(key = "summary") {
                WorkspaceListSummary(
                    title = stringResource(R.string.component_surface_hidden_summary,
                        visible.count(getEffective), visible.size),
                    secondary = stringResource(R.string.component_surface_hide_note),
                )
                if (scanning) WorkspaceAppNote(stringResource(
                    R.string.component_surface_scanning, scannedCount, notificationPackages.size))
                if (settingsLoading) WorkspaceProgress()
            }
            groups.forEach { (pkg, rows) ->
                val expanded = expandedApp == pkg
                val current = rows.count(getEffective)
                val editableItems = rows.filter(isEditable)
                item(key = "app|$pkg") {
                    val icon = remember(pkg, userId) {
                        if (userId != currentUser) null else runCatching {
                            val drawable = context.packageManager.getApplicationIcon(pkg).mutate()
                            val px = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
                                drawable.setBounds(0, 0, px, px)
                                drawable.draw(Canvas(it))
                            }
                        }.getOrNull()
                    }
                    val lockCount = rows.count { managedRuleLocked(saved, it) }
                    WorkspaceAppRow(
                        icon = icon, title = labels[pkg] ?: pkg,
                        packageName = pkg,
                        subtitle = stringResource(R.string.component_surface_hidden_summary,
                            current, rows.size),
                        expanded = expanded,
                        lockState = when {
                            lockCount == rows.size -> BulkLockState.FULL
                            lockCount > 0 -> BulkLockState.PARTIAL
                            else -> BulkLockState.NONE
                        },
                        onExpand = { expandedApp = if (expanded) null else pkg },
                        onLock = {
                            var next = vm.surfaceFilters.value
                            rows.forEach { next = withManagedRuleLocked(next, it, true) }
                            vm.setSurfaceFilters(next)
                        },
                        onUnlock = {
                            var next = vm.surfaceFilters.value
                            rows.forEach { next = withManagedRuleLocked(next, it, false) }
                            vm.setSurfaceFilters(next)
                        },
                        selection = {
                            WorkspaceAppSelectionCheckbox(
                                state = listSelectionState(current, rows.size),
                                enabled = editableItems.isNotEmpty(),
                                onClick = {
                                    val hide = current != rows.size
                                    val systemCount = if (hide && systemApps[pkg] == true) 1 else 0
                                    confirmation.requestForSystemSelection(
                                        labels[pkg] ?: pkg, systemCount, SystemSelectionRisk.ENTRY
                                    ) {
                                        editableItems.forEach { item ->
                                            selectionDraft.set(item.id,
                                                managedRuleHidden(saved, item), hide)
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
                if (expanded) itemsIndexed(rows, key = { _, item -> item.id }) { index, item ->
                    val selected = getEffective(item)
                    val locked = managedRuleLocked(saved, item)
                    WorkspaceDetailRow(
                        position = if (index == rows.lastIndex) ListRowPosition.LAST else ListRowPosition.MIDDLE,
                        checked = selected, enabled = !locked, locked = locked,
                        onToggle = {
                            confirmation.requestForSystemSelection(
                                item.title, if (!selected && systemApps[pkg] == true) 1 else 0,
                                SystemSelectionRisk.ENTRY
                            ) {
                                selectionDraft.set(item.id,
                                    managedRuleHidden(saved, item), !selected)
                            }
                        },
                        content = {
                            WorkspaceDetailHeading(item.title, stringResource(when (item.type) {
                                ManagedComponentRuleType.SETTINGS_APP -> R.string.component_settings_apps
                                ManagedComponentRuleType.NOTIFICATION_APP -> R.string.component_surface_app_rule
                                ManagedComponentRuleType.CHANNEL -> R.string.component_surface_channel_rule
                                ManagedComponentRuleType.LEGACY_CATEGORY -> R.string.component_surface_legacy_categories
                            }))
                            WorkspaceDetailCaption(item.detail)
                            if (item.type == ManagedComponentRuleType.CHANNEL &&
                                inventories[pkg]?.status != ChannelInventoryStatus.COMPLETE) {
                                WorkspaceDetailStatus(stringResource(R.string.component_surface_unverified))
                            }
                        },
                        trailing = { menu, setMenu ->
                            WorkspaceDetailActionsMenu(
                                onPreview = null,
                                locked = locked, lockToggleEnabled = true,
                                onLock = { vm.setSurfaceFilters(withManagedRuleLocked(vm.surfaceFilters.value, item, true)) },
                                onUnlock = { vm.setSurfaceFilters(withManagedRuleLocked(vm.surfaceFilters.value, item, false)) },
                                expanded = menu, onExpandedChange = setMenu,
                            )
                        },
                    )
                }
            }
            if (legacyRules.isNotEmpty()) {
                item(key = "legacy-heading") {
                    WorkspaceListSummary(
                        title = stringResource(R.string.component_surface_legacy_categories),
                        secondary = stringResource(R.string.component_surface_legacy_note),
                    )
                }
                items(legacyRules, key = { it.id }) { item ->
                    WorkspaceDetailRow(
                        position = ListRowPosition.SINGLE,
                        checked = getEffective(item), locked = false,
                        onToggle = {
                            selectionDraft.set(item.id, managedRuleHidden(saved, item), !getEffective(item))
                        },
                        content = {
                            WorkspaceDetailHeading(notificationCategoryLabel(item.key.takeUnless {
                                it == "(other)"
                            }), stringResource(R.string.component_surface_legacy_categories))
                        },
                    )
                }
            }
            if (groups.isEmpty() && legacyRules.isEmpty() && !scanning && !settingsLoading) {
                item(key = "empty") {
                    WorkspaceEmptyState(
                        message = stringResource(R.string.root_empty_filtered),
                        clearLabel = stringResource(R.string.root_clear_filters),
                        onClear = {
                            viewFilter = UiFilter.ALL
                            appTypeFilter = AppTypeFilter.ALL
                            vm.setQuery("")
                        },
                    )
                }
            }
        }
        PendingChangesBar(
            count = selectionDraft.count,
            onDiscard = selectionDraft::discard,
            onApply = { applyChanges() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
