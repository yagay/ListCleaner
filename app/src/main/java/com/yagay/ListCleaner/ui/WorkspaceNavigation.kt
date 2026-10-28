package com.yagay.ListCleaner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

/** An Entry-only shortcut; leaves the main branch's existing priority page intact. */
@Composable
internal fun WorkspaceSortShortcut(kind: IntentKind?, onOpen: () -> Unit) {
    val context = LocalContext.current
    val supported = kind != null && kind in HomeLayoutPolicy.sortableKinds
    Surface(
        onClick = {
            if (supported) onOpen()
            else Toast.makeText(context, R.string.priority_unsupported_current_list, Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.size(48.dp), shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            AdaptiveIcon(
                Icons.Rounded.FormatListNumbered,
                stringResource(R.string.nav_priority),
                modifier = Modifier.size(22.dp),
                tint = if (supported) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
