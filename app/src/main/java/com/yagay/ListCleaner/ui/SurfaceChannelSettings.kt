package com.yagay.ListCleaner.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.PackageIdentity
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    var draft by remember(userId, saved) { mutableStateOf(saved) }
    var pkg by remember(userId) { mutableStateOf("") }
    var inventory by remember(userId) { mutableStateOf<ChannelInventory?>(null) }
    var querying by remember(userId) { mutableStateOf(false) }
    var viewFilter by remember(userId) { mutableStateOf(UiFilter.ALL) }
    var expandedPackage by remember(userId) { mutableStateOf<String?>(null) }
    var packageInventories by remember(userId) { mutableStateOf<Map<String, ChannelInventory>>(emptyMap()) }
    val installedPackages = remember(userId) {
        if (userId == android.os.Process.myUid() / 100000) {
            runCatching {
                context.packageManager.getInstalledApplications(0).map { it.packageName }
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    val guardOwner = remember(userId) { Any() }
    RegisterPendingNavigation(
        pendingNavigation, guardOwner, if (draft != saved) 1 else 0,
        apply = { vm.setSurfaceFilters(draft); true },
        discard = { draft = saved },
    )

    val observedPackages = (installedPackages + observed.filter { it.userId == userId }.map { it.packageName } + NotificationCleanupController.observedRows.value.filter { it.userId == userId }.map { it.packageName }).distinct().sorted()
    val chosenIds = inventory?.items.orEmpty()
        .filter { surfaceFilterAllows(viewFilter,
            draft.isChannelHidden(it.packageName, it.channelId), false) }
    SurfaceWorkspaceToolbar(
        category = pkg,
        options = (listOf("" to stringResource(R.string.surface_channels_choose)) +
            (observedPackages + pkg.takeIf { PackageIdentity.valid(it) }.orEmpty())
                .filter { it.isNotBlank() }.distinct().sorted().map { it to it }),
        onCategory = { pkg = it; inventory = null },
        filter = viewFilter, onFilter = { viewFilter = it },
        actions = listOf(
            WorkspaceMenuAction(stringResource(R.string.select_all), {
                chosenIds.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId, true)
                }
            }, chosenIds.any { !draft.isChannelHidden(it.packageName, it.channelId) }),
            WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                chosenIds.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId,
                        !draft.isChannelHidden(channel.packageName, channel.channelId))
                }
            }, chosenIds.isNotEmpty()),
            WorkspaceMenuAction(stringResource(R.string.select_none), {
                chosenIds.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId, false)
                }
            }, chosenIds.any { draft.isChannelHidden(it.packageName, it.channelId) }),
        ),
    )
    WorkspaceListSummary(
        title = stringResource(R.string.surface_channels_title),
        secondary = stringResource(R.string.surface_channels_help),
    )
    BasicTextField(
        value = pkg,
        onValueChange = { pkg = it.trim().take(255); inventory = null },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        decorationBox = { inner ->
            Column {
                WorkspaceDetailCaption(stringResource(R.string.surface_channels_package))
                inner()
            }
        },
    )
    SettingsActionButton(
        enabled = PackageIdentity.valid(pkg) && !querying,
        onClick = {
            querying = true
            scope.launch {
                try {
                    inventory = NotificationCleanupController.channelsForPackage(pkg, userId)
                } finally {
                    querying = false
                }
            }
        },
    ) { AdaptiveText(stringResource(R.string.surface_channels_scan)) }

    observedPackages.forEach { appPackage ->
        val found = packageInventories[appPackage]
        val channels = found?.items.orEmpty()
        val selected = channels.isNotEmpty() && channels.all { draft.isChannelHidden(appPackage, it.channelId) }
        SurfaceWorkspaceRow(
            title = appPackage,
            subtitle = if (found == null) stringResource(R.string.surface_channels_help)
                else stringResource(R.string.surface_channels_count, channels.size),
            selected = selected,
            enabled = channels.isNotEmpty(),
            onToggle = {
                channels.forEach { draft = draft.withChannelHidden(appPackage, it.channelId, !selected) }
            },
            expanded = expandedPackage == appPackage,
            onExpand = {
                expandedPackage = if (expandedPackage == appPackage) null else appPackage
                if (expandedPackage == appPackage && found == null) {
                    scope.launch {
                        val result = NotificationCleanupController.channelsForPackage(appPackage, userId)
                        packageInventories = packageInventories + (appPackage to result)
                    }
                }
            },
            details = {
                if (found != null) {
                    WorkspaceAppNote(stringResource(when (found.status) {
                        ChannelInventoryStatus.COMPLETE -> R.string.surface_channels_full
                        ChannelInventoryStatus.OBSERVED_ONLY -> R.string.surface_channels_restricted
                        ChannelInventoryStatus.DISCONNECTED -> R.string.surface_wait_for_listener
                    }))
                    channels.forEach { channel ->
                        val checked = draft.isChannelHidden(appPackage, channel.channelId)
                        SurfaceWorkspaceRow(
                            title = channel.label,
                            subtitle = channel.channelId,
                            selected = checked,
                            onToggle = {
                                draft = draft.withChannelHidden(appPackage, channel.channelId, !checked)
                            },
                        )
                    }
                }
            },
        )
    }
    inventory?.let { result ->
        val statusRes = when (result.status) {
            ChannelInventoryStatus.COMPLETE -> R.string.surface_channels_full
            ChannelInventoryStatus.OBSERVED_ONLY -> R.string.surface_channels_restricted
            ChannelInventoryStatus.DISCONNECTED -> R.string.surface_wait_for_listener
        }
        WorkspaceListSummary(
            title = stringResource(R.string.surface_channels_count, result.items.size),
            secondary = stringResource(statusRes),
        )
        chosenIds.forEach { channel ->
            val selected = draft.isChannelHidden(channel.packageName, channel.channelId)
            SurfaceWorkspaceRow(
                title = channel.label,
                subtitle = channel.channelId,
                selected = selected,
                onToggle = {
                    draft = draft.withChannelHidden(
                        channel.packageName, channel.channelId, !selected,
                    )
                },
                actions = if (userId == android.os.Process.myUid() / 100000) listOf(
                    WorkspaceMenuAction(stringResource(R.string.surface_channel_settings), {
                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, channel.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }),
                ) else emptyList(),
            )
        }
    }
    if (draft != saved) PendingChangesBar(
        count = 1, onDiscard = { draft = saved },
        onApply = { vm.setSurfaceFilters(draft) },
    )
}
