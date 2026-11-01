package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/** MIME values are derived from actual successful resolver probes, separately per kind. */
@Composable
internal fun ShareMimeFilterMenu(
    selected: String?,
    supported: List<String>,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = if (selected != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AdaptiveIcon(
                    Icons.Rounded.Description,
                    stringResource(R.string.share_mime_filter_title) + ": " +
                        (selected ?: stringResource(R.string.common_all)),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 440.dp),
            menuWidth = 292.dp,
            scrollState = scroll,
        ) {
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { expanded = false; onSelected(null) },
            )
            FlatPopupHeader(stringResource(R.string.share_mime_filter_title))
            supported.forEach { mime ->
                WorkspaceFlatMenuRow(
                    title = mime,
                    selected = selected == mime,
                    onClick = { expanded = false; onSelected(mime) },
                )
            }
            AdaptiveText(
                stringResource(R.string.share_mime_scope_note),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
