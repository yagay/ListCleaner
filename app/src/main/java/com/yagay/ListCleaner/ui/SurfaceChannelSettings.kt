package com.yagay.ListCleaner.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.runtime.ActiveNotificationRow
import com.yagay.ListCleaner.runtime.ChannelInventory
import com.yagay.ListCleaner.runtime.ChannelInventoryStatus
import com.yagay.ListCleaner.runtime.NotificationCleanupController
import kotlinx.coroutines.launch

/** Channel-specific AOSP shade rules. Updates are staged, with no cross-app
 * channel mutation; OS-owned Settings remains the authority for importance. */
@Composable
internal fun SurfaceChannelSettings(
    vm: MainViewModel,
    userId: Int,
    observed: List<ActiveNotificationRow>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    var draft by remember(userId, saved) { mutableStateOf(saved) }
    var pkg by remember(userId) { mutableStateOf("") }
    var inventory by remember(userId) { mutableStateOf<ChannelInventory?>(null) }
    var querying by remember(userId) { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val observedPackages = observed.filter { it.userId == userId }
        .map { it.packageName }.distinct().sorted()
    SettingsArea(stringResource(R.string.surface_channels_title)) {
        SettingsHint(stringResource(R.string.surface_channels_help))
        if (observedPackages.isNotEmpty()) {
            SettingsChoices(
                title = stringResource(R.string.surface_channels_choose),
                value = pkg,
                options = observedPackages.map { it to it },
                onSelected = { pkg = it; inventory = null },
            )
        }
        BasicTextField(
            value = pkg,
            onValueChange = { pkg = it.trim().take(255); inventory = null },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { inner ->
                Column {
                    SettingsHint(stringResource(R.string.surface_channels_package))
                    inner()
                }
            }
        )
        SettingsActionButton(
            enabled = PackageIdentity.valid(pkg) && !querying,
            onClick = {
                querying = true
                scope.launch {
                    inventory = NotificationCleanupController.channelsForPackage(pkg, userId)
                    querying = false
                }
            },
        ) { AdaptiveText(stringResource(R.string.surface_channels_scan)) }

        inventory?.let { result ->
            val statusRes = when (result.status) {
                ChannelInventoryStatus.COMPLETE -> R.string.surface_channels_full
                ChannelInventoryStatus.OBSERVED_ONLY -> R.string.surface_channels_restricted
                ChannelInventoryStatus.DISCONNECTED -> R.string.surface_wait_for_listener
            }
            SettingsHint(stringResource(statusRes))
            SettingsHint(stringResource(R.string.surface_channels_count, result.items.size))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                result.items.forEach { channel ->
                    SettingsSwitchRow(
                        title = channel.label,
                        supportingText = channel.channelId,
                        checked = draft.isChannelHidden(channel.packageName, channel.channelId),
                        onCheckedChange = { checked ->
                            draft = draft.withChannelHidden(
                                channel.packageName, channel.channelId, checked
                            )
                        },
                    )
                    if (userId == android.os.Process.myUid() / 100000) {
                        SettingsActionButton(onClick = {
                            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, channel.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }) { AdaptiveText(stringResource(R.string.surface_channel_settings)) }
                    }
                }
            }
        }
        SettingsActionButton(
            enabled = draft != saved,
            style = SettingsActionStyle.PRIMARY,
            onClick = { confirming = true }
        ) { AdaptiveText(stringResource(R.string.surface_aosp_apply)) }
    }
    if (confirming) {
        SettingsFlatDialog(
            onDismissRequest = { confirming = false },
            title = { AdaptiveText(stringResource(R.string.surface_channels_confirm)) },
            text = { AdaptiveText(stringResource(R.string.surface_channels_confirm_help)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirming = false
                    vm.setSurfaceFilters(draft)
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirming = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
