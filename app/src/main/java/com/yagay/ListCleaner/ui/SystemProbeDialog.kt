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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source-first system probe. Keep the actionable answer visible without an entire page of
 * legal/technical disclaimers; diagnostics and limitations are explicit secondary disclosures.
 */
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: request.entryKind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    var scenarioMenu by remember { mutableStateOf(false) }
    var showDiagnostics by remember(request) { mutableStateOf(false) }
    var showDetails by remember(request) { mutableStateOf(false) }
    var expandedPackage by remember(request) { mutableStateOf<String?>(null) }

    val choices = request.entryKind?.let(::availableProbeScenarios).orEmpty()
    val nativeAvailable = request.entryKind?.let {
        systemPreviewRoute(it) != SystemPreviewRoute.UNAVAILABLE
    } ?: request.componentKind?.let {
        systemPreviewRoute(it) != SystemPreviewRoute.UNAVAILABLE
    } ?: false
    val nativeRootsRelevant = request.entryKind in setOf(
        IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
        IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT,
    )
    val activeRequest = request.copy(scenario = scenario)
    var response by remember(request) { mutableStateOf<SystemProbeResult?>(null) }

    // After the user exits DocumentsUI and returns, synchronize the real Hook observations
    // before querying. It does not infer any visibility from PackageManager registrations.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activeRequest, revision) {
        response = null
        response = withContext(Dispatchers.IO) {
            if (nativeRootsRelevant) {
                (context.applicationContext as? ListCleanerApp)?.synchronizeObservedEntries()
            }
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
    val qualityRes = when (value?.evidence) {
        ProbeEvidence.ENABLED -> R.string.system_probe_quality_enabled
        ProbeEvidence.REGISTERED -> R.string.system_probe_quality_candidate
        ProbeEvidence.PICKER_HOST -> R.string.system_probe_quality_picker
        ProbeEvidence.NOT_PUBLIC, null -> R.string.system_probe_quality_unavailable
    }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AdaptiveText(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (choices.size > 1) {
                    item(key = "test-scenario") {
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
                                            expandedPackage = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (value == null) {
                    item(key = "loading") {
                        AdaptiveText(stringResource(R.string.system_probe_loading))
                    }
                } else {
                    item(key = "quality") {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AdaptiveText(stringResource(qualityRes),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                            if (value.evidence == ProbeEvidence.PICKER_HOST ||
                                (nativeRootsRelevant && value.evidence == ProbeEvidence.NOT_PUBLIC)
                            ) {
                                AdaptiveText(stringResource(R.string.system_probe_picker_compact),
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                AdaptiveText(stringResource(value.sourceRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    value.pickerHost?.let { host ->
                        item(key = "picker-host") {
                            AdaptiveText(stringResource(R.string.system_probe_picker_host_short,
                                host.appLabel),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (nativeRootsRelevant) {
                        item(key = "observed-label") {
                            val found = value.observedThirdPartyRoots.size
                            AdaptiveText(
                                stringResource(R.string.system_probe_observed_title, found),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (found == 0) {
                                AdaptiveText(stringResource(R.string.system_probe_observed_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(value.observedThirdPartyRoots, key = { it.packageName + "/" + it.key }) { root ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AdaptiveText(root.label,
                                    style = MaterialTheme.typography.bodyMedium)
                                AdaptiveText(root.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AdaptiveText(stringResource(R.string.system_probe_observed_time,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(root.capturedAtMillis))),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    value.pickerHost?.let { _ -> }
                    if (value.diagnosticCandidates.isNotEmpty()) {
                        item(key = "diagnostics-action") {
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
                        item(key = "candidate-count") {
                            val countRes = if (diagnostic) R.string.system_probe_diagnostic_count
                                else if (value.evidence == ProbeEvidence.ENABLED)
                                    R.string.system_probe_count_enabled
                                else R.string.system_probe_count_candidate
                            AdaptiveText(stringResource(countRes, displayed.size, groups.size),
                                style = MaterialTheme.typography.titleSmall)
                        }
                        if (request.focusPackage != null) {
                            item(key = "target") {
                                val matches = displayed.any(request::matches)
                                AdaptiveText(stringResource(
                                    if (matches) R.string.system_probe_target_found
                                    else R.string.system_probe_target_missing, request.focusPackage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (matches) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (displayed.isEmpty()) {
                            item(key = "empty") {
                                AdaptiveText(stringResource(R.string.system_probe_empty))
                            }
                        }
                        items(groups, key = { it.first }) { (pkg, entries) ->
                            val expanded = expandedPackage == pkg
                            val selected = entries.any(request::matches)
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    expandedPackage = if (expanded) null else pkg
                                }.padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AdaptiveText(entries.first().appLabel,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface)
                                    AdaptiveText(stringResource(R.string.system_probe_group_count,
                                        entries.size), style = MaterialTheme.typography.labelSmall)
                                }
                                if (expanded) {
                                    entries.forEach { entry ->
                                        AdaptiveText(entry.label,
                                            style = MaterialTheme.typography.bodySmall)
                                        AdaptiveText(entry.component,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "details-toggle") {
                        AdaptiveTextButton(onClick = { showDetails = !showDetails }) {
                            AdaptiveText(stringResource(
                                if (showDetails) R.string.system_probe_details_hide
                                else R.string.system_probe_details_show))
                        }
                    }
                    if (showDetails) {
                        item(key = "details") {
                            AdaptiveText(stringResource(value.explanationRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AdaptiveText(stringResource(R.string.system_probe_scope_contract),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "refresh") {
                        AdaptiveTextButton(onClick = { revision++ }) {
                            AdaptiveText(stringResource(R.string.system_probe_refresh))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (nativeAvailable) {
                AdaptiveTextButton(onClick = {
                    request.entryKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId, scenario)
                    } ?: request.componentKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId)
                    }
                }) {
                    AdaptiveText(stringResource(R.string.system_probe_open_system))
                }
            } else {
                AdaptiveTextButton(onClick = onDismiss) {
                    AdaptiveText(stringResource(R.string.system_probe_close))
                }
            }
        },
        dismissButton = {
            if (nativeAvailable) {
                AdaptiveTextButton(onClick = onDismiss) {
                    AdaptiveText(stringResource(R.string.system_probe_close))
                }
            }
        },
    )
}
