package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Process
import android.widget.TextView
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
    // A dedicated temporary host id is used only while testing the OS widget
    // picker. Never leave the chosen widget bound or modify the launcher.
    val widgetHost = remember(context) {
        AppWidgetHost(context.applicationContext, 0x4C4301).also { host ->
            // Isolated testing-only host ID. Reclaim abandoned allocations from
            // a previous process crash or a killed picker before using it again.
            runCatching { host.deleteHost() }
        }
    }
    var pendingWidgetId by remember(request) { mutableIntStateOf(-1) }
    val widgetPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingWidgetId >= 0) {
            runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
            pendingWidgetId = -1
        }
        revision++
    }
    DisposableEffect(widgetHost) {
        onDispose {
            if (pendingWidgetId >= 0) {
                runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
                pendingWidgetId = -1
            }
        }
    }
    // Only a return captured after this dialog launched a system preview can be
    // attributed to the current verification attempt.
    var previewLaunchedAt by remember(request) { mutableStateOf<Long?>(null) }
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: request.entryKind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    var scenarioMenu by remember { mutableStateOf(false) }
    // All API results are visible by default; diagnostics remain a separate source
    // selector rather than hiding otherwise useful, possibly Hook-filtered results.
    var showDiagnostics by remember(request) { mutableStateOf(false) }
    var showDetails by remember(request) { mutableStateOf(false) }

    val choices = request.entryKind?.let(::availableProbeScenarios).orEmpty()
    val nativeRoute = request.entryKind?.let(::systemPreviewRoute)
        ?: request.componentKind?.let(::systemPreviewRoute)
        ?: SystemPreviewRoute.UNAVAILABLE
    val nativeAvailable = nativeRoute in setOf(
        SystemPreviewRoute.CHOOSER, SystemPreviewRoute.DIRECT,
        SystemPreviewRoute.PICKER, SystemPreviewRoute.SETTINGS,
        SystemPreviewRoute.WIDGET_PICKER, SystemPreviewRoute.INPUT_METHOD_PICKER
    )
    val scenarioTestAvailable = request.entryKind?.let(::supportsScenarioTest) == true
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
    val selectedResult = SystemPreviewSelection.select(
        request = activeRequest,
        value = value,
        showDiagnostics = showDiagnostics,
        nowMillis = System.currentTimeMillis(),
        launchedAtMillis = previewLaunchedAt,
        currentCallerUid = android.os.Process.myUid(),
        expectedAction = runCatching {
            if (value?.runtimeReturn?.source == "PACKAGE_MANAGER_FILTERED_RETURN")
                request.entryKind?.let { kind ->
                    SystemEffectPreview.queryIntent(context, kind, scenario).let { intent ->
                        (intent.selector ?: intent).action
                    }
                }
            else null
        }.getOrNull(),
    )
    val diagnostic = showDiagnostics && value != null
    val runtimeAvailable = selectedResult.source in setOf(
        PreviewListSource.HOOK_OBSERVATION, PreviewListSource.HOST_OBSERVATION
    )
    val runtimeSnapshot = if (selectedResult.source == PreviewListSource.HOST_OBSERVATION)
        value?.hostReturn else value?.runtimeReturn
    val runtimePackages = runtimeSnapshot?.packages.orEmpty()
    val runtimeComponents = runtimeSnapshot?.components.orEmpty()
    val documentPicker = request.entryKind == IntentKind.OPEN_DOCUMENT ||
        request.entryKind == IntentKind.CREATE_DOCUMENT
    val documentSnapshot = if (selectedResult.source == PreviewListSource.DOCUMENT_ROOTS)
        value?.matchingRootsSnapshot else null
    val displayed = selectedResult.items
    val sourceRes = when (selectedResult.source) {
        PreviewListSource.HOOK_OBSERVATION -> R.string.preview_source_hook_observed
        PreviewListSource.HOST_OBSERVATION -> R.string.preview_source_host_observed
        PreviewListSource.DOCUMENT_ROOTS -> R.string.preview_source_root_return
        PreviewListSource.API_RETURN -> R.string.preview_source_api_candidates
        PreviewListSource.DIAGNOSTIC -> R.string.preview_source_diagnostic
        PreviewListSource.UNAVAILABLE -> R.string.preview_source_unavailable
    }
    val distinctApps = displayed.map(SystemProbeItem::packageName).distinct().size
    val qualityRes = when (selectedResult.source) {
        PreviewListSource.HOOK_OBSERVATION, PreviewListSource.HOST_OBSERVATION ->
            R.string.system_probe_quality_candidate
        PreviewListSource.DOCUMENT_ROOTS -> R.string.system_probe_quality_picker
        PreviewListSource.API_RETURN, PreviewListSource.DIAGNOSTIC ->
            if (value?.evidence == ProbeEvidence.ENABLED)
                R.string.system_probe_quality_enabled
            else R.string.system_probe_quality_candidate
        PreviewListSource.UNAVAILABLE -> R.string.system_probe_quality_unavailable
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
                item(key = "preview-capability") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AdaptiveText(
                            stringResource(request.entryKind?.let(::previewRouteNote)
                                ?: request.componentKind?.let(::previewRouteNote)
                                ?: R.string.preview_no_public_system_entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (request.userId != com.yagay.ListCleaner.domain.AndroidUid.userId(
                                Process.myUid()
                            )) {
                            AdaptiveText(
                                stringResource(R.string.preview_other_user_unavailable),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (request.entryKind == IntentKind.PROCESS_TEXT) {
                    item(key = "native-text-selection") {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = ctx.getString(R.string.system_preview_example_text)
                                    setTextIsSelectable(true)
                                    setPadding(12, 12, 12, 12)
                                }
                            },
                        )
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
                            AdaptiveText(stringResource(sourceRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (observedThisTest && matching != null && !documentPicker) {
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
                        if (matching?.requestKind == "UNKNOWN") {
                            item(key = "picker-action-unresolved") {
                                AdaptiveText(
                                    stringResource(R.string.preview_document_action_unknown,
                                        matching.actionCode?.toString() ?: "?",
                                        matching.stateClassName.ifBlank { "?" }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                    runtimeSnapshot?.let { returned ->
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
                    if (selectedResult.source == PreviewListSource.UNAVAILABLE) {
                        item(key = "no-confirmed-preview") {
                            AdaptiveText(
                                stringResource(when {
                                    documentPicker -> R.string.system_probe_no_matched_document_roots
                                    request.entryKind in setOf(
                                        IntentKind.ACCESSIBILITY, IntentKind.PRINT,
                                        IntentKind.CREDENTIAL_PROVIDER
                                    ) -> R.string.system_probe_no_filtered_manager_return
                                    else -> R.string.preview_no_verified_list
                                }),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selectedResult.source != PreviewListSource.UNAVAILABLE &&
                        (documentPicker || runtimeAvailable || diagnostic ||
                            value.items.isNotEmpty() || value.diagnosticCandidates.isNotEmpty())) {
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
                        if (documentPicker && !diagnostic) {
                            item(key = "document-root-qualification") {
                                AdaptiveText(
                                    stringResource(if (documentSnapshot == null)
                                        R.string.system_probe_no_matched_document_roots
                                    else R.string.system_probe_document_roots_qualified),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!documentPicker && runtimeAvailable && runtimePackages.isEmpty() && runtimeComponents.isEmpty()) {
                            item(key = "post-filter-empty") {
                                AdaptiveText(
                                    "Observed Hook post-filter result is empty (the originating request is not independently verified).",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else if (!documentPicker && !runtimeAvailable && !diagnostic) {
                            item(key = "post-filter-stale") {
                                AdaptiveText(
                                    "API return shown; Hook filtering cannot be independently verified for this request. The API result may already be filtered.",
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
            if (nativeAvailable || scenarioTestAvailable) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    if (request.entryKind == IntentKind.INPUT_METHOD) {
                        AdaptiveTextButton(onClick = {
                            SystemEffectPreview.launchSettings(
                                context, IntentKind.INPUT_METHOD, request.userId
                            )
                        }) {
                            AdaptiveText(stringResource(R.string.preview_open_keyboard_settings))
                        }
                    }
                    if (scenarioTestAvailable) {
                        AdaptiveTextButton(onClick = {
                            request.entryKind?.let { kind ->
                                previewLaunchedAt = System.currentTimeMillis()
                                SystemEffectPreview.launchScenario(
                                    context, kind, request.userId, scenario
                                )
                            }
                        }) {
                            AdaptiveText(stringResource(R.string.preview_test_intent))
                        }
                    }
                    if (nativeAvailable) {
                        AdaptiveTextButton(onClick = {
                            if (nativeRoute == SystemPreviewRoute.WIDGET_PICKER) {
                                if (request.userId != com.yagay.ListCleaner.domain.AndroidUid.userId(
                                        Process.myUid()
                                    )) {
                                    android.widget.Toast.makeText(
                                        context, R.string.system_preview_other_space,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else if (pendingWidgetId < 0) {
                                    runCatching {
                                        val id = widgetHost.allocateAppWidgetId()
                                        pendingWidgetId = id
                                        widgetPicker.launch(
                                            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(
                                                AppWidgetManager.EXTRA_APPWIDGET_ID, id
                                            )
                                        )
                                    }.onFailure {
                                        if (pendingWidgetId >= 0) {
                                            runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
                                            pendingWidgetId = -1
                                        }
                                        android.widget.Toast.makeText(
                                            context, R.string.preview_widget_picker_unavailable,
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                previewLaunchedAt = System.currentTimeMillis()
                                request.entryKind?.let {
                                    SystemEffectPreview.launch(context, it, request.userId, scenario)
                                } ?: request.componentKind?.let {
                                    SystemEffectPreview.launch(context, it, request.userId)
                                }
                            }
                        }) {
                            AdaptiveText(stringResource(when (nativeRoute) {
                                SystemPreviewRoute.PICKER, SystemPreviewRoute.WIDGET_PICKER,
                                SystemPreviewRoute.INPUT_METHOD_PICKER ->
                                    R.string.system_probe_open_picker
                                SystemPreviewRoute.SETTINGS -> R.string.preview_open_related_settings
                                else -> R.string.system_probe_open_system
                            }))
                        }
                    }
                }
            } else {
                AdaptiveTextButton(onClick = onDismiss) {
                    AdaptiveText(stringResource(R.string.system_probe_close))
                }
            }
        },
        dismissButton = {
            if (nativeAvailable || scenarioTestAvailable) {
                AdaptiveTextButton(onClick = onDismiss) {
                    AdaptiveText(stringResource(R.string.system_probe_close))
                }
            }
        },
    )
}
