package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig

@StringRes
private fun customOpenErrorRes(code: String?): Int = when (code) {
    "custom_open_invalid_title" -> R.string.custom_open_invalid_title
    "custom_open_invalid_mime" -> R.string.custom_open_invalid_mime
    "custom_open_invalid_extension" -> R.string.custom_open_invalid_extension
    "custom_open_missing_matcher" -> R.string.custom_open_missing_matcher
    else -> R.string.custom_open_generic_error
}

@Composable
internal fun CustomOpenTypeDialog(
    config: OpenTypeConfig,
    onSave: (OpenPreset, CustomOpenDefinition?) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<OpenPreset?>(null) }
    val preset = editing
    if (preset != null) {
        CustomOpenTypeEditor(
            preset = preset,
            initial = config.customDefinitions[preset],
            onSave = { definition -> onSave(preset, definition); editing = null },
            onDelete = if (preset in config.customDefinitions) ({ onSave(preset, null); editing = null }) else null,
            onDismiss = { editing = null }
        )
        return
    }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.custom_open_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                AdaptiveText(
                    stringResource(R.string.custom_open_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(OpenPreset.CUSTOM_SLOTS, key = { it.name }) { slot ->
                        val definition = config.customDefinitions[slot]
                        ListItem(
                            headlineContent = {
                                AdaptiveText(
                                    definition?.title ?: stringResource(slot.titleRes()),
                                    fontWeight = if (definition != null) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                AdaptiveText(
                                    if (definition == null) {
                                        stringResource(R.string.custom_open_not_configured)
                                    } else {
                                        buildString {
                                            if (definition.mimeTypes.isNotEmpty()) {
                                                append(stringResource(R.string.custom_open_mime_summary, definition.mimeTypes.joinToString()))
                                            }
                                            if (definition.mimeTypes.isNotEmpty() && definition.extensions.isNotEmpty()) append(" · ")
                                            if (definition.extensions.isNotEmpty()) {
                                                append(
                                                    stringResource(
                                                        R.string.custom_open_extension_summary,
                                                        definition.extensions.joinToString { ".$it" }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                            trailingContent = {
                                AdaptiveTextButton(onClick = { editing = slot }) {
                                    AdaptiveText(stringResource(if (definition == null) R.string.common_add else R.string.common_edit))
                                }
                            }
                        )
                        AdaptiveHorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { AdaptiveTextButton(onClick = onDismiss) { AdaptiveText(stringResource(R.string.common_done)) } }
    )
}

@Composable
private fun CustomOpenTypeEditor(
    preset: OpenPreset,
    initial: CustomOpenDefinition?,
    onSave: (CustomOpenDefinition) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var mimeText by remember(initial) { mutableStateOf(initial?.mimeTypes?.sorted()?.joinToString("\n").orEmpty()) }
    var extensionText by remember(initial) { mutableStateOf(initial?.extensions?.sorted()?.joinToString("\n").orEmpty()) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    fun parseSet(text: String): Set<String> = text
        .split(',', ';', '\n', '\r', '\t', ' ')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    val presetTitle = stringResource(preset.titleRes())
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AdaptiveText(
                if (initial == null) stringResource(R.string.custom_open_add_title, presetTitle)
                else stringResource(R.string.custom_open_edit_title, initial.title)
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveOutlinedTextField(
                    title,
                    { title = it; errorRes = null },
                    label = { AdaptiveText(stringResource(R.string.custom_open_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AdaptiveOutlinedTextField(
                    mimeText,
                    { mimeText = it; errorRes = null },
                    label = { AdaptiveText(stringResource(R.string.custom_open_mime_label)) },
                    supportingText = { AdaptiveText(stringResource(R.string.custom_open_mime_example)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                AdaptiveOutlinedTextField(
                    extensionText,
                    { extensionText = it; errorRes = null },
                    label = { AdaptiveText(stringResource(R.string.custom_open_extension_label)) },
                    supportingText = { AdaptiveText(stringResource(R.string.custom_open_extension_example)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                errorRes?.let {
                    AdaptiveText(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { delete ->
                    AdaptiveTextButton(onClick = delete) {
                        AdaptiveText(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                AdaptiveTextButton(
                    onClick = {
                        runCatching {
                            CustomOpenDefinition(title, parseSet(mimeText), parseSet(extensionText)).validated()
                        }.onSuccess(onSave).onFailure { errorRes = customOpenErrorRes(it.message) }
                    }
                ) {
                    AdaptiveText(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = { AdaptiveTextButton(onClick = onDismiss) { AdaptiveText(stringResource(R.string.common_cancel)) } }
    )
}
