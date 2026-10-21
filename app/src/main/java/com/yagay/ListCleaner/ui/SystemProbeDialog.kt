package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** A read-only in-app window showing fresh Android framework API results. */
@Composable
internal fun SystemProbeDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    var result by remember(request) { mutableStateOf<SystemProbeResult?>(null) }

    LaunchedEffect(request, refreshToken) {
        result = null
        result = withContext(Dispatchers.IO) {
            SystemEntryProbe.query(context.applicationContext, request)
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
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (response == null) {
                    AdaptiveText(stringResource(R.string.system_probe_loading))
                } else {
                    AdaptiveText(
                        stringResource(response.sourceRes),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AdaptiveText(
                        stringResource(response.explanationRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (response.supported) {
                        AdaptiveText(
                            stringResource(R.string.system_probe_count, response.items.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (request.focusPackage != null) {
                            val hits = response.items.count(request::matches)
                            AdaptiveText(
                                stringResource(
                                    if (hits > 0) R.string.system_probe_target_found
                                    else R.string.system_probe_target_missing,
                                    request.focusPackage,
                                ),
                                color = if (hits > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (response.items.isEmpty()) {
                            AdaptiveText(
                                stringResource(R.string.system_probe_empty),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        response.items.forEachIndexed { index, item ->
                            val matches = request.matches(item)
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AdaptiveText(
                                        "${index + 1}. ${item.label}",
                                        fontWeight = if (matches) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (matches) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                AdaptiveText(
                                    item.component,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    AdaptiveText(
                        stringResource(R.string.system_probe_scope_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_close))
            }
        },
        dismissButton = {
            Row {
                AdaptiveTextButton(onClick = { refreshToken++ }) {
                    AdaptiveText(stringResource(R.string.common_refresh))
                }
                AdaptiveTextButton(onClick = {
                    request.entryKind?.let { SystemEffectPreview.launch(context, it, request.userId) }
                        ?: request.componentKind?.let { SystemEffectPreview.launch(context, it, request.userId) }
                }) {
                    AdaptiveText(stringResource(R.string.system_probe_open_system))
                }
            }
        }
    )
}
