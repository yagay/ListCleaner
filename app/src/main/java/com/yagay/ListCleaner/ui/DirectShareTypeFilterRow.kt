package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import com.yagay.ListCleaner.domain.DirectShareType

internal fun DirectShareType.titleRes(): Int = when (this) {
    DirectShareType.TEXT -> R.string.direct_share_type_text
    DirectShareType.IMAGE -> R.string.direct_share_type_images
    DirectShareType.VIDEO -> R.string.direct_share_type_videos
    DirectShareType.AUDIO -> R.string.direct_share_type_audio
    DirectShareType.FILE -> R.string.direct_share_type_files
}

/** Uses the same compact secondary selector as Open with and Deep Links. */
@Composable
internal fun DirectShareTypeFilterMenu(
    selected: DirectShareType?,
    onSelected: (DirectShareType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = selected?.let { stringResource(it.titleRes()) }
        ?: stringResource(R.string.common_all)
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
                AdaptiveIcon(Icons.Rounded.Description,
                    stringResource(R.string.direct_share_type_filter) + ": " + title,
                    modifier = Modifier.size(20.dp))
            }
        }
        FlatWorkspaceDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
            menuWidth = 264.dp,
        ) {
            WorkspaceFlatMenuRow(
                title = stringResource(R.string.common_all), selected = selected == null,
                onClick = { expanded = false; onSelected(null) }
            )
            DirectShareType.entries.forEach { type ->
                WorkspaceFlatMenuRow(
                    title = stringResource(type.titleRes()),
                    selected = selected == type,
                    onClick = { expanded = false; onSelected(type) }
                )
            }
        }
    }
}
