package com.yagay.ListCleaner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Still identifies logical grouped rows, but shape and spacing are owned by
 * the Material 3 Card/ListItem implementations, never drawn per position.
 */
internal enum class ListRowPosition { SINGLE, FIRST, MIDDLE, LAST }

@Composable
internal fun PageSectionTitle(title: String) {
    AdaptiveText(
        title, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 2
    )
}

@Composable
internal fun WorkspaceSectionTitle(title: String) = PageSectionTitle(title)

@Composable
internal fun PageFilterSection(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        content = content
    )
}
