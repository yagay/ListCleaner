package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgress
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.HorizontalDivider as MaterialDivider
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgress
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.TriStateCheckbox as MaterialTriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as NativeButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as NativeCircularProgress
import top.yukonga.miuix.kmp.basic.HorizontalDivider as NativeDivider
import top.yukonga.miuix.kmp.basic.IconButton as NativeIconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as NativeLinearProgress
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AdaptiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialTextButton(onClick = onClick, modifier = modifier, enabled = enabled,
            contentPadding = contentPadding, content = content)
    else MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled,
        insideMargin = contentPadding, colors = NativeButtonDefaults.buttonColors(), content = content)
}

@Composable
internal fun AdaptiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    else MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = NativeButtonDefaults.buttonColors(), content = content)
}

@Composable
internal fun AdaptiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    else NativeIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
internal fun AdaptiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        MaterialFilterChip(
            selected = selected, onClick = onClick, label = label,
            modifier = modifier, enabled = enabled, leadingIcon = leadingIcon
        )
    } else {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minHeight = 38.dp,
            colors = if (selected) NativeButtonDefaults.buttonColorsPrimary()
                     else NativeButtonDefaults.buttonColors(),
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            leadingIcon?.invoke()
            label()
        }
    }
}

@Composable
internal fun AdaptiveTriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialTriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)
    else {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
            MiuixCheckbox(
                checked = state == ToggleableState.On,
                onCheckedChange = { onClick() },
                enabled = enabled
            )
            if (state == ToggleableState.Indeterminate) {
                androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                    drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .5f),
                        end = androidx.compose.ui.geometry.Offset(size.width * .75f, size.height * .5f),
                        strokeWidth = 3.dp.toPx())
                }
            }
        }
    }
}

@Composable
internal fun AdaptiveHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = Color.Unspecified
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialDivider(modifier = modifier, thickness = thickness,
            color = if (color == Color.Unspecified) androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant else color)
    else NativeDivider(modifier = modifier, thickness = thickness,
        color = if (color == Color.Unspecified) MiuixTheme.colorScheme.dividerLine else color)
}

@Composable
internal fun AdaptiveLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        if (progress == null) MaterialLinearProgress(modifier = modifier)
        else MaterialLinearProgress(progress = progress, modifier = modifier)
    } else NativeLinearProgress(progress = progress?.invoke(), modifier = modifier)
}

@Composable
internal fun AdaptiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
        MaterialCircularProgress(modifier = modifier, strokeWidth = strokeWidth)
    else NativeCircularProgress(modifier = modifier, strokeWidth = strokeWidth)
}

/** Shared callbacks, with separate native UI components selected at the theme boundary. */
@Composable
internal fun AdaptiveCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialCard(modifier = modifier, content = content)
        UiThemeStyle.MIUIX_STYLE -> MiuixCard(modifier = modifier, content = content)
    }
}

@Composable
internal fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        UiThemeStyle.MIUIX_STYLE -> MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
internal fun AdaptiveSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialSwitch(checked, onCheckedChange, modifier, enabled = enabled)
        UiThemeStyle.MIUIX_STYLE -> MiuixSwitch(checked, onCheckedChange, modifier, enabled = enabled)
    }
}

@Composable
internal fun AdaptiveCheckbox(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialCheckbox(checked, onCheckedChange, modifier, enabled = enabled)
        UiThemeStyle.MIUIX_STYLE -> MiuixCheckbox(checked, onCheckedChange, modifier, enabled = enabled)
    }
}

/**
 * Native Miuix container for candidate, priority and component application rows.
 * Material mode keeps the historical structure and hit targets unchanged.
 */
@Composable
internal fun AdaptiveGroupSurface(
    modifier: Modifier = Modifier,
    position: MiuixRowPosition = MiuixRowPosition.SINGLE,
    content: @Composable () -> Unit
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.MATERIAL -> MaterialCard(
            modifier = modifier.padding(horizontal = 12.dp).padding(position.verticalPadding()),
            shape = position.shape()
        ) { content() }
        UiThemeStyle.MIUIX_STYLE -> MiuixSurface(
            modifier = modifier.padding(horizontal = 14.dp).padding(position.verticalPadding()),
            shape = position.shape(),
            color = MiuixTheme.colorScheme.surfaceContainer
        ) { content() }
    }
}
