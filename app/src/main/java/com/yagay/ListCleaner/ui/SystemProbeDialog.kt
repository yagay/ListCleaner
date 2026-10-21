package com.yagay.ListCleaner.ui

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
    // Only a return captured after this dialog launched a system preview can be
    // attributed to the current verification attempt.
    var previewLaunchedAt by remember(request) { mutableStateOf<Long?>(null) }
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: request.entryKind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    var scenarioMenu by remember { mutableStateOf(false) }
    // Document open/save registration results should be immediately inspectable even
    // when the OEM DocumentsUI does not expose a matching-roots callback.
    // They remain clearly labelled as API candidates, never final visible entries.
    var showDiagnostics by remember(request) { mutableStateOf(false) }
    var showDetails by remember(request) { mutableStateOf(false) }

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
    val diagnostic = showDiagnostics && value != null
    // Prefer the actual post-filter framework return when the Hook captured one.
    // It is a package-level runtime snapshot, not proof of rendered UI components.
    val runtimeSnapshot = value?.runtimeReturn
    val runtimePackages = runtimeSnapshot?.packages.orEmpty()
    // A persisted return may belong to an older system request. Keep stale
    // observations in diagnostics rather than presenting them as current cleanup.
    val runtimeAge = runtimeSnapshot?.let { System.currentTimeMillis() - it.capturedAtMillis }
    val runtimeRecent = runtimeAge != null && runtimeAge in 0..120_000L
    val runtimeForAttempt = previewLaunchedAt?.let { launched ->
        runtimeSnapshot != null && runtimeSnapshot.capturedAtMillis >= launched
    } == true
    val runtimeAvailable = runtimeRecent && runtimeForAttempt && !diagnostic
    val runtimeComponents = runtimeSnapshot?.components.orEmpty()
    val displayed = when {
        value == null -> emptyList()
        diagnostic -> if (value.diagnosticCandidates.isNotEmpty())
            value.diagnosticCandidates else value.items
        runtimeAvailable && runtimeComponents.isNotEmpty() -> runtimeComponents.map { id ->
            val pkg = id.substringBefore('/')
            val componentClass = id.substringAfter('/')
            val label = value.items.firstOrNull { it.component == id }?.label
                ?: componentClass.substringAfterLast('.')
            SystemProbeItem(
                label = label,
                packageName = pkg,
                className = componentClass,
                appLabel = value.items.firstOrNull { it.packageName == pkg }?.appLabel ?: pkg,
            )
        }
        runtimeAvailable -> runtimePackages.map { pkg ->
            SystemProbeItem(
                label = value.items.firstOrNull { it.packageName == pkg }?.appLabel ?: pkg,
                packageName = pkg,
                className = "@runtime/" + (request.entryKind?.name ?: "UNKNOWN"),
                appLabel = value.items.firstOrNull { it.packageName == pkg }?.appLabel ?: pkg,
            )
        }
        // Without a matched runtime capture, there is no verified post-filter
        // result. Raw manager or registration lists are diagnostics only.
        else -> emptyList()
    }
    val distinctApps = displayed.map(SystemProbeItem::packageName).distinct().size
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
                        val observedThisTest = request.entryKind !=
                            IntentKind.DOCUMENT_PROVIDER && value.pickerProbeStatus ==
                            PickerProbeStatus.OBSERVED_AFTER_TEST
                        val matching = value.matchingRootsSnapshot
                        item(key = "picker-verification") {
                            AdaptiveText(
                                stringResource(when (value.pickerProbeStatus) {
                                    PickerProbeStatus.OBSERVED_AFTER_TEST ->
                                        R.string.system_probe_test_capture
                                    PickerProbeStatus.AWAITING_HOOK ->
                                        R.string.system_probe_test_waiting
                                    PickerProbeStatus.HISTORY_ONLY ->
                                        R.string.system_probe_test_history
                                    else -> R.string.system_probe_test_no_hook
                                }),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (observedThisTest && matching != null) {
                                AdaptiveText(
                                    stringResource(R.string.system_probe_test_root_count,
                                        matching.roots.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                AdaptiveText(
                                    stringResource(R.string.system_probe_test_captured_at,
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT, DateFormat.SHORT
                                        ).format(Date(matching.capturedAt))),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (observedThisTest && matching != null) {
                            items(matching.roots, key = { "current/" + it.syntheticId }) { root ->
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    AdaptiveText(root.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    AdaptiveText(
                                        stringResource(
                                            if (root.systemOwned)
                                                R.string.system_probe_system_root
                                            else R.string.system_probe_third_party_root,
                                            root.packageName
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // Historical data is only a fallback if this test did not yield a
                        // verified matching-roots snapshot. Never merge it into the live list.
                        if (!observedThisTest) {
                            if (matching != null) {
                                item(key = "previous-matching") {
                                    AdaptiveText(
                                        stringResource(R.string.system_probe_historical_snapshot,
                                            matching.roots.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(matching.roots, key = { "history/" + it.syntheticId }) { root ->
                                    AdaptiveText(root.label,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (value.observedThirdPartyRoots.isNotEmpty()) {
                                item(key = "historical-root-label") {
                                    AdaptiveText(stringResource(
                                        R.string.system_probe_observed_title,
                                        value.observedThirdPartyRoots.size
                                    ), style = MaterialTheme.typography.bodySmall)
                                }
                                items(value.observedThirdPartyRoots,
                                    key = { "observed/" + it.packageName + "/" + it.key }) { root ->
                                    AdaptiveText(root.label,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    value.runtimeReturn?.let { returned ->
                        // Capture details stay visible, but don't duplicate the same package
                        // list below the primary results or imply a rendered UI snapshot.
                        run {
                            item(key = "runtime-return-source") {
                                AdaptiveText(
                                    stringResource(
                                        R.string.system_probe_runtime_historical,
                                        returned.packages.size
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                AdaptiveText(stringResource(R.string.system_probe_runtime_scope,
                                    returned.source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                        }
                    }
                    if (value.diagnosticCandidates.isNotEmpty() || value.items.isNotEmpty()) {
                        item(key = "diagnostics-action") {
                            AdaptiveTextButton(onClick = {
                                showDiagnostics = !showDiagnostics
                            }) {
                                AdaptiveText(stringResource(
                                    if (showDiagnostics) R.string.system_probe_diagnostic_hide
                                    else R.string.system_probe_diagnostic_show))
                            }
                        }
                    }
                    if (runtimeAvailable || diagnostic || value.items.isNotEmpty() ||
                        value.diagnosticCandidates.isNotEmpty()) {
                        item(key = "candidate-count") {
                            val countRes = if (runtimeAvailable && !diagnostic)
                                R.string.system_probe_count_enabled
                                else R.string.system_probe_count_candidate
                            AdaptiveText(stringResource(countRes, displayed.size, distinctApps),
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
                        if (runtimeAvailable && runtimePackages.isEmpty()) {
                            item(key = "post-filter-empty") {
                                AdaptiveText(
                                    "Hook returned an empty post-filter package list.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else if (!runtimeAvailable && !diagnostic) {
                            item(key = "post-filter-stale") {
                                AdaptiveText(
                                    "No Hook result captured after launching this preview. Use diagnostics to inspect API candidates.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (displayed.isEmpty() && diagnostic) {
                            item(key = "empty") {
                                AdaptiveText(stringResource(R.string.system_probe_empty))
                            }
                        }
                        // Show every queried entry immediately. Package-level folding hid the
                        // requested list behind counts and extra taps.
                        items(displayed, key = { it.component }) { entry ->
                            val selected = request.matches(entry)
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                AdaptiveText(
                                    entry.appLabel,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                        else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (entry.label != entry.appLabel) {
                                    AdaptiveText(entry.label,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                AdaptiveText(entry.component,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    previewLaunchedAt = System.currentTimeMillis()
                    request.entryKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId, scenario)
                    } ?: request.componentKind?.let {
                        SystemEffectPreview.launch(context, it, request.userId)
                    }
                }) {
                    AdaptiveText(stringResource(
                        if (request.entryKind?.let { systemPreviewRoute(it) } == SystemPreviewRoute.PICKER)
                            R.string.system_probe_open_picker else R.string.system_probe_open_system
                    ))
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
