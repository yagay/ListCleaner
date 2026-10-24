package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * List-first presentation contract.
 *
 * App, category, settings and picker rows can reuse the same surface
 * independently of their domain actions. A future Card presentation may
 * reuse those actions, but no card-specific structure is needed today.
 */
@Composable
internal fun FlatRowSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    horizontalInset: Dp = 0.dp,
    dividerInset: Dp = MaterialUiTokens.pageHorizontal,
    showDivider: Boolean = true,
    color: Color = MaterialFlatPalette.surface(),
    content: @Composable () -> Unit
) {
    val dividerColor = MaterialFlatPalette.outline()
    val rowModifier = modifier.fillMaxWidth().padding(horizontal = horizontalInset)
        .then(if (showDivider) Modifier.drawWithContent {
            drawContent()
            val stroke = 0.75.dp.toPx()
            val x = dividerInset.toPx().coerceAtMost(size.width / 2f)
            val y = size.height - stroke / 2f
            drawLine(dividerColor, Offset(x, y), Offset(size.width - x, y), stroke)
        } else Modifier)
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = rowModifier,
            shape = RectangleShape, color = color
        ) { content() }
    } else {
        Surface(
            modifier = rowModifier, shape = RectangleShape, color = color
        ) { content() }
    }
}

/** Single-column, cardless category row, shared by Entry, Root and Settings. */
@Composable
internal fun FlatCategoryRow(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FlatRowSurface(modifier = modifier, onClick = onClick) {
        ListItem(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                AdaptiveIcon(
                    icon, null, Modifier.padding(start = 0.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                AdaptiveText(
                    title, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = if (description.isNullOrBlank()) null else ({
                AdaptiveText(
                    description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }),
            trailingContent = {
                AdaptiveIcon(
                    Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

/** Same unobtrusive section typography on every flat page. */
@Composable
internal fun FlatSectionHeader(title: String, modifier: Modifier = Modifier) {
    AdaptiveText(
        title,
        modifier = modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2
    )
}
