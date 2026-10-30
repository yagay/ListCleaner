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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    var draft by remember(userId, saved) { mutableStateOf(saved) }
    var pkg by remember(userId) { mutableStateOf("") }
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
    val visiblePackages = observedPackages.filter {
        pkg.isBlank() || it.contains(pkg.trim(), ignoreCase = true)
    }
    val visibleChannels = visiblePackages.flatMap { packageInventories[it]?.items.orEmpty() }
        .filter { surfaceFilterAllows(viewFilter,
            draft.isChannelHidden(it.packageName, it.channelId), false) }
    SurfaceWorkspaceToolbar(
        category = "",
        options = listOf("" to stringResource(R.string.common_all)),
        onCategory = {},
        filter = viewFilter, onFilter = { viewFilter = it },
        actions = listOf(
            WorkspaceMenuAction(stringResource(R.string.select_all), {
                visibleChannels.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId, true)
                }
            }, visibleChannels.any { !draft.isChannelHidden(it.packageName, it.channelId) }),
            WorkspaceMenuAction(stringResource(R.string.surface_invert_selected), {
                visibleChannels.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId,
                        !draft.isChannelHidden(channel.packageName, channel.channelId))
                }
            }, visibleChannels.isNotEmpty()),
            WorkspaceMenuAction(stringResource(R.string.select_none), {
                visibleChannels.forEach { channel ->
                    draft = draft.withChannelHidden(channel.packageName, channel.channelId, false)
                }
            }, visibleChannels.any { draft.isChannelHidden(it.packageName, it.channelId) }),
        ),
    )
    WorkspaceListSummary(
        title = stringResource(R.string.surface_channels_title),
        secondary = stringResource(R.string.surface_channels_help),
    )
    BasicTextField(
        value = pkg,
        onValueChange = { pkg = it.take(255) },
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
    visiblePackages.take(250).forEach { appPackage ->
        val appLabel = remember(appPackage) {
            runCatching {
                val info = context.packageManager.getApplicationInfo(appPackage, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(appPackage)
        }
        val found = packageInventories[appPackage]
        val channels = found?.items.orEmpty()
        val selected = channels.isNotEmpty() && channels.all { draft.isChannelHidden(appPackage, it.channelId) }
        val expandApp: () -> Unit = {
            expandedPackage = if (expandedPackage == appPackage) null else appPackage
            if (expandedPackage == appPackage && found == null) {
                scope.launch {
                    val result = NotificationCleanupController.channelsForPackage(appPackage, userId)
                    packageInventories = packageInventories + (appPackage to result)
                }
            }
        }
        WorkspaceAppRow(
            icon = null,
            title = appLabel,
            subtitle = if (found == null || found.status != ChannelInventoryStatus.COMPLETE)
                appPackage else stringResource(R.string.surface_channels_count, channels.size),
            packageName = appPackage,
            expanded = expandedPackage == appPackage,
            lockState = BulkLockState.NONE,
            onExpand = expandApp,
            onLock = {},
            onUnlock = {},
            selection = {
                WorkspaceAppSelectionCheckbox(
                    state = when {
                        selected -> ToggleableState.On
                        channels.any { draft.isChannelHidden(appPackage, it.channelId) } ->
                            ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    enabled = channels.isNotEmpty(),
                    onClick = {
                        channels.forEach {
                            draft = draft.withChannelHidden(appPackage, it.channelId, !selected)
                        }
                    },
                )
            },
            menuActions = if (userId == android.os.Process.myUid() / 100000) listOf(
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
                                locked = false,
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
                                        actions = if (userId == android.os.Process.myUid() / 100000)
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
    if (draft != saved) PendingChangesBar(
        count = 1, onDiscard = { draft = saved },
        onApply = { vm.setSurfaceFilters(draft) },
    )
}
