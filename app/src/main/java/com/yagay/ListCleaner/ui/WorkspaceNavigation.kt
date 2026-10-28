package com.yagay.ListCleaner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
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

/** Aligned, single-vector up/down sort glyph (not Material SwapVert's offset arrows). */
private val AlignedSortArrows: ImageVector = ImageVector.Builder(
    name = "AlignedSortArrows", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 3f)
        lineTo(2.5f, 7.5f)
        lineTo(4f, 9f)
        lineTo(6f, 7f)
        lineTo(6f, 20f)
        lineTo(8f, 20f)
        lineTo(8f, 7f)
        lineTo(10f, 9f)
        lineTo(11.5f, 7.5f)
        close()
        moveTo(16f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 17f)
        lineTo(20f, 15f)
        lineTo(21.5f, 16.5f)
        lineTo(17f, 21f)
        lineTo(12.5f, 16.5f)
        lineTo(14f, 15f)
        lineTo(16f, 17f)
        close()
    }
}.build()

/** An Entry-only shortcut; leaves the main branch's existing priority page intact. */
@Composable
internal fun WorkspaceSortShortcut(kind: IntentKind?, ordering: Boolean = false, onOpen: () -> Unit) {
    val context = LocalContext.current
    val supported = ordering || (kind != null && kind in HomeLayoutPolicy.sortableKinds)
    Surface(
        onClick = {
            if (supported) onOpen()
            else Toast.makeText(context, R.string.priority_unsupported_current_list, Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.size(48.dp), shape = RectangleShape,
        color = if (ordering) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            val iconTint = when {
                !supported -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ordering -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            AdaptiveIcon(
                AlignedSortArrows,
                stringResource(if (ordering) R.string.nav_rules else R.string.nav_priority),
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
        }
    }
}
