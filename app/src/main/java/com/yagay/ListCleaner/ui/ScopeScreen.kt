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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
        title = { Text(stringResource(R.string.scope_title)) },
        text = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.scope_intro))
                Text(stringResource(R.string.scope_detected_hosts), fontWeight = FontWeight.Bold)
                if (status.detection.hosts.isEmpty()) Text(stringResource(R.string.scope_no_hosts))
                status.detection.hosts.forEach { host ->
                    Text(
                        stringResource(
                            R.string.scope_host_details,
                            host.packageName,
                            host.className,
                            host.processName,
                            host.scenarios.joinToString(" · ")
                        )
                    )
                    if (host.requiresManualScope) {
                        Text(stringResource(R.string.scope_manual_host_warning), color = MaterialTheme.colorScheme.error)
                    } else if (host.packageName == "android") {
                        Text(stringResource(R.string.scope_android_system_note))
                    }
                }
                val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
                if (unconfirmed.isNotEmpty()) {
                    Text(stringResource(R.string.scope_unconfirmed_installed, unconfirmed.sorted().joinToString("\n")))
                }
                Text(stringResource(R.string.scope_granted), fontWeight = FontWeight.Bold)
                Text(
                    if (!status.scopeKnown) stringResource(R.string.scope_grant_unknown)
                    else status.grantedScope.sorted().joinToString("\n").ifEmpty { stringResource(R.string.common_none) }
                )
                if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                    Text(stringResource(R.string.scope_extra_grants, status.extraScope.sorted().joinToString("\n")))
                }
                Text(stringResource(R.string.scope_running_targets), fontWeight = FontWeight.Bold)
                if (status.runningTargets.isEmpty()) Text(stringResource(R.string.scope_no_running_targets))
                status.runningTargets.forEach { target ->
                    val stateLabel = when (target.state) {
                        "UP_TO_DATE" -> stringResource(R.string.scope_target_up_to_date)
                        "STALE" -> stringResource(R.string.scope_target_stale)
                        "RELOADING" -> stringResource(R.string.scope_target_reloading)
                        "FAILED" -> stringResource(R.string.scope_target_failed)
                        else -> target.state
                    }
                    Text(stringResource(R.string.scope_target_details, target.processName, stateLabel, target.version))
                }
                Text(stringResource(R.string.scope_runtime_warning))
                status.detection.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                status.message?.let { Text(it) }
                status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = refresh, enabled = !status.requesting) { Text(stringResource(R.string.scope_recheck)) }
                Text(stringResource(R.string.scope_advanced))
            }
        },
        confirmButton = {
            TextButton(
                onClick = requestScope,
                enabled = status.connected && status.scopeKnown && !status.requesting && status.missingScope.isNotEmpty()
            ) {
                Text(
                    when {
                        status.requesting -> stringResource(R.string.scope_requesting)
                        status.detection.recommended.isEmpty() -> stringResource(R.string.scope_no_recommended)
                        status.scopeKnown && status.missingScope.isEmpty() -> stringResource(R.string.scope_recommended_granted)
                        else -> stringResource(R.string.scope_request_missing, status.missingScope.size)
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenDetails(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                    }
                )
            },
            bottomBar = {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).padding(horizontal = 16.dp)) { text() }
        }
    }
}
