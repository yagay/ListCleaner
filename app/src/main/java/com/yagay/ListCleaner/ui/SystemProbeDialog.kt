package com.yagay.ListCleaner.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.DOCUMENTS_UI_PREVIEW_KINDS

/**
 * Preview actions now launch Android's real surfaces without any local candidate
 * grid, capture sheet or reconstructed system list.
 */
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
    previewState: MainState? = null,
    previewScopedSelection: Set<com.yagay.ListCleaner.domain.ComponentRule>? = null,
    hasPendingDraft: Boolean = false,
    rootSnapshot: RootPreviewSnapshot? = null,
) {
    val context = LocalContext.current
    val widgetHost = remember(context) { AppWidgetHost(context.applicationContext, 0x4C4301) }
    var widgetId by remember(request) { mutableIntStateOf(-1) }
    val widgetPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (widgetId >= 0) {
            runCatching { widgetHost.deleteAppWidgetId(widgetId) }
            widgetId = -1
        }
        onDismiss()
    }
    DisposableEffect(widgetHost) {
        onDispose {
            if (widgetId >= 0) runCatching { widgetHost.deleteAppWidgetId(widgetId) }
        }
    }
    // When Share has a selected MIME filter, preview that exact scenario
    // instead of asking for a different preset in a second popup.
    val documentChoices = if (request.entryKind == IntentKind.DOCUMENT_PROVIDER && !request.directLaunch)
        DOCUMENTS_UI_PREVIEW_KINDS else emptyList()
    val typedKind = request.entryKind?.takeIf {
        it in setOf(com.yagay.ListCleaner.domain.IntentKind.OPEN,
            com.yagay.ListCleaner.domain.IntentKind.SHARE,
            com.yagay.ListCleaner.domain.IntentKind.SHARE_MULTIPLE)
    }
    val typedOptions = if (!request.directLaunch && typedKind != null && previewState != null)
        previewMimeOptions(typedKind, previewState.candidates, previewState.openTypesExplicit)
    else emptyList()
    val choices = if (request.directLaunch || request.shareMimeType != null || typedOptions.isNotEmpty())
        emptyList() else request.entryKind?.let(::availableProbeScenarios).orEmpty()
    if (documentChoices.isNotEmpty()) {
        SettingsFlatDialog(
            onDismissRequest = onDismiss,
            title = { AdaptiveText(stringResource(R.string.intent_document_provider)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    documentChoices.forEach { previewKind ->
                        SettingsFlatChoiceRow(
                            label = stringResource(previewKind.titleRes()),
                            selected = false,
                            onClick = {
                                SystemEffectPreview.launch(context, previewKind, request.userId)
                                onDismiss()
                            },
                        )
                    }
                }
            },
            confirmButton = { AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            } },
        )
    } else if (typedOptions.isNotEmpty()) {
        SettingsFlatDialog(
            onDismissRequest = onDismiss,
            title = { AdaptiveText(stringResource(typedKind!!.titleRes())) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    typedOptions.forEach { option ->
                        SettingsFlatChoiceRow(
                            label = previewMimeLabel(option, previewState!!.openTypesExplicit),
                            selected = false,
                            onClick = {
                                SystemEffectPreview.launch(
                                    context, typedKind!!, request.userId, option.scenario,
                                    previewMimeRequest(
                                        typedKind, request.userId, option,
                                        request.focusPackage, request.focusClass
                                    ),
                                )
                                onDismiss()
                            },
                        )
                    }
                }
            },
            confirmButton = { AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            } },
        )
    } else if (choices.size > 1) {
        SettingsFlatDialog(
            onDismissRequest = onDismiss,
            title = { AdaptiveText(stringResource(request.entryKind!!.titleRes())) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        SettingsFlatChoiceRow(
                            label = stringResource(choice.titleRes),
                            selected = choice == request.scenario,
                            onClick = {
                                val kind = requireNotNull(request.entryKind)
                                SystemEffectPreview.launch(context, kind, request.userId,
                                    choice, request.copy(
                                        scenario = choice,
                                        openPreset = request.openPreset.takeIf { choice == request.scenario },
                                        directShareType = request.directShareType.takeIf { choice == request.scenario },
                                        customMimeType = request.customMimeType.takeIf { choice == request.scenario },
                                    ))
                                onDismiss()
                            },
                        )
                    }
                }
            },
            confirmButton = { AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            } },
        )
    }
    LaunchedEffect(request) {
        if (documentChoices.isNotEmpty() || choices.size > 1 || typedOptions.isNotEmpty()) return@LaunchedEffect
        try {
            val kind = request.entryKind
            val component = request.componentKind
            when {
                kind != null -> SystemEffectPreview.launch(
                    context, kind, request.userId,
                    request.scenario ?: defaultProbeScenario(kind), request,
                )
                component == CleanupKind.WIDGET -> {
                    if (request.userId != AndroidUid.userId(Process.myUid())) {
                        Toast.makeText(context, R.string.system_preview_other_space, Toast.LENGTH_LONG).show()
                    } else {
                        runCatching {
                            val id = widgetHost.allocateAppWidgetId()
                            widgetId = id
                            widgetPicker.launch(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            )
                        }.onFailure {
                            if (widgetId >= 0) {
                                runCatching { widgetHost.deleteAppWidgetId(widgetId) }
                                widgetId = -1
                            }
                            Toast.makeText(context, R.string.preview_widget_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                component != null -> SystemEffectPreview.launch(context, component, request.userId)
            }
        } finally {
            if (request.componentKind != CleanupKind.WIDGET || widgetId < 0) onDismiss()
        }
    }
}
