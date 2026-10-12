package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.ListPopup as NativeListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn as NativeListPopupColumn
import top.yukonga.miuix.kmp.basic.Surface as NativeSurface
import top.yukonga.miuix.kmp.extra.SuperDialog as NativeSuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Dialog behavior remains shared; the MIUIX branch uses upstream SuperDialog. */
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
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties
        )
        UiThemeStyle.MIUIX_STYLE -> {
            // SuperDialog takes a mutable show flag; its parent owns actual dialog lifetime.
            val show = remember { mutableStateOf(true) }
            NativeSuperDialog(show = show, onDismissRequest = onDismissRequest) {
                Column(
                    modifier = modifier.fillMaxWidth().heightIn(max = 600.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    icon?.invoke()
                    title?.invoke()
                    text?.let { body ->
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            body()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Menu uses the upstream MIUIX ListPopup rather than a Material popup surface. */
@Composable
internal fun AdaptiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialDropdownMenu(
            expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier,
            offset = offset, scrollState = scrollState, content = content
        )
        UiThemeStyle.MIUIX_STYLE -> {
            val show = remember { mutableStateOf(expanded) }
            show.value = expanded
            NativeListPopup(
                show = show, onDismissRequest = onDismissRequest,
                popupModifier = modifier, enableWindowDim = false
            ) {
                NativeListPopupColumn {
                    Column { content() }
                }
            }
        }
    }
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
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialDropdownMenuItem(
            text = text, onClick = onClick, modifier = modifier,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled
        )
        UiThemeStyle.MIUIX_STYLE -> NativeSurface(
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = onClick,
            enabled = enabled,
            color = MiuixTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                leadingIcon?.invoke()
                Column(Modifier.weight(1f, fill = true)) { text() }
                trailingIcon?.invoke()
            }
        }
    }
}
