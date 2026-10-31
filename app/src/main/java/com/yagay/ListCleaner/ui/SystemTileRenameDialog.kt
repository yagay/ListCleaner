package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.RootComponent

/** Edits the actual SystemUI TileService label override, never a local list alias. */
@Composable
internal fun SystemTileRenameDialog(
    item: RootComponent,
    currentName: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(currentName.orEmpty()) }
    LaunchedEffect(item.id, currentName) { text = currentName.orEmpty() }
    val normalized = text.trim()
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.qs_tile_rename_title)) },
        text = {
            Column {
                AdaptiveText(
                    stringResource(R.string.qs_tile_rename_original, item.label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AdaptiveText(
                    stringResource(R.string.qs_tile_rename_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdaptiveText(
                    item.component.flattenToShortString(),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(12.dp))
                AdaptiveOutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 64 && it.none(Char::isISOControl)) text = it
                    },
                    label = { AdaptiveText(stringResource(R.string.qs_tile_rename_field)) },
                    supportingText = {
                        AdaptiveText(stringResource(R.string.qs_tile_rename_count, text.length))
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                onClick = { onSave(normalized.ifEmpty { null }) },
                enabled = normalized != currentName.orEmpty(),
            ) { AdaptiveText(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (!currentName.isNullOrEmpty()) {
                    AdaptiveTextButton(onClick = { onSave(null) }) {
                        AdaptiveText(stringResource(R.string.qs_tile_rename_restore))
                    }
                }
                AdaptiveTextButton(onClick = onDismiss) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
