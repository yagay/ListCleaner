package com.yagay.ListCleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.DialogProperties

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

@Composable
internal fun AdaptiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest,
        modifier = modifier, offset = offset,
        scrollState = scrollState,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialFlatPalette.outline()),
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

/** Square, non-elevated dropdown for the flat Cleanup workspace. */
@Composable
internal fun FlatWorkspaceDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    AdaptiveDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        content = content
    )
}
