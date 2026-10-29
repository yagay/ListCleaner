package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.runtime.NotificationCleanupController
import com.yagay.ListCleaner.domain.SurfaceKind

private fun SurfaceKind.label(): Int = when (this) {
    SurfaceKind.NOTIFICATION_SHADE -> R.string.surface_aosp_notifications
    SurfaceKind.LAUNCHER_DRAWER -> R.string.surface_aosp_drawer
    SurfaceKind.LAUNCHER_PREDICTIONS -> R.string.surface_aosp_predictions
    SurfaceKind.RECENTS -> R.string.surface_aosp_recents
    SurfaceKind.QS_EDITOR -> R.string.surface_aosp_qs
    SurfaceKind.DOCUMENT_RECENTS -> R.string.surface_aosp_documents
    SurfaceKind.SETTINGS_APPS -> R.string.surface_aosp_settings
    SurfaceKind.MEDIA_OUTPUT -> R.string.surface_aosp_media
}

@Composable
internal fun AospSurfaceSettings(vm: MainViewModel, userId: Int) {
    val context = LocalContext.current
    val saved by vm.surfaceFilters.collectAsStateWithLifecycle()
    var selectedKind by remember(userId) { mutableStateOf(SurfaceKind.LAUNCHER_DRAWER) }
    var query by remember(userId, selectedKind) { mutableStateOf("") }
    var draft by remember(userId, saved) { mutableStateOf(saved) }
    var confirming by remember { mutableStateOf(false) }
    var refreshRevision by remember(userId) { mutableStateOf(0) }
    val activeNotifications by NotificationCleanupController.rows.collectAsStateWithLifecycle()

    val catalog = remember(userId, selectedKind, refreshRevision, activeNotifications) {
        SurfaceCandidateDiscovery.query(context, selectedKind, userId, activeNotifications)
    }
    val chosen = draft.selected(selectedKind)
    // Selected but temporarily unavailable targets remain editable. Never discard
    // existing rules merely because the source is restricted or currently empty.
    val selectable = (catalog.items + chosen.map { it to it }).distinctBy { it.first }
        .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
    val matches = selectable.filter { (pkg, label) ->
        query.isBlank() || pkg.contains(query.trim(), ignoreCase = true) ||
            label.contains(query.trim(), ignoreCase = true)
    }
    SettingsArea(stringResource(R.string.surface_aosp_title)) {
        SettingsHint(stringResource(R.string.surface_aosp_help))
        SettingsHint(catalog.description)
        SettingsActionButton(onClick = { refreshRevision++ }) {
            AdaptiveText(stringResource(R.string.surface_scan_refresh))
        }
        SettingsChoices(
            title = stringResource(R.string.surface_aosp_choose),
            value = selectedKind,
            options = SurfaceKind.entries.map { it to context.getString(it.label()) },
            onSelected = { selectedKind = it }
        )
        SettingsHint(stringResource(R.string.surface_aosp_selected, chosen.size))
        BasicTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { inner ->
                Column {
                    SettingsHint(stringResource(R.string.surface_aosp_search))
                    inner()
                }
            }
        )
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            matches.take(120).forEach { (pkg, label) ->
                SettingsSwitchRow(
                    title = label,
                    supportingText = pkg,
                    checked = pkg in chosen,
                    onCheckedChange = { enabled ->
                        val update = if (enabled) chosen + pkg else chosen - pkg
                        draft = draft.withSelected(selectedKind, update)
                    }
                )
            }
        }
        if (matches.size > 120) SettingsHint(
            stringResource(R.string.surface_aosp_more, matches.size - 120)
        )
        SettingsActionButton(
            onClick = { confirming = true },
            enabled = draft != saved,
            style = SettingsActionStyle.PRIMARY
        ) {
            AdaptiveText(stringResource(R.string.surface_aosp_apply))
        }
    }
    if (confirming) {
        SettingsFlatDialog(
            onDismissRequest = { confirming = false },
            title = { AdaptiveText(stringResource(R.string.surface_aosp_confirm)) },
            text = { AdaptiveText(stringResource(R.string.surface_aosp_confirm_help)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirming = false
                    vm.setSurfaceFilters(draft)
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirming = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
