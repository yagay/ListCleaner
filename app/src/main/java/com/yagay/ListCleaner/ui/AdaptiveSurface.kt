package com.yagay.ListCleaner.ui

import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface as NativeSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Native MIUIX surfaces for sticky filters and drag overlays, with independent elevation. */
@Composable
internal fun AdaptiveSurface(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        MaterialSurface(
            modifier = modifier,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content
        )
    } else {
        NativeSurface(
            modifier = modifier,
            color = if (tonalElevation > 0.dp) MiuixTheme.colorScheme.surfaceContainer
                    else MiuixTheme.colorScheme.surface,
            shadowElevation = shadowElevation,
            content = content
        )
    }
}
