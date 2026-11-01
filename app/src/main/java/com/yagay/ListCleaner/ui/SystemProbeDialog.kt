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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid

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
    }
    DisposableEffect(widgetHost) {
        onDispose {
            if (widgetId >= 0) runCatching { widgetHost.deleteAppWidgetId(widgetId) }
        }
    }
    LaunchedEffect(request) {
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
            onDismiss()
        }
    }
}
