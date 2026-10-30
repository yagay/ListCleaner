package com.yagay.ListCleaner.ui

import android.app.Notification
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.domain.AppTypeFilter
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.runtime.ActiveNotificationRow
import com.yagay.ListCleaner.runtime.NotificationCleanupController
import com.yagay.ListCleaner.runtime.NotificationScanStatus

/** Grouping is a browsing choice, never a different notification dismissal backend. */
internal enum class ActiveNotificationGrouping { ALL, APPLICATION, CHANNEL, CATEGORY }

/** Stable categories shown even when no matching notification is currently posted.
 * "Push" is not an AOSP category; it is the explicit unclassified bucket.
 * Updates cannot reliably be inferred from an Android notification category.
 */
internal val notificationCategoryIds = listOf(
    Notification.CATEGORY_PROMO, Notification.CATEGORY_MESSAGE,
    Notification.CATEGORY_EMAIL, Notification.CATEGORY_SOCIAL,
    Notification.CATEGORY_PROGRESS, Notification.CATEGORY_REMINDER,
    Notification.CATEGORY_EVENT, Notification.CATEGORY_TRANSPORT,
    Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
    Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM, "(other)"
)
internal fun notificationCategoryKey(row: ActiveNotificationRow): String =
    row.category?.takeIf { it in notificationCategoryIds } ?: "(other)"

internal data class ActiveNotificationGroup(
    val key: String,
    val rows: List<ActiveNotificationRow>,
)

internal fun groupActiveNotifications(
    rows: List<ActiveNotificationRow>,
    grouping: ActiveNotificationGrouping,
): List<ActiveNotificationGroup> {
    if (grouping == ActiveNotificationGrouping.ALL) {
        return rows.map { ActiveNotificationGroup(it.key, listOf(it)) }
    }
    val groups = rows.groupBy { row ->
        when (grouping) {
            ActiveNotificationGrouping.ALL -> row.key
            ActiveNotificationGrouping.APPLICATION -> row.packageName
            ActiveNotificationGrouping.CHANNEL -> row.packageName + "|" + row.channelId
            ActiveNotificationGrouping.CATEGORY -> notificationCategoryKey(row)
        }
    }
    return groups.entries.sortedBy { it.key }
        .map { ActiveNotificationGroup(it.key, it.value) }
}

@Composable
private fun notificationCategoryLabel(value: String?): String = when (value) {
    Notification.CATEGORY_MESSAGE -> stringResource(R.string.notification_category_message)
    Notification.CATEGORY_EMAIL -> stringResource(R.string.notification_category_email)
    Notification.CATEGORY_CALL -> stringResource(R.string.notification_category_call)
    Notification.CATEGORY_ALARM -> stringResource(R.string.notification_category_alarm)
    Notification.CATEGORY_SERVICE -> stringResource(R.string.notification_category_service)
    Notification.CATEGORY_PROGRESS -> stringResource(R.string.notification_category_progress)
    Notification.CATEGORY_REMINDER -> stringResource(R.string.notification_category_reminder)
    Notification.CATEGORY_EVENT -> stringResource(R.string.notification_category_event)
    Notification.CATEGORY_SOCIAL -> stringResource(R.string.notification_category_social)
    Notification.CATEGORY_TRANSPORT -> stringResource(R.string.notification_category_transport)
    Notification.CATEGORY_PROMO -> stringResource(R.string.notification_category_promo)
    Notification.CATEGORY_SYSTEM -> stringResource(R.string.notification_category_system)
    else -> stringResource(R.string.notification_category_other)
}

