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
    // One notification entry: app-level hiding and channel-level hiding share
    // the registered-channel inventory. Old category rules remain recoverable.
    // Keep the old CHANNELS enum ID for saved navigation state compatibility.
    NOTIFICATIONS_CHANNELS(SurfaceManagerGroup.NOTIFICATIONS, R.string.notification_cleanup_title,
        R.string.notification_cleanup_help, Icons.Rounded.Notifications),
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

/**
 * Lists V2: one selection screen and one shared editor host.
 * A single tap opens each AOSP/notification/maintenance editor.
 * Backend implementations remain separate.
 */
@Composable
internal fun SurfaceManagementTab(
    state: MainState,
    vm: MainViewModel,
    pendingNavigation: PendingNavigationGuard? = null,
    onSearchStateChanged: ((SurfaceWorkspaceUiState?) -> Unit)? = null,
    refreshRegistry: ListsRefreshRegistry? = null,
) {
    var page by rememberSaveable { mutableStateOf<SurfaceManagerPage?>(null) }
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val activeNotifications by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val editorStateHolder = rememberSaveableStateHolder()
    val navigateBack: () -> Unit = {
        val next = { page = null }
        if (pendingNavigation != null) pendingNavigation.request(next) else next()
    }

    val current = page
    if (current == null) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            SurfaceManagerGroup.entries.forEach { section ->
                item(key = "group-${section.name}") {
                    FlatSectionHeader(
                        stringResource(section.title),
                        modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal),
                    )
                }
                SurfaceManagerPage.entries.filter { it.group == section }.forEach { option ->
                    item(key = "page-${option.name}") {
                        FlatCategoryRow(
                            icon = option.icon,
                            title = stringResource(option.title),
                            description = stringResource(option.description),
                            onClick = { page = option },
                        )
                    }
                }
            }
        }
        return
    }

    BackHandler(onBack = navigateBack)
    Column(Modifier.fillMaxSize()) {
        SurfaceNavigationHeading(
            title = stringResource(current.title),
            onBack = navigateBack,
        )
        Box(Modifier.weight(1f)) {
            editorStateHolder.SaveableStateProvider("lists-v2-${userId}-${current.name}") {
                when (current) {
                    SurfaceManagerPage.NOTIFICATIONS_CHANNELS ->
                        SurfaceChannelSettings(vm, userId, activeNotifications, pendingNavigation, onSearchStateChanged, refreshRegistry)
                    SurfaceManagerPage.INVALID_RULES ->
                        StaleRuleManagement(state, vm, pendingNavigation, onSearchStateChanged, refreshRegistry)
                    else ->
                        AospSurfaceSettings(vm, userId, requireNotNull(current.kind), pendingNavigation, onSearchStateChanged, refreshRegistry)
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
        AdaptiveText(title, style = MaterialTheme.typography.titleMedium)
    }
}
