package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.SurfaceKind
import com.yagay.ListCleaner.runtime.NotificationCleanupController

/** Actual cleanup workflows, kept separate from application appearance/configuration settings. */
internal enum class SurfaceManagerGroup(
    @StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector,
) {
    NOTIFICATIONS(R.string.surface_group_notifications, R.string.surface_group_notifications_help,
        Icons.Rounded.Notifications),
    LAUNCHER(R.string.surface_group_launcher, R.string.surface_group_launcher_help,
        Icons.Rounded.Apps),
    SYSTEM(R.string.surface_group_system, R.string.surface_group_system_help,
        Icons.Rounded.Tune),
    MAINTENANCE(R.string.surface_group_maintenance, R.string.surface_group_maintenance_help,
        Icons.Rounded.CleaningServices),
}

internal enum class SurfaceManagerPage(
    val group: SurfaceManagerGroup,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val kind: SurfaceKind? = null,
) {
    NOTIFICATIONS_CURRENT(SurfaceManagerGroup.NOTIFICATIONS, R.string.surface_notifications,
        R.string.surface_notifications_help, Icons.Rounded.Notifications),
    NOTIFICATIONS_TYPES(SurfaceManagerGroup.NOTIFICATIONS, R.string.notification_group_category,
        R.string.notification_group_category_help, Icons.Rounded.Notifications),
    NOTIFICATIONS_SHADE(SurfaceManagerGroup.NOTIFICATIONS, R.string.surface_aosp_notifications,
        R.string.surface_shade_filter_help, Icons.Rounded.Apps, SurfaceKind.NOTIFICATION_SHADE),
    NOTIFICATIONS_CHANNELS(SurfaceManagerGroup.NOTIFICATIONS, R.string.surface_channels_title,
        R.string.surface_channels_help, Icons.Rounded.Tune),
    DRAWER(SurfaceManagerGroup.LAUNCHER, R.string.surface_aosp_drawer,
        R.string.surface_drawer_filter_help, Icons.Rounded.Apps, SurfaceKind.LAUNCHER_DRAWER),
    PREDICTIONS(SurfaceManagerGroup.LAUNCHER, R.string.surface_aosp_predictions,
        R.string.surface_prediction_filter_help, Icons.Rounded.GridView, SurfaceKind.LAUNCHER_PREDICTIONS),
    RECENTS(SurfaceManagerGroup.LAUNCHER, R.string.surface_aosp_recents,
        R.string.surface_recents_filter_help, Icons.Rounded.History, SurfaceKind.RECENTS),
    QS_EDITOR(SurfaceManagerGroup.SYSTEM, R.string.surface_aosp_qs,
        R.string.surface_qs_filter_help, Icons.Rounded.Widgets, SurfaceKind.QS_EDITOR),
    DOCUMENTS(SurfaceManagerGroup.SYSTEM, R.string.surface_aosp_documents,
        R.string.surface_documents_filter_help, Icons.Rounded.FolderOpen, SurfaceKind.DOCUMENT_RECENTS),
    SETTINGS_APPS(SurfaceManagerGroup.SYSTEM, R.string.surface_aosp_settings,
        R.string.surface_settings_filter_help, Icons.Rounded.Settings, SurfaceKind.SETTINGS_APPS),
    MEDIA_OUTPUT(SurfaceManagerGroup.SYSTEM, R.string.surface_aosp_media,
        R.string.surface_media_filter_help, Icons.Rounded.Security, SurfaceKind.MEDIA_OUTPUT),
    INVALID_RULES(SurfaceManagerGroup.MAINTENANCE, R.string.surface_rules,
        R.string.surface_rules_help, Icons.Rounded.Description),
}

/** Category -> list type -> flat editor, without inserting another top-level toolbar. */
@Composable
internal fun SurfaceManagementTab(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null,
) {
    var group by rememberSaveable { mutableStateOf<SurfaceManagerGroup?>(null) }
    var page by rememberSaveable { mutableStateOf<SurfaceManagerPage?>(null) }
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val activeNotifications by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val editorStateHolder = rememberSaveableStateHolder()

    val navigateBack: () -> Unit = {
        val performBack = {
            if (page != null) page = null else group = null
        }
        if (pendingNavigation != null) pendingNavigation.request(performBack)
        else performBack()
    }
    BackHandler(enabled = group != null || page != null, onBack = navigateBack)

    if (page?.kind != null || page == SurfaceManagerPage.NOTIFICATIONS_CHANNELS ||
        page == SurfaceManagerPage.NOTIFICATIONS_CURRENT || page == SurfaceManagerPage.NOTIFICATIONS_TYPES ||
        page == SurfaceManagerPage.INVALID_RULES) {
        val current = requireNotNull(page)
        Column(Modifier.fillMaxSize()) {
            SurfaceNavigationHeading(
                title = stringResource(current.title),
                onBack = navigateBack,
            )
            Box(Modifier.weight(1f)) {
                editorStateHolder.SaveableStateProvider("lists-${userId}-${current.name}") {
                    when (current) {
                    SurfaceManagerPage.NOTIFICATIONS_CHANNELS ->
                        SurfaceChannelSettings(vm, userId, activeNotifications, pendingNavigation)
                    SurfaceManagerPage.NOTIFICATIONS_CURRENT ->
                        ActiveNotificationManagement(vm, pendingNavigation)
                    SurfaceManagerPage.NOTIFICATIONS_TYPES ->
                        ActiveNotificationManagement(vm, pendingNavigation, ActiveNotificationGrouping.CATEGORY)
                    SurfaceManagerPage.INVALID_RULES -> StaleRuleManagement(state, vm, pendingNavigation)
                    else -> AospSurfaceSettings(vm, userId, requireNotNull(current.kind), pendingNavigation)
                    }
                }
            }
        }
        return
    }
    MaterialPageColumn(fullBleed = true) {
        when {
            group != null -> {
                val selected = requireNotNull(group)
                SurfaceNavigationHeading(
                    title = stringResource(selected.title),
                    onBack = navigateBack,
                )
                Column(Modifier.fillMaxWidth()) {
                    SurfaceManagerPage.entries.filter { it.group == selected }.forEach { option ->
                        FlatCategoryRow(
                            icon = option.icon,
                            title = stringResource(option.title),
                            description = stringResource(option.description),
                            onClick = { page = option },
                        )
                    }
                }
            }
            else -> {
                FlatSectionHeader(
                    stringResource(R.string.nav_surface_management),
                    modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal),
                )
                Column(Modifier.fillMaxWidth()) {
                    SurfaceManagerGroup.entries.forEach { section ->
                        FlatCategoryRow(
                            icon = section.icon,
                            title = stringResource(section.title),
                            description = stringResource(section.description),
                            onClick = { group = section; page = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceNavigationHeading(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.sectionGap),
    ) {
        AdaptiveBackButton(onClick = onBack)
        AdaptiveText(
            title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
