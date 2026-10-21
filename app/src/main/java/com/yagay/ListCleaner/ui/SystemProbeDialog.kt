package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 * Shows what this caller can actually query, without promoting resolver registrations to
 * a platform/OEM picker result. Native-only surfaces show an explanation and launch button.
 */
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: request.entryKind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    var scenarioMenu by remember { mutableStateOf(false) }
    val choices = request.entryKind?.let(::availableProbeScenarios).orEmpty()
    val evidence = request.entryKind?.let(::previewEvidence)
        ?: request.componentKind?.let(::previewEvidence)
        ?: ProbeEvidence.NATIVE_UI_ONLY
    val nativeAvailable = request.entryKind?.let {
        systemPreviewRoute(it) != SystemPreviewRoute.UNAVAILABLE
    } ?: request.componentKind?.let {
        systemPreviewRoute(it) != SystemPreviewRoute.UNAVAILABLE
    } ?: false
    val activeRequest = request.copy(scenario = scenario)
    var result by remember(request) { mutableStateOf<SystemProbeResult?>(null) }

    LaunchedEffect(activeRequest, refreshToken) {
        result = null
        result = withContext(Dispatchers.IO) {
            SystemEntryProbe.query(context.applicationContext, activeRequest)
        }
    }
    val kindTitle = request.entryKind?.let { stringResource(it.titleRes()) }
        ?: request.componentKind?.let { stringResource(it.titleRes()) }
        ?: ""
    val response = result

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.system_probe_title, kindTitle)) },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (choices.size > 1) {
                    item(key = "scenario") {
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
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "source") {
                    AdaptiveText(
                        stringResource(evidence.labelRes),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (response == null) {
                    item(key = "loading") {
                        AdaptiveText(stringResource(R.string.system_probe_loading))
                    }
                } else {
                    item(key = "source-note") {
                        AdaptiveText(
                            stringResource(response.explanationRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (response.supported) {
                        item(key = "count") {
                            val label = if (evidence == ProbeEvidence.RESOLVER_CANDIDATES ||
                                evidence == ProbeEvidence.ROLE_CANDIDATES
                            ) R.string.system_probe_candidate_count
                            else R.string.system_probe_registry_count
                            AdaptiveText(
                                stringResource(label, response.totalCount),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (response.totalCount > response.items.size) {
                                AdaptiveText(
                                    stringResource(
                                        R.string.system_probe_results_capped,
                                        response.items.size, response.totalCount,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (request.focusPackage != null) {
                            item(key = "focus") {
                                val hits = response.items.count(request::matches)
                                AdaptiveText(
                                    stringResource(
                                        if (hits > 0) R.string.system_probe_target_found
                                        else R.string.system_probe_target_missing,
                                        request.focusPackage
                                    ),
                                    color = if (hits > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (response.items.isEmpty()) {
                            item(key = "empty") {
                                AdaptiveText(stringResource(R.string.system_probe_empty))
                            }
                        }
                        itemsIndexed(
                            response.items,
                            key = { _, entry -> entry.component },
                        ) { index, entry ->
                            val matches = request.matches(entry)
                            androidx.compose.foundation.layout.Column(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AdaptiveText(
                                        "${index + 1}. ${entry.label}",
                                        fontWeight = if (matches) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (matches) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                AdaptiveText(
                                    entry.component,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "disclaimer") {
                        AdaptiveText(
                            stringResource(R.string.system_probe_filtered_comparison),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                if (evidence != ProbeEvidence.NATIVE_UI_ONLY) {
                    AdaptiveTextButton(onClick = { refreshToken++ }) {
                        AdaptiveText(stringResource(R.string.system_probe_refresh))
                    }
                }
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
                }
            }
        },
    )
}
