package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compact, shared operational-mode status row.
 *
 * Mode is an execution policy, not a list filter. Each workspace supplies
 * its own actions and semantics; the row supplies only stable layout.
 */
@Composable
internal fun WorkspaceModeStatusRow(
    title: String,
    status: String,
    note: String? = null,
    actions: @Composable RowScope.() -> Unit
) {
    FlatRowSurface(dividerInset = MaterialUiTokens.pageHorizontal) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = MaterialUiTokens.detailHeight)
                .padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(Modifier.weight(1f)) {
                AdaptiveText(title, style = MaterialTheme.typography.labelMedium)
                AdaptiveText(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!note.isNullOrBlank()) {
                    AdaptiveText(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
