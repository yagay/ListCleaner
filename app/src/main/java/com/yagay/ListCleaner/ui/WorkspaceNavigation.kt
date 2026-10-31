package com.yagay.ListCleaner.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

/** Entry and Root use the exact same icon and mode-switch interaction.
 * Each screen still owns its separate ordering backend.
 */
@Composable
internal fun WorkspaceSortShortcut(kind: IntentKind?, ordering: Boolean = false, onOpen: () -> Unit) =
    WorkspaceSortModeAction(
        supported = ordering || (kind != null && kind in HomeLayoutPolicy.sortableKinds),
        ordering = ordering,
        onOpen = onOpen,
    )

@Composable
internal fun WorkspaceSortModeAction(
    supported: Boolean,
    ordering: Boolean = false,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    WorkspaceToolbarAction(
        onClick = {
            if (supported) onOpen()
            else Toast.makeText(context, R.string.priority_unsupported_current_list, Toast.LENGTH_SHORT).show()
        },
        active = ordering
    ) {
            val iconTint = when {
                !supported -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ordering -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            AdaptiveIcon(
                Icons.AutoMirrored.Rounded.Sort,
                stringResource(if (ordering) R.string.nav_rules else R.string.nav_priority),
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
    }
}