@Composable
internal fun ActiveNotificationManagement(
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null,
    initialGrouping: ActiveNotificationGrouping = ActiveNotificationGrouping.APPLICATION,
) {
    val context = LocalContext.current
    val rows by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val observedRows by NotificationCleanupController.observedRows.collectAsStateWithLifecycle()
    val scan by NotificationCleanupController.scan.collectAsStateWithLifecycle()
    val savedCategories by vm.surfaceFilters.collectAsStateWithLifecycle()
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    var draftCategories by remember(userId) { mutableStateOf<Set<String>?>(null) }
    var draftChannels by remember(userId) { mutableStateOf<Set<String>?>(null) }
    val connected by NotificationCleanupController.connected.collectAsStateWithLifecycle()
    val categoryMode = initialGrouping == ActiveNotificationGrouping.CATEGORY
    val hiddenCategories = draftCategories ?: savedCategories.hiddenNotificationCategories
    val hiddenChannels = draftChannels ?: savedCategories.hiddenNotificationChannels
    val categoryChanges = (hiddenCategories - savedCategories.hiddenNotificationCategories).size +
        (savedCategories.hiddenNotificationCategories - hiddenCategories).size
    val channelChanges = (hiddenChannels - savedCategories.hiddenNotificationChannels).size +
        (savedCategories.hiddenNotificationChannels - hiddenChannels).size
    val applyCategoryChanges: () -> Unit = {
        vm.setSurfaceFilters(savedCategories.copy(
            hiddenNotificationCategories = hiddenCategories,
            hiddenNotificationChannels = hiddenChannels,
        ).validated())
        draftCategories = null
        draftChannels = null
    }
    val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    var grouping by remember(userId, initialGrouping) { mutableStateOf(initialGrouping) }
    var categorySelection by remember(userId, initialGrouping) { mutableStateOf<String?>(null) }
    val uiState = rememberSurfaceWorkspaceUiState(
        userId, "notifications_${initialGrouping.name}",
        UiPreferencesStore.initialFilter(context, "lists_notifications_${initialGrouping.name}"),
        UiPreferencesStore.initialAppType(context, "lists_notifications_${initialGrouping.name}"),
    )
    val filter = uiState.viewFilter
    val appTypeFilter = uiState.appTypeFilter
    var selectedKeys by remember(userId) { mutableStateOf(emptySet<String>()) }
    var dismissalCount by remember(userId) { mutableStateOf<Int?>(null) }
    val sameProfile = rows.filter { it.userId == userId }
    val available = sameProfile.filterNot { it.protected }
    val selectableKeys = available.mapTo(hashSetOf()) { it.key }
    val chosen = selectedKeys.intersect(selectableKeys)
    val inventoryRows = if (categoryMode) observedRows.filter { it.userId == userId } else sameProfile
    val filteredInventoryRows = inventoryRows.filter { row ->
        if (appTypeFilter == AppTypeFilter.ALL) true else {
            val system = runCatching {
                val app = context.packageManager.getApplicationInfo(row.packageName, 0)
                (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrNull()
            when (appTypeFilter) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> system == false
                AppTypeFilter.SYSTEM -> system == true
            }
        }
    }
    val groups = groupActiveNotifications(filteredInventoryRows, grouping)
    val displayGroups = if (initialGrouping == ActiveNotificationGrouping.CATEGORY) {
        notificationCategoryIds.map { id ->
            ActiveNotificationGroup(id, groups.firstOrNull { it.key == id }?.rows.orEmpty())
        }
    } else groups
    val shownGroups = displayGroups.filter { group ->
        if (initialGrouping == ActiveNotificationGrouping.CATEGORY &&
            categorySelection != null && group.key != categorySelection) return@filter false
        val allowed = group.rows.asSequence().filterNot { it.protected }.map { it.key }.toSet()
        val complete = allowed.isNotEmpty() && allowed.all { it in chosen }
        val protected = group.rows.any { it.protected }
        surfaceFilterAllows(filter, if (categoryMode) group.key in hiddenCategories else complete,
            if (categoryMode) false else protected)
    }
    val shownKeys = shownGroups.flatMap { group ->
        group.rows.filterNot { it.protected }.map { it.key }
    }.toSet()
    val guardOwner = remember(userId) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, if (categoryMode) {
            categoryChanges + channelChanges
        } else chosen.size,
        apply = {
            if (categoryMode) {
                applyCategoryChanges()
            } else {
                dismissalCount = NotificationCleanupController.dismiss(chosen)
                selectedKeys = emptySet()
            }
            true
        },
        discard = {
            if (categoryMode) { draftCategories = null; draftChannels = null }
            else selectedKeys = emptySet()
        },
    )

    if (!granted && !categoryMode) {
        SettingsHint(stringResource(R.string.surface_notifications_help))
        SettingsActionButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { AdaptiveText(stringResource(R.string.surface_grant_listener)) }
        return
    }
    if (!connected && !categoryMode) {
        SettingsHint(stringResource(R.string.surface_wait_for_listener))
        return
    }

    // Category rules must remain editable with no posted notifications, but the
    // observation status must never be silently presented as a real zero count.
    if (categoryMode && !granted) {
        SettingsHint(stringResource(R.string.surface_notifications_help))
        SettingsActionButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { AdaptiveText(stringResource(R.string.surface_grant_listener)) }
    } else if (categoryMode && !connected) {
        SettingsHint(stringResource(R.string.surface_wait_for_listener))
    }

    Column(Modifier.fillMaxSize()) {
    SurfaceWorkspaceToolbar(
        category = if (initialGrouping == ActiveNotificationGrouping.CATEGORY)
            categorySelection ?: "ALL" else grouping.name,
        options = if (initialGrouping == ActiveNotificationGrouping.CATEGORY) {
            listOf("ALL" to stringResource(R.string.common_all)) +
                notificationCategoryIds.map { key ->
                    key to notificationCategoryLabel(key.takeUnless { it == "(other)" })
                }
        } else ActiveNotificationGrouping.entries.map { mode ->
            mode.name to stringResource(when (mode) {
                ActiveNotificationGrouping.ALL -> R.string.common_all
                ActiveNotificationGrouping.APPLICATION -> R.string.notification_group_application
                ActiveNotificationGrouping.CHANNEL -> R.string.notification_group_channel
                ActiveNotificationGrouping.CATEGORY -> R.string.notification_group_category
            })
        },
        onCategory = { value ->
            if (initialGrouping == ActiveNotificationGrouping.CATEGORY) {
                categorySelection = value.takeUnless { it == "ALL" }
            } else grouping = ActiveNotificationGrouping.entries.first { it.name == value }
        },
        filter = filter, onFilter = { uiState.viewFilter = it; UiPreferencesStore.rememberFilter(context, it, "lists_notifications_${initialGrouping.name}") },
        appTypeFilter = appTypeFilter,
        onAppTypeFilter = { uiState.appTypeFilter = it; UiPreferencesStore.rememberAppType(context, it, "lists_notifications_${initialGrouping.name}") },
        showAppTypeFilter = true,
        actions = listOf(
            WorkspaceMenuAction(stringResource(R.string.select_all), {
                if (categoryMode) draftCategories = applySurfaceBulkSelection(
                    hiddenCategories, shownGroups.map { it.key }, SurfaceBulkOperation.ALL)
                else selectedKeys = applySurfaceBulkSelection(chosen, shownKeys, SurfaceBulkOperation.ALL)
            }, if (categoryMode) shownGroups.any { it.key !in hiddenCategories } else shownKeys.any { it !in chosen }),
            WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                if (categoryMode) draftCategories = applySurfaceBulkSelection(
                    hiddenCategories, shownGroups.map { it.key }, SurfaceBulkOperation.INVERT)
                else selectedKeys = applySurfaceBulkSelection(chosen, shownKeys, SurfaceBulkOperation.INVERT)
            }, if (categoryMode) shownGroups.isNotEmpty() else shownKeys.isNotEmpty()),
            WorkspaceMenuAction(stringResource(R.string.select_none), {
                if (categoryMode) draftCategories = applySurfaceBulkSelection(
                    hiddenCategories, shownGroups.map { it.key }, SurfaceBulkOperation.NONE)
                else selectedKeys = applySurfaceBulkSelection(chosen, shownKeys, SurfaceBulkOperation.NONE)
            }, if (categoryMode) shownGroups.any { it.key in hiddenCategories } else shownKeys.any { it in chosen }),
        ),
    )
    WorkspaceListSummary(
        title = stringResource(R.string.surface_active_count, sameProfile.size, available.size),
        secondary = if (categoryMode) stringResource(R.string.notification_category_help) +
            " · " + stringResource(R.string.surface_channels_count, inventoryRows.size)
            else stringResource(R.string.notification_category_help),
    )
    if (categoryMode) {
        SettingsActionButton(onClick = { NotificationCleanupController.rescan() }) {
            AdaptiveText(stringResource(R.string.surface_scan_refresh))
        }
        WorkspaceAppNote(stringResource(R.string.surface_channels_restricted))
        if (scan.status != NotificationScanStatus.CONNECTED) {
            WorkspaceAppNote(stringResource(R.string.surface_wait_for_listener), warning = true)
        }
    }
    if (categoryMode && connected && inventoryRows.isEmpty()) {
        WorkspaceAppNote(stringResource(R.string.surface_no_notifications))
    } else if (!categoryMode && groups.isEmpty()) {
        WorkspaceAppNote(stringResource(R.string.surface_no_notifications))
    }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(shownGroups, key = { it.key }) { group ->
        val allowedKeys = group.rows.filterNot { it.protected }.map { it.key }.toSet()
        val complete = allowedKeys.isNotEmpty() && allowedKeys.all { it in chosen }
        val any = allowedKeys.any { it in chosen }
        val first = group.rows.firstOrNull()
        val title = when (grouping) {
            ActiveNotificationGrouping.ALL -> requireNotNull(first).packageName + " · " +
                notificationCategoryLabel(first.category)
            ActiveNotificationGrouping.APPLICATION -> requireNotNull(first).packageName
            ActiveNotificationGrouping.CHANNEL -> requireNotNull(first).packageName + " · " + first.channelId
            ActiveNotificationGrouping.CATEGORY -> notificationCategoryLabel(group.key.takeUnless { it == "(other)" })
        }
        val subtitle = stringResource(R.string.notification_group_count,
            group.rows.size, group.rows.count { it.protected })
        SurfaceWorkspaceRow(
            title = title, subtitle = subtitle,
            selected = if (categoryMode) group.key in hiddenCategories else complete,
            indeterminate = !categoryMode && any && !complete,
            enabled = if (categoryMode) true else allowedKeys.isNotEmpty(),
            locked = !categoryMode && allowedKeys.isEmpty(),
            onToggle = {
                if (categoryMode) draftCategories = if (group.key in hiddenCategories)
                    hiddenCategories - group.key else hiddenCategories + group.key
                else selectedKeys = if (complete) chosen - allowedKeys else chosen + allowedKeys
            },
            expanded = uiState.expandedKey == group.key,
            onExpand = if (categoryMode || (grouping != ActiveNotificationGrouping.ALL && group.rows.size > 1)) {
                { uiState.toggleExpanded(group.key) }
            } else null,
            details = if (categoryMode) {
                {
                    val packages = group.rows.groupBy { it.packageName }
                    packages.toSortedMap().forEach { (pkg, notifications) ->
                        val channels = notifications.groupBy { it.channelId }.toSortedMap()
                        WorkspaceDetailHeading(
                            title = pkg,
                            kind = channels.size.toString(),
                        )
                        channels.forEach { (channelId, channelRows) ->
                            val key = "$pkg|$channelId"
                            val safeToHide = channelRows.none { it.protected }
                            val hidden = key in hiddenChannels
                            SurfaceWorkspaceRow(
                                title = channelId,
                                subtitle = stringResource(
                                    R.string.notification_group_count,
                                    channelRows.size,
                                    channelRows.count { it.protected },
                                ),
                                selected = hidden,
                                enabled = safeToHide,
                                locked = !safeToHide,
                                onToggle = {
                                    draftChannels = if (hidden) hiddenChannels - key
                                        else hiddenChannels + key
                                },
                                actions = if (userId == android.os.Process.myUid() / 100000)
                                    listOf(WorkspaceMenuAction(
                                        stringResource(R.string.surface_channel_settings),
                                        {
                                            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            runCatching { context.startActivity(intent) }
                                        },
                                    )) else emptyList(),
                            )
                        }
                    }
                }
            } else if (grouping != ActiveNotificationGrouping.ALL && group.rows.size > 1) {
                {
                    group.rows.forEach { notification ->
                        SurfaceWorkspaceRow(
                            title = notification.packageName + " · " +
                                notificationCategoryLabel(notification.category),
                            subtitle = notification.channelId,
                            selected = notification.key in chosen,
                            enabled = !notification.protected,
                            locked = notification.protected,
                            onToggle = {
                                selectedKeys = if (notification.key in chosen)
                                    chosen - notification.key else chosen + notification.key
                            },
                        )
                    }
                }
            } else null,
            actions = if (grouping == ActiveNotificationGrouping.CHANNEL &&
                userId == android.os.Process.myUid() / 100000 && first != null) listOf(
                WorkspaceMenuAction(stringResource(R.string.surface_channel_settings), {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, first.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, first.channelId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }),
            ) else emptyList(),
        )
    }
        }
    dismissalCount?.let {
        WorkspaceAppNote(stringResource(R.string.surface_dismiss_requested, it))
    }
    if (categoryMode && categoryChanges + channelChanges > 0) PendingChangesBar(
        count = categoryChanges + channelChanges,
        onDiscard = { draftCategories = null; draftChannels = null },
        onApply = applyCategoryChanges,
    )
    if (!categoryMode && chosen.isNotEmpty()) PendingChangesBar(
        count = chosen.size,
        onDiscard = { selectedKeys = emptySet() },
        onApply = {
            dismissalCount = NotificationCleanupController.dismiss(chosen)
            selectedKeys = emptySet()
        },
    )    }

}

