package com.yagay.ListCleaner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * One typography contract for both classic cleanup mode tabs, Entry/Component
 * kind filters, and the shared Apps/View/Actions/Help toolbar.
 * Selection is expressed through the existing Material3 palette, never font weight.
 */
@Composable
internal fun WorkspaceControlLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    AdaptiveText(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = maxLines,
        overflow = overflow
    )
}
