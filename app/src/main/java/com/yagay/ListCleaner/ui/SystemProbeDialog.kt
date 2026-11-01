package com.yagay.ListCleaner.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preview v2: one list, one provenance, one request. An OS return, a PM candidate
 * inventory, and an uncommitted rule simulation can never be silently blended.
 * This is deliberately independent of the previous multi-mode preview implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
    previewState: MainState? = null,
    previewScopedSelection: Set<ComponentRule>? = null,
    hasPendingDraft: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val kind = request.entryKind
    val route = kind?.let(::systemPreviewRoute)
        ?: request.componentKind?.let(::systemPreviewRoute)
        ?: SystemPreviewRoute.UNAVAILABLE
    val fixedSubtype = request.openPreset != null || request.directShareType != null ||
        request.customMimeType != null
    val scenarios = kind?.let(::availableProbeScenarios).orEmpty()
        .takeUnless { fixedSubtype }.orEmpty()
    var scenario by remember(request) {
        mutableStateOf(request.scenario ?: kind?.let(::defaultProbeScenario)
            ?: SystemProbeScenario.TEXT)
    }
    val actualRequest = request.copy(scenario = scenario)
    var response by remember(request) { mutableStateOf<SystemProbeResult?>(null) }
    var queryStartedAt by remember(request) { mutableStateOf<Long?>(null) }
    var systemLaunchedAt by remember(request) { mutableStateOf<Long?>(null) }
    var revision by remember(request) { mutableIntStateOf(0) }
    var history by remember(request) { mutableStateOf(false) }
    var inspected by remember(request) { mutableStateOf<SystemProbeItem?>(null) }

    // This is a query-start marker, not a claim that the resulting system screen
    // rendered anything. Capture is matched separately inside SystemPreviewV2.
    LaunchedEffect(actualRequest, revision) {
        response = null
        queryStartedAt = System.currentTimeMillis()
        response = withContext(Dispatchers.IO) {
            SystemEntryProbe.query(context.applicationContext, actualRequest)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val identity = remember(actualRequest) {
        runCatching { kind?.let {
            SystemEffectPreview.queryIntent(context, it, scenario, actualRequest)
        } }.getOrNull()?.let { intent ->
            val effective = intent.selector ?: intent
            Triple(effective.action, effective.type,
                com.yagay.ListCleaner.domain.normalizeBrowserHost(effective.data?.host))
        }
    }
    val result = SystemPreviewV2.resolve(
        request = actualRequest, probe = response,
        atMillis = System.currentTimeMillis(),
        queryStartedAt = queryStartedAt,
        systemLaunchedAt = systemLaunchedAt,
        managerUid = Process.myUid(),
        expectedAction = identity?.first,
        expectedMime = identity?.second,
        expectedHost = identity?.third,
        pendingDraft = hasPendingDraft && !history,
        selected = (previewState?.selected.orEmpty() + previewScopedSelection.orEmpty()),
        globalMode = previewState?.displayMode ?: com.yagay.ListCleaner.domain.DisplayMode.HIDE_SELECTED,
        overrides = previewState?.modeOverrides.orEmpty(),
        historyRequested = history,
    )

    val widgetHost = remember(context) {
        AppWidgetHost(context.applicationContext, 0x4C4301).also {
            runCatching { it.deleteHost() }
        }
    }
    var widgetId by remember(request) { mutableIntStateOf(-1) }
    val widgetPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (widgetId >= 0) {
            runCatching { widgetHost.deleteAppWidgetId(widgetId) }
            widgetId = -1
        }
        revision++
    }
    DisposableEffect(widgetHost) {
        onDispose {
            if (widgetId >= 0) runCatching { widgetHost.deleteAppWidgetId(widgetId) }
        }
    }

    inspected?.let { item ->
        AdaptiveAlertDialog(
            onDismissRequest = { inspected = null },
            title = { Text(item.appLabel) },
            text = {
                Text(item.label + "\n" + item.packageName + "\n" + item.className)
            },
            confirmButton = {
                AdaptiveTextButton(onClick = { inspected = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        val heading = kind?.let { stringResource(it.titleRes()) }
            ?: request.componentKind?.let { stringResource(it.titleRes()) }.orEmpty()
        Column(Modifier.fillMaxWidth()) {
            Text(heading, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            if (scenarios.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    scenarios.forEach { option ->
                        AdaptiveFilterChip(
                            selected = scenario == option,
                            onClick = { scenario = option; systemLaunchedAt = null },
                            label = { Text(stringResource(option.titleRes)) },
                        )
                    }
                }
            }
            val sourceText = when (result.source) {
                PreviewV2Source.ADAPTER_OBSERVED -> R.string.preview_v2_adapter
                PreviewV2Source.FILTERED_OBSERVED -> R.string.preview_v2_filtered
                PreviewV2Source.DOCUMENT_ROOTS -> R.string.preview_v2_documents
                PreviewV2Source.ENABLED -> R.string.preview_v2_enabled
                PreviewV2Source.INTENT_CANDIDATES -> R.string.preview_v2_candidates
                PreviewV2Source.ROLE_ELIGIBILITY -> R.string.preview_v2_role_eligibility
                PreviewV2Source.ROLE_CANDIDATES -> R.string.preview_v2_role_candidates
                PreviewV2Source.ROLE_HOLDER -> R.string.preview_v2_role_holder
                PreviewV2Source.DRAFT_SIMULATION -> R.string.preview_v2_draft
                PreviewV2Source.HISTORY -> R.string.preview_v2_history
                PreviewV2Source.UNAVAILABLE -> R.string.preview_v2_unavailable
            }
            Text(
                stringResource(sourceText),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AdaptiveTextButton(onClick = { revision++ }) {
                    Text(stringResource(R.string.system_probe_refresh))
                }
                if (!history && response?.historicalItems?.isNotEmpty() == true) {
                    AdaptiveTextButton(onClick = { history = true }) {
                        Text(stringResource(R.string.preview_tab_history))
                    }
                } else if (history) {
                    AdaptiveTextButton(onClick = { history = false }) {
                        Text(stringResource(R.string.preview_tab_current))
                    }
                }
                if (route !in setOf(
                        SystemPreviewRoute.UNAVAILABLE, SystemPreviewRoute.CONTEXT_MENU,
                    )
                ) {
                    AdaptiveTextButton(onClick = {
                        if (route == SystemPreviewRoute.WIDGET_PICKER) {
                            if (request.userId != AndroidUid.userId(Process.myUid())) {
                                Toast.makeText(context, R.string.system_preview_other_space,
                                    Toast.LENGTH_LONG).show()
                            } else if (widgetId < 0) {
                                runCatching {
                                    val allocated = widgetHost.allocateAppWidgetId()
                                    widgetId = allocated
                                    widgetPicker.launch(
                                        Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(
                                            AppWidgetManager.EXTRA_APPWIDGET_ID, allocated,
                                        )
                                    )
                                }.onFailure {
                                    if (widgetId >= 0) {
                                        runCatching { widgetHost.deleteAppWidgetId(widgetId) }
                                        widgetId = -1
                                    }
                                    Toast.makeText(context, R.string.preview_widget_picker_unavailable,
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            systemLaunchedAt = System.currentTimeMillis()
                            kind?.let {
                                SystemEffectPreview.launch(context, it, request.userId,
                                    scenario, actualRequest)
                            } ?: request.componentKind?.let {
                                SystemEffectPreview.launch(context, it, request.userId)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.system_probe_open_system))
                    }
                }
                if (kind != null && supportsScenarioTest(kind)) {
                    AdaptiveTextButton(onClick = {
                        systemLaunchedAt = System.currentTimeMillis()
                        SystemEffectPreview.launchScenario(context, kind, request.userId, scenario)
                    }) {
                        Text(stringResource(R.string.preview_test_intent))
                    }
                }
            }

            if (response == null) {
                Text(stringResource(R.string.preview_v2_loading),
                    Modifier.padding(16.dp))
            } else if (result.items.isEmpty()) {
                Text(stringResource(R.string.preview_v2_empty),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 78.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(result.items, key = { index, item ->
                        "${index}_${item.component}"
                    }) { _, item ->
                        SystemPreviewGridTile(
                            item = item,
                            focused = request.matches(item),
                            onInspect = { inspected = item },
                        )
                    }
                }
            }
            Text(stringResource(R.string.preview_v2_disclaimer),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