/** Stale-rule review uses the same checkbox and category/filter system as Entry cleanup. */
@Composable
internal fun StaleRuleManagement(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null,
) {
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    var refreshed by remember(userId) { mutableStateOf(false) }
    var scanStarted by remember(userId) { mutableStateOf(false) }
    LaunchedEffect(refreshed, state.loading) {
        if (refreshed && state.loading) scanStarted = true
    }
    val completed = refreshed && scanStarted && !state.loading
    var selectedRules by remember(userId) { mutableStateOf(emptySet<ComponentRule>()) }
    var category by remember(userId) { mutableStateOf("ALL") }
    val maintenanceUi = rememberSurfaceWorkspaceUiState(
        userId, "maintenance",
        UiPreferencesStore.initialFilter(LocalContext.current, "lists_maintenance"),
        UiPreferencesStore.initialAppType(LocalContext.current, "lists_maintenance"),
    )
    val viewFilter = maintenanceUi.viewFilter
    val stale = if (completed && state.error.isNullOrBlank()) {
        val returned = state.candidates.mapTo(hashSetOf()) { it.rule }
        (state.selected - returned) + state.candidates.asSequence()
            .filter { it.unavailable && it.rule in state.selected }
            .map { it.rule }.toSet()
    } else emptySet()
    val selected = selectedRules.intersect(stale)
    val visible = stale.asSequence().filter {
        (category == "ALL" || it.kind.name == category) &&
            surfaceFilterAllows(viewFilter, it in selected, false)
    }.sortedWith(compareBy({ it.packageName }, { it.kind.name }, { it.className })).toList()
    val guardOwner = remember(userId) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, selected.size,
        apply = {
            if (!completed || !state.error.isNullOrBlank()) false else {
                vm.deselectRules(selected, null, null)
                selectedRules = emptySet()
                refreshed = false
                true
            }
        },
        discard = { selectedRules = emptySet() },
    )
    val categories = listOf("ALL" to stringResource(R.string.common_all)) +
        stale.map { it.kind }.distinct().sortedBy { it.name }
            .map { it.name to stringResource(it.titleRes()) }

    Column(Modifier.fillMaxSize()) {
    SurfaceWorkspaceToolbar(
        category = category, options = categories,
        onCategory = { category = it },
        filter = viewFilter, onFilter = { maintenanceUi.viewFilter = it; UiPreferencesStore.rememberFilter(LocalContext.current, it, "lists_maintenance") },
        actions = listOf(
            WorkspaceMenuAction(stringResource(R.string.select_all), {
                selectedRules = applySurfaceBulkSelection(selected, visible, SurfaceBulkOperation.ALL)
            }, visible.any { it !in selected }),
            WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                selectedRules = applySurfaceBulkSelection(selected, visible, SurfaceBulkOperation.INVERT)
            }, visible.isNotEmpty()),
            WorkspaceMenuAction(stringResource(R.string.select_none), {
                selectedRules = applySurfaceBulkSelection(selected, visible, SurfaceBulkOperation.NONE)
            }, visible.any { it in selected }),
        ),
    )
    WorkspaceListSummary(
        title = stringResource(R.string.surface_stale_count, stale.size),
        secondary = stringResource(R.string.surface_rules_help),
    )
    SettingsActionButton(onClick = {
        refreshed = true
        scanStarted = false
        selectedRules = emptySet()
        vm.refresh(forceCatalog = true)
    }) { AdaptiveText(stringResource(R.string.surface_refresh_rules)) }
    if (refreshed && !completed) WorkspaceAppNote(stringResource(R.string.surface_scanning))
    if (completed && !state.error.isNullOrBlank()) {
        WorkspaceAppNote(stringResource(R.string.surface_scan_error), warning = true)
    }
        LazyColumn(Modifier.weight(1f)) {
            if (completed && state.error.isNullOrBlank()) items(visible, key = { it.packageName + "|" + it.kind.name + "|" + it.className }) { rule ->
        SurfaceWorkspaceRow(
            title = rule.packageName,
            subtitle = rule.kind.name + " · " + rule.className,
            selected = rule in selected,
            onToggle = {
                selectedRules = if (rule in selected) selected - rule else selected + rule
            },
        )
    }
        }
    if (selected.isNotEmpty()) PendingChangesBar(
        count = selected.size,
        onDiscard = { selectedRules = emptySet() },
        onApply = {
            if (completed && state.error.isNullOrBlank()) {
                vm.deselectRules(selected, null, null)
            }
            selectedRules = emptySet()
            refreshed = false
        },
    )    }

}
