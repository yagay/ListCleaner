package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Flat list treatment shared by Entry, Ordering and Root.
 *
 * No item card background, rounded container or outer gap: content fills the
 * available width and a quiet inset divider separates neighboring rows.
 * Selection is conveyed by the checkbox, not a full-row purple surface.
 */
@Composable
internal fun FlatWorkspaceRowSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal),
            thickness = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    }
}

/** Visual-only grouped list. App and component remain independent LazyColumn items. */
@Composable
internal fun GroupedApplicationSurface(expanded: Boolean, content: @Composable () -> Unit) {
    val shape = if (expanded) RoundedCornerShape(
        topStart = MaterialUiTokens.cardRadius, topEnd = MaterialUiTokens.cardRadius
    ) else RoundedCornerShape(MaterialUiTokens.cardRadius)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = MaterialUiTokens.groupHorizontal)
            .padding(top = MaterialUiTokens.groupedOuterGap),
        shape = shape,
        color = if (expanded) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            content()
            if (expanded) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun GroupedComponentSurface(position: ListRowPosition, selected: Boolean, content: @Composable () -> Unit) {
    val last = position == ListRowPosition.LAST
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(start = MaterialUiTokens.groupHorizontal,
                end = MaterialUiTokens.groupHorizontal),
        shape = if (last) RoundedCornerShape(
            bottomStart = MaterialUiTokens.cardRadius,
            bottomEnd = MaterialUiTokens.cardRadius
        ) else RoundedCornerShape(0.dp),
        // Component and expanded app header share one surface color; selection
        // remains visible through the trailing checked icon.
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column {
            content()
            // Visible inset separator between siblings; no divider after the last row.
            if (!last) HorizontalDivider(
                modifier = Modifier.padding(
                    start = MaterialUiTokens.groupedDetailStartPadding,
                    end = MaterialUiTokens.groupedDetailEndPadding
                ),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.24f)
            )
        }
    }
}
