package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

@Composable
internal fun ScopeDialog(status: ModuleStatus, requestScope: () -> Unit, refresh: () -> Unit, dismiss: () -> Unit) {
    FullScreenDetails(
        onDismissRequest = dismiss,
        title = { AdaptiveText(stringResource(R.string.scope_title)) },
        text = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
            ) {
                AdaptiveText(stringResource(R.string.scope_intro))
                AdaptiveText(stringResource(R.string.scope_detected_hosts), fontWeight = FontWeight.Bold)
                if (status.detection.hosts.isEmpty()) AdaptiveText(stringResource(R.string.scope_no_hosts))
                status.detection.hosts.forEach { host ->
                    AdaptiveText(
                        stringResource(
                            R.string.scope_host_details,
                            host.packageName,
                            host.className,
                            host.processName,
                            host.scenarios.joinToString(" · ")
                        )
                    )
                    if (host.requiresManualScope) {
                        AdaptiveText(stringResource(R.string.scope_manual_host_warning), color = MaterialTheme.colorScheme.error)
                    } else if (host.packageName == "android") {
                        AdaptiveText(stringResource(R.string.scope_android_system_note))
                    }
                }
                val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
                if (unconfirmed.isNotEmpty()) {
                    AdaptiveText(stringResource(R.string.scope_unconfirmed_installed, unconfirmed.sorted().joinToString("\n")))
                }
                AdaptiveText(stringResource(R.string.scope_granted), fontWeight = FontWeight.Bold)
                AdaptiveText(
                    if (!status.scopeKnown) stringResource(R.string.scope_grant_unknown)
                    else status.grantedScope.sorted().joinToString("\n").ifEmpty { stringResource(R.string.common_none) }
                )
                if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                    AdaptiveText(stringResource(R.string.scope_extra_grants, status.extraScope.sorted().joinToString("\n")))
                }
                AdaptiveText(stringResource(R.string.scope_running_targets), fontWeight = FontWeight.Bold)
                if (status.runningTargets.isEmpty()) AdaptiveText(stringResource(R.string.scope_no_running_targets))
                status.runningTargets.forEach { target ->
                    val stateLabel = when (target.state) {
                        "UP_TO_DATE" -> stringResource(R.string.scope_target_up_to_date)
                        "STALE" -> stringResource(R.string.scope_target_stale)
                        "RELOADING" -> stringResource(R.string.scope_target_reloading)
                        "FAILED" -> stringResource(R.string.scope_target_failed)
                        else -> target.state
                    }
                    AdaptiveText(stringResource(R.string.scope_target_details, target.processName, stateLabel, target.version))
                }
                AdaptiveText(stringResource(R.string.scope_runtime_warning))
                status.detection.warnings.forEach { AdaptiveText(it, color = MaterialTheme.colorScheme.error) }
                status.message?.let { AdaptiveText(it) }
                status.error?.let { AdaptiveText(it, color = MaterialTheme.colorScheme.error) }
                AdaptiveTextButton(onClick = refresh, enabled = !status.requesting) { AdaptiveText(stringResource(R.string.scope_recheck)) }
                AdaptiveText(stringResource(R.string.scope_advanced))
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                onClick = requestScope,
                enabled = status.connected && status.scopeKnown && !status.requesting && status.missingScope.isNotEmpty()
            ) {
                AdaptiveText(
                    when {
                        status.requesting -> stringResource(R.string.scope_requesting)
                        status.detection.recommended.isEmpty() -> stringResource(R.string.scope_no_recommended)
                        status.scopeKnown && status.missingScope.isEmpty() -> stringResource(R.string.scope_recommended_granted)
                        else -> stringResource(R.string.scope_request_missing, status.missingScope.size)
                    }
                )
            }
        },
        dismissButton = { AdaptiveTextButton(onClick = dismiss) { AdaptiveText(stringResource(R.string.common_close)) } }
    )
}

@Composable
private fun FullScreenDetails(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    MaterialFullScreenDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        bottomBar = {
            MaterialDialogActions(
                dismissButton = dismissButton,
                confirmButton = confirmButton
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = MaterialUiTokens.pageHorizontal)) {
            text()
        }
    }
}
