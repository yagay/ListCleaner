package com.yagay.ListCleaner.ui

import androidx.core.app.NotificationManagerCompat
import android.content.Intent
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
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.runtime.NotificationCleanupController

/** All destructive actions require a refreshed snapshot and an explicit confirmation. */
@Composable
internal fun SurfaceCleanupSettings(state: MainState, vm: MainViewModel) {
    val context = LocalContext.current
    val rows by NotificationCleanupController.rows.collectAsStateWithLifecycle()
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val connected by NotificationCleanupController.connected.collectAsStateWithLifecycle()
    val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var confirmNotifications by remember { mutableStateOf(false) }
    var notificationResult by remember { mutableStateOf<Int?>(null) }
    val sameUserRows = rows.filter { it.userId == userId }
    val allowedRows = sameUserRows.filterNot { it.protected }
    val liveKeys = allowedRows.mapTo(hashSetOf()) { it.key }
    val currentSelected = selectedKeys.intersect(liveKeys)
    val grouped = allowedRows.groupBy { it.packageName to it.channelId }

    SettingsArea(stringResource(R.string.surface_notifications)) {
        SettingsHint(stringResource(R.string.surface_notifications_help))
        if (!granted) {
            SettingsActionButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { AdaptiveText(stringResource(R.string.surface_grant_listener)) }
        } else if (!connected) {
            SettingsHint(stringResource(R.string.surface_wait_for_listener))
        } else {
            SettingsHint(stringResource(R.string.surface_active_count, sameUserRows.size, allowedRows.size))
            if (grouped.isEmpty()) SettingsHint(stringResource(R.string.surface_no_notifications))
            grouped.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
                .take(40).forEach { (key, group) ->
                val groupKeys = group.mapTo(hashSetOf()) { it.key }
                SettingsSwitchRow(
                    title = "${key.first} · ${key.second}",
                    supportingText = stringResource(R.string.surface_channel_count, group.size),
                    checked = groupKeys.isNotEmpty() && groupKeys.all { it in currentSelected },
                    onCheckedChange = { selected ->
                        selectedKeys = if (selected) currentSelected + groupKeys
                        else currentSelected - groupKeys
                    },
                )
                if (userId == android.os.Process.myUid() / 100000) {
                    SettingsActionButton(onClick = {
                        val channelIntent = if (key.second == "(default)") {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, key.first)
                        } else {
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, key.first)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, key.second)
                        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Public AOSP Settings actions; OEMs may omit individual activities.
                        runCatching { context.startActivity(channelIntent) }
                            .onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, key.first)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                    }) {
                        AdaptiveText(stringResource(R.string.surface_channel_settings))
                    }
                }
            }
            SettingsActionButton(
                onClick = { confirmNotifications = true },
                enabled = currentSelected.isNotEmpty(),
                style = SettingsActionStyle.DANGER,
            ) { AdaptiveText(stringResource(R.string.surface_dismiss_selected, currentSelected.size)) }
            notificationResult?.let {
                SettingsHint(stringResource(R.string.surface_dismiss_requested, it))
            }
        }
    }
    if (confirmNotifications) {
        SettingsFlatDialog(
            onDismissRequest = { confirmNotifications = false },
            title = { AdaptiveText(stringResource(R.string.surface_confirm_notifications)) },
            text = { AdaptiveText(stringResource(R.string.surface_confirm_notifications_help, currentSelected.size)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirmNotifications = false
                    notificationResult = NotificationCleanupController.dismiss(currentSelected)
                    selectedKeys = emptySet()
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirmNotifications = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    var refreshed by remember(userId) { mutableStateOf(false) }
    var scanStarted by remember(userId) { mutableStateOf(false) }
    LaunchedEffect(refreshed, state.loading) {
        if (refreshed && state.loading) scanStarted = true
    }
    val scanComplete = refreshed && scanStarted && !state.loading
    var selectedRules by remember(userId) { mutableStateOf(emptySet<ComponentRule>()) }
    var confirmRules by remember { mutableStateOf(false) }
    val staleRules = if (scanComplete && state.error.isNullOrBlank()) {
        state.candidates.filter { it.unavailable && it.rule in state.selected }
            .mapTo(linkedSetOf()) { it.rule }
    } else emptySet()
    val chosenRules = selectedRules.intersect(staleRules)
    SettingsArea(stringResource(R.string.surface_rules)) {
        SettingsHint(stringResource(R.string.surface_rules_help))
        SettingsActionButton(onClick = {
            refreshed = true
            scanStarted = false
            selectedRules = emptySet()
            vm.refresh(forceCatalog = true)
        }) { AdaptiveText(stringResource(R.string.surface_refresh_rules)) }
        if (refreshed && !scanComplete) SettingsHint(stringResource(R.string.surface_scanning))
        if (scanComplete && state.error.isNullOrBlank()) {
            SettingsHint(stringResource(R.string.surface_stale_count, staleRules.size))
            staleRules.take(40).forEach { rule ->
                SettingsSwitchRow(
                    title = rule.packageName,
                    supportingText = rule.kind.name + " · " + rule.className,
                    checked = rule in chosenRules,
                    onCheckedChange = { selected ->
                        selectedRules = if (selected) chosenRules + rule else chosenRules - rule
                    },
                )
            }
            SettingsActionButton(
                enabled = chosenRules.isNotEmpty(),
                onClick = { confirmRules = true },
                style = SettingsActionStyle.DANGER,
            ) { AdaptiveText(stringResource(R.string.surface_remove_rules, chosenRules.size)) }
        } else if (scanComplete && !state.error.isNullOrBlank()) {
            SettingsHint(stringResource(R.string.surface_scan_error), warning = true)
        }
    }
    if (confirmRules) {
        SettingsFlatDialog(
            onDismissRequest = { confirmRules = false },
            title = { AdaptiveText(stringResource(R.string.surface_confirm_rules)) },
            text = { AdaptiveText(stringResource(R.string.surface_confirm_rules_help, chosenRules.size)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirmRules = false
                    if (scanComplete && state.error.isNullOrBlank()) {
                        vm.deselectRules(chosenRules, null, null)
                    }
                    selectedRules = emptySet()
                    refreshed = false
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirmRules = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    AospSurfaceSettings(vm, userId)
}
