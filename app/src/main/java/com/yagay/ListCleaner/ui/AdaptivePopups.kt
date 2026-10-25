package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yagay.ListCleaner.R

/** One flat modal treatment for app-owned dialogs; native system dialogs are unaffected. */
@Composable
internal fun AdaptiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        properties = properties
    )
}

/**
 * Common anchored Popup engine. Unlike DropdownMenu, it does not inject an
 * 8dp content gutter above and below the first/last item. Only the choices
 * scroll; header and footer remain outside the scroll viewport. The popup
 * is focusable and dismisses on Back or outside clicks.
 */
@Composable
internal fun FlatPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val offsetX = with(density) { offset.x.roundToPx() }
    val offsetY = with(density) { offset.y.roundToPx() }
    val edgeInset = with(density) { 8.dp.roundToPx() }
    val popupMaxHeight = (LocalConfiguration.current.screenHeightDp - 24)
        .coerceAtLeast(96).dp
    val positionProvider = remember(offsetX, offsetY, edgeInset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val maxX = (windowSize.width - edgeInset - popupContentSize.width)
                    .coerceAtLeast(edgeInset)
                val x = (anchorBounds.left + offsetX).coerceIn(edgeInset, maxX)
                val below = anchorBounds.bottom + offsetY
                val above = anchorBounds.top - popupContentSize.height + offsetY
                val maxY = (windowSize.height - edgeInset - popupContentSize.height)
                    .coerceAtLeast(edgeInset)
                val y = when {
                    below + popupContentSize.height <= windowSize.height - edgeInset -> below
                    above >= edgeInset -> above
                    else -> below.coerceIn(edgeInset, maxY)
                }
                return IntOffset(x, y.coerceIn(edgeInset, maxY))
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 196.dp)
                .heightIn(max = popupMaxHeight),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(0.5.dp, MaterialFlatPalette.outline())
        ) {
            Column {
                header?.invoke()
                // The available height is measured AFTER header/footer, so no
                // footer height is ever included in the scrolling region.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    content = content
                )
                footer?.invoke()
            }
        }
    }
}

/** Shared title for category and filter popups. */
@Composable
internal fun FlatPopupHeader(title: String) {
    AdaptiveText(
        title,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialSelectionPalette.text(),
        maxLines = 2
    )
}

/**
 * Flat footer with a single fixed 48dp minimum target height. No nested menu
 * padding, no TextButton minimum-size modifiers and no implicit vertical inset.
 * The row grows naturally for accessibility font scaling.
 */
@Composable
internal fun FlatPopupFooter(
    content: @Composable RowScope.() -> Unit
) {
    AdaptiveHorizontalDivider(thickness = 0.5.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        content = content
    )
}

@Composable
internal fun AdaptiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    FlatPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        content = content
    )
}

@Composable
internal fun AdaptiveDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = text, onClick = onClick, modifier = modifier,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled
    )
}

/** One menu entry point for categories, filter, app actions and settings. */
@Composable
internal fun FlatWorkspaceDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    FlatPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        header = header,
        footer = footer,
        content = content
    )
}
