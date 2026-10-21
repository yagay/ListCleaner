package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only, authority-first dialog. Do not draw PackageManager registrations as "enabled".
 * A private DocumentsUI/PhotoPicker list is never assigned a fabricated size or substituted
 * by its ACTION_GET_CONTENT handler inventory.
 */
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: request.entryKind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    var scenarioMenu by remember { mutableStateOf(false) }
    var showDiagnostics by remember(request) { mutableStateOf(false) }
    var expandedPackage by remember(request) { mutableStateOf<String?>(null) }
    val choices = request.entryKind?.let(::availableProbeScenarios).orEmpty()
    val activeRequest = request.copy(scenario = scenario)
    var response by remember(request) { mutableStateOf<SystemProbeResult?>(null) }

    LaunchedEffect(activeRequest, revision) {
        response = null
        response = withContext(Dispatchers.IO) {
            SystemEntryProbe.query(context.applicationContext, activeRequest)
        }
    }

    val title = request.entryKind?.let { stringResource(it.titleRes()) }
        ?: request.componentKind?.let { stringResource(it.titleRes()) }
        ?: ""
    val value = response
    val diagnostic = showDiagnostics && value?.diagnosticCandidates?.isNotEmpty() == true
    val displayed = when {
        value == null -> emptyList()
        diagnostic -> value.diagnosticCandidates
        else -> value.items
    }
    val groups = displayed.groupBy(SystemProbeItem::packageName).toList()
    val detailQuality = value?.evidence
    val qualityRes = when (detailQuality) {
        ProbeEvidence.ENABLED -> R.string.system_probe_quality_enabled
        ProbeEvidence.REGISTERED -> R.string.system_probe_quality_candidate
        ProbeEvidence.PICKER_HOST -> R.string.system_probe_quality_picker
        ProbeEvidence.NOT_PUBLIC, null -> R.string.system_probe_quality_unavailable
    }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.system_probe_title, title)) },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (choices.size > 1) {
                    item {
                        Box {
                            AdaptiveTextButton(onClick = { scenarioMenu = true }) {
                                AdaptiveText(stringResource(R.string.system_probe_scenario_label,
                                    stringResource(scenario.titleRes)))
                            }
                            AdaptiveDropdownMenu(
                                expanded = scenarioMenu,
                                onDismissRequest = { scenarioMenu = false },
                            ) {
                                choices.forEach { choice ->
                                    AdaptiveDropdownMenuItem(
                                        text = { AdaptiveText(stringResource(choice.titleRes)) },
                                        onClick = {
                                            scenarioMenu = false
                                            scenario = choice
                                            showDiagnostics = false
                                            expandedPackage = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (value == null) {
                    item { AdaptiveText(stringResource(R.string.system_probe_loading)) }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            AdaptiveText(stringResource(value.sourceRes),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                            AdaptiveText(stringResource(qualityRes),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodySmall)
                            AdaptiveText(stringResource(value.explanationRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AdaptiveText(stringResource(R.string.system_probe_scope_contract),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    value.pickerHost?.let { host ->
                        item {
                            AdaptiveText(stringResource(R.string.system_probe_picker_host,
                                host.appLabel + " (" + host.packageName + ")"),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (value.evidence == ProbeEvidence.PICKER_HOST && value.pickerHost == null) {
                        item {
                            AdaptiveText(stringResource(R.string.system_probe_unknown_host),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (value.diagnosticCandidates.isNotEmpty()) {
                        item {
                            AdaptiveTextButton(onClick = {
                                showDiagnostics = !showDiagnostics
                                expandedPackage = null
                            }) {
                                AdaptiveText(stringResource(
                                    if (showDiagnostics) R.string.system_probe_diagnostic_hide
                                    else R.string.system_probe_diagnostic_show))
                            }
                        }
                    }
                    if (value.evidence == ProbeEvidence.ENABLED ||
                        value.evidence == ProbeEvidence.REGISTERED || diagnostic) {
                        item {
                            val count = if (diagnostic) R.string.system_probe_diagnostic_count
                                else if (value.evidence == ProbeEvidence.ENABLED)
                                    R.string.system_probe_count_enabled
                                else R.string.system_probe_count_candidate
                            AdaptiveText(stringResource(count, displayed.size, groups.size),
                                style = MaterialTheme.typography.titleSmall)
                        }
                        if (request.focusPackage != null) {
                            item {
                                val matches = displayed.any(request::matches)
                                AdaptiveText(stringResource(
                                    if (matches) R.string.system_probe_target_found
                                    else R.string.system_probe_target_missing,
                                    request.focusPackage
                                ), style = MaterialTheme.typography.bodySmall,
                                    color = if (matches) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (displayed.isEmpty()) {
                            item {
                                AdaptiveText(stringResource(R.string.system_probe_empty),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(groups, key = { it.first }) { (pkg, entries) ->
                            val expanded = expandedPackage == pkg
                            val match = entries.any(request::matches)
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    expandedPackage = if (expanded) null else pkg
                                }.padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AdaptiveText(
                                        entries.first().appLabel,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (match) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (match) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    AdaptiveText(stringResource(R.string.system_probe_group_count,
                                        entries.size), style = MaterialTheme.typography.labelSmall)
                                }
                                AdaptiveText(pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (expanded) {
                                    entries.forEach { entry ->
                                        AdaptiveText(entry.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (request.matches(entry)) FontWeight.SemiBold
                                            else FontWeight.Normal)
                                        AdaptiveText(entry.component,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.system_probe_close))
            }
        },
        dismissButton = {
            Row {
                AdaptiveTextButton(onClick = { revision++ }) {
                    AdaptiveText(stringResource(R.string.system_probe_refresh))
                }
                AdaptiveTextButton(onClick = {
                    request.entryKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId, scenario)
                    } ?: request.componentKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId)
                    }
                }) {
                    AdaptiveText(stringResource(R.string.system_probe_open_system))
                }
            }
        },
    )
}
