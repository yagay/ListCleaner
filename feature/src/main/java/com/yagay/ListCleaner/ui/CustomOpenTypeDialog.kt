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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_open_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
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
                                Text(
                                    definition?.title ?: stringResource(slot.titleRes()),
                                    fontWeight = if (definition != null) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                Text(
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
                                TextButton(onClick = { editing = slot }) {
                                    Text(stringResource(if (definition == null) R.string.common_add else R.string.common_edit))
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.custom_open_add_title, presetTitle)
                else stringResource(R.string.custom_open_edit_title, initial.title)
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it; errorRes = null },
                    label = { Text(stringResource(R.string.custom_open_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    mimeText,
                    { mimeText = it; errorRes = null },
                    label = { Text(stringResource(R.string.custom_open_mime_label)) },
                    supportingText = { Text(stringResource(R.string.custom_open_mime_example)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                OutlinedTextField(
                    extensionText,
                    { extensionText = it; errorRes = null },
                    label = { Text(stringResource(R.string.custom_open_extension_label)) },
                    supportingText = { Text(stringResource(R.string.custom_open_extension_example)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
                errorRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { delete ->
                    TextButton(onClick = delete) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = {
                        runCatching {
                            CustomOpenDefinition(title, parseSet(mimeText), parseSet(extensionText)).validated()
                        }.onSuccess(onSave).onFailure { errorRes = customOpenErrorRes(it.message) }
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
